package org.icedamericanomall.trade.domain.repository;

import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.model.order.PageResult;
import org.icedamericanomall.trade.domain.model.order.ShipPackageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单仓储（领域端口）—— 实现方：{@code infrastructure.persistence.order.OrderRepositoryImpl}。
 *
 * <p>所有状态迁移一律使用「条件更新」（UPDATE ... WHERE id=? AND status=期望值），
 * 受影响行数为 0 表示并发下状态已变更，实现方必须拒绝本次操作。
 * 跨服务编排（库存回滚、积分发放、物流创建）由 {@code app.order.*Manager} 承接。
 *
 * <p><b>框架无关</b>（ADR-038 分层守卫）：分页返回领域 {@link PageResult}，
 * 不使用 MyBatis-Plus {@code IPage}。
 */
public interface OrderRepository {

    /** 保存订单 + 订单项（同一本地事务，id 回填）。 */
    Order saveWithItems(Order order, List<OrderItem> items);

    /** 按订单号取订单（不存在返回 null）。 */
    Order findByOrderNo(String orderNo);

    /** 按技术主键取订单（不存在返回 null）。 */
    Order findById(Long id);

    /**
     * 查询订单并校验归属当前用户。
     *
     * @throws org.icedamericanomall.common.exception.BizException        订单不存在（ORDER_NOT_FOUND）
     * @throws org.icedamericanomall.common.exception.ForbiddenException 非本人订单（FORBIDDEN）
     */
    Order findOwnedOrder(String orderNo, Long userId);

    /**
     * 查询订单并校验归属当前商家。
     *
     * @throws org.icedamericanomall.common.exception.BizException        订单不存在（ORDER_NOT_FOUND）
     * @throws org.icedamericanomall.common.exception.ForbiddenException 非本店订单（FORBIDDEN）
     */
    Order findSellerOrder(String orderNo, Long sellerId);

    /** 查询订单项列表（下单快照）。 */
    List<OrderItem> findItemsByOrderId(Long orderId);

    /** 批量查询订单项（消除按单循环查询的 N+1）。 */
    List<OrderItem> findItemsByOrderIds(List<Long> orderIds);

    /**
     * 通用订单分页 + 后端筛选（A2 收口：AD12/T10 的 keyword/start_date/end_date）。
     *
     * <p>返回<b>框架无关</b> {@link PageResult}（ADR-038 分层守卫：域端口不得以
     * MyBatis-Plus {@code IPage} 作返回类型；适配层需要的分页信封由应用层转换）。
     *
     * @param userId     买家过滤（null = 不限）
     * @param sellerId   商家过滤（null = 不限）
     * @param status     状态过滤（null = 不限）
     * @param keyword    关键词（null/空白 = 不限）：订单号精确 / 收货人姓名·手机号模糊 / 明细 SPU 名模糊
     * @param startTime  下单时间下界（含；null = 不限）
     * @param endTime    下单时间上界（含；null = 不限）
     */
    PageResult<Order> pageOrders(Long userId, Long sellerId, Integer status, String keyword,
                                 LocalDateTime startTime, LocalDateTime endTime, int page, int size);

    /** 用户取消待付款订单（条件更新：仅待付款→已取消；库存回滚由编排层处理）。 */
    void cancelByUser(String orderNo, Long userId);

    /** 用户确认收货（条件更新：仅待收货→已完成；积分发放由编排层处理）。 */
    void confirmReceiptByUser(String orderNo, Long userId);

    /**
     * 商家发货一个包裹（V5.6 拆单发货，F3）—— 本地事务内完成「明细打包裹号 + 订单状态迁移」。
     *
     * <p><b>事务边界</b>：本方法只做本地库操作，物流记录创建与领域事件由编排层在事务外完成。
     *
     * @param orderNo  订单号
     * @param sellerId 当前商家 ID（归属校验）
     * @param skuIds   本包裹发货明细；空/缺省 = 全部剩余未发货明细（兼容单包裹语义）
     * @return 本包裹发货结果（包裹号 / 覆盖明细 / 是否已全部发货）
     */
    ShipPackageResult shipPackage(String orderNo, Long sellerId, List<String> skuIds);

    /**
     * 支付结果回写（由 pay-service 内部接口调用）。
     * 仅允许「待付款 → 待发货」与「待付款 → 已取消」两种合法迁移，其余一律拒绝。
     *
     * @param targetStatus 目标状态码
     */
    void transitionFromPendingPayment(String orderNo, Integer targetStatus);

    /** 查询超时未支付订单（供超时任务使用）。 */
    List<Order> listTimeoutPending(LocalDateTime cutoff);

    /**
     * 关闭超时订单（条件更新：仅待付款→已取消，无归属校验，供超时任务使用）。
     *
     * @return true=本次真正完成状态迁移；false=订单已被并发处理（幂等无操作）
     */
    boolean closeTimeoutOrder(Long orderId);

    /**
     * 系统取消已支付订单（F13 拼团退款闭环，ADR-049：条件更新 仅待发货→已取消，无归属校验；
     * 库存恢复与 I55 退款由编排层 {@code GroupBuyParticipationService} 处理）。
     *
     * @param orderNo 订单号
     * @return true=本次真正完成状态迁移；false=订单非待发货（已取消/已发货等，幂等无操作）
     */
    boolean cancelPaidBySystem(String orderNo);

    // ==================== 统计读模型（仪表盘/结算，CQRS 直查） ====================

    /** 全部订单数。 */
    long countAll();

    /** 指定状态订单数。 */
    long countByStatus(Integer status);

    /** 某时刻之后创建的订单数。 */
    long countByCreateTimeAfter(LocalDateTime start);

    /** 时间窗内创建的订单数（左闭右开）。 */
    long countByCreateTimeBetween(LocalDateTime start, LocalDateTime end);

    /** 指定状态订单列表（营收/商品聚合用，调用方内存汇总）。 */
    List<Order> findByStatus(Integer status);

    /** 指定状态 + 某时刻之后创建的订单列表。 */
    List<Order> findByStatusAndCreateTimeAfter(Integer status, LocalDateTime start);

    /** 指定状态 + 时间窗内创建的订单列表（左闭右开）。 */
    List<Order> findByStatusAndCreateTimeBetween(Integer status, LocalDateTime start, LocalDateTime end);

    /** 商家某时刻之后创建的订单数。 */
    long countBySellerAndCreateTimeAfter(Long sellerId, LocalDateTime start);

    /** 商家指定状态订单数。 */
    long countBySellerAndStatus(Long sellerId, Integer status);

    /** 商家某时刻之后创建的订单列表。 */
    List<Order> findBySellerAndCreateTimeAfter(Long sellerId, LocalDateTime start);

    /** 商家指定状态 + 某时刻之后创建的订单列表。 */
    List<Order> findBySellerAndStatusAndCreateTimeAfter(Long sellerId, Integer status, LocalDateTime start);

    /** 商家指定状态 + 完成时间窗内的订单列表（左闭右开，结算用）。 */
    List<Order> findBySellerAndStatusAndEndTimeBetween(Long sellerId, Integer status,
                                                       LocalDateTime start, LocalDateTime end);
}

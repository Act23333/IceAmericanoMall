package org.icedamericanomall.trade.app.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.trade.adapter.assembler.OrderConverter;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.model.order.PageResult;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.icedamericanomall.trade.domain.service.OrderItemPriceComparator.CompareResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单查询用例族编排（应用层 · 读侧）—— 详情（含订单项）/ 分页 / 跨服务摘要。
 *
 * <p>归属校验（本人订单 / 本店订单）下沉在仓储实现，本层只做装配；
 * 出参一律为 VO 或 Feign CO，聚合不泄漏到适配层之外。
 *
 * <p><b>订单项 = 标识 + 快照（ADR-061 §决策 1）</b>：读取路径<b>只</b>用订单项自身的数据
 * 渲染（{@code spu_name}/{@code sku_spec}/{@code price}/…均为下单时点快照），
 * <b>绝不</b>回查 {@code spu}/{@code sku} 活表来「补齐/刷新」快照列
 * （活表改名/改价/下架后，历史订单必须仍显示下单时点的事实）。
 *
 * <p><b>比价/失效提示（ADR-061 §决策 3）</b>：为<b>读时增强、fail-open</b>——
 * 由 {@link OrderCurrentStateEnricher} 按业务键<b>批量</b>取当前状态并比对，
 * 异常 → 空 Map + WARN（{@link #compareStates} 再兜一层），明细<b>不缩行</b>。
 * 该输出<b>仅用于展示</b>，不得作为下单/退款/售后等业务判定的依据。
 * <p>⚠ 契约 §十二 12.2 明确「比价结果字段形态未定、前端不得对接」，故
 * {@code OrderItemVO} <b>不</b>新增这些键；{@link #compareStates} 为服务端能力出口，
 * 待契约定稿后接线到 VO。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueryManager {

    private final OrderRepository orderRepository;
    private final OrderConverter orderConverter;
    private final OrderCurrentStateEnricher orderCurrentStateEnricher;

    /** 订单详情（本人）：归属校验失败抛 ORDER_NOT_FOUND / FORBIDDEN。 */
    public OrderVO getOwnedOrderDetail(String orderNo, Long userId) {
        Order order = orderRepository.findOwnedOrder(orderNo, userId);
        return toDetailVO(order);
    }

    /** 订单详情（本店）：归属校验失败抛 ORDER_NOT_FOUND / FORBIDDEN。 */
    public OrderVO getSellerOrderDetail(String orderNo, Long sellerId) {
        Order order = orderRepository.findSellerOrder(orderNo, sellerId);
        return toDetailVO(order);
    }

    /** 通用订单分页（无筛选）：userId/sellerId 可同时传（互斥）；均为 null 时查所有。 */
    public IPage<OrderVO> pageOrders(Long userId, Long sellerId, Integer status, int page, int size) {
        return pageOrders(userId, sellerId, status, null, null, null, page, size);
    }

    /**
     * 通用订单分页 + 后端筛选（A2 收口：AD12/T10 的 {@code keyword}/{@code start_date}/{@code end_date}）。
     *
     * <p>筛选在 <b>仓储层 SQL</b> 完成（分页 total 与筛选口径一致）——此前契约声明而实现未接线，
     * 参数被静默忽略（前端表现为「筛选无效」）。控制器签名不变，筛选器以一等方法参数进入，
     * 便于后续继续扩展。
     *
     * @param keyword   关键词（null/空白 = 不限）
     * @param startTime 下单时间下界（含）
     * @param endTime   下单时间上界（含）
     */
    public IPage<OrderVO> pageOrders(Long userId, Long sellerId, Integer status, String keyword,
                                     LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        PageResult<Order> result = orderRepository.pageOrders(
                userId, sellerId, status, keyword, startTime, endTime, page, size);
        // 域层返回框架无关 PageResult（ADR-038 分层守卫）；适配层既有 HTTP 信封为 IPage，
        // 两者的转换收敛在应用层这一处
        Page<OrderVO> voPage = new Page<>(result.page(), result.size(), result.total());
        voPage.setRecords(result.records().stream().map(orderConverter::toVO).toList());
        return voPage;
    }

    /** 订单摘要（按订单号；不存在返回 null）—— 供内部 Feign 端点。 */
    public OrderSummaryCO getOrderSummary(String orderNo) {
        return toSummary(orderRepository.findByOrderNo(orderNo));
    }

    /** 订单摘要（按技术主键；不存在返回 null）—— 供内部 Feign 端点。 */
    public OrderSummaryCO getOrderSummaryById(Long orderId) {
        return toSummary(orderRepository.findById(orderId));
    }

    /**
     * 订单项的「快照 vs 实时」比对（读时增强，fail-open；ADR-061 §决策 3）。
     *
     * <p>显式接口而非只藏在详情装配里，目的是让「不缩行」成为<b>可直接断言</b>的契约：
     * 返回 Map 的条目数恒等于入参明细中「有 ID」的条数（数据源异常 → 每条为
     * {@code UNCOMPARABLE}，而不是缺项）。
     *
     * <p>本方法<b>不</b>修改任何订单项字段、不产生任何写操作；调用方（适配层）在
     * 契约定稿前不得据此新增线格式字段。
     *
     * @param items 订单项快照（可空）
     * @return 订单项 ID → 比对结果；端口异常时全部为 {@code UNCOMPARABLE}
     */
    public Map<Long, CompareResult> compareStates(List<OrderItem> items) {
        try {
            return orderCurrentStateEnricher.compare(items);
        } catch (Exception e) {
            // 第二道兜底：读时增强的任何失败都不得让订单读取失败（不缩行由调用侧按 id 取默认值保证）
            log.warn("订单项比价读模型调用失败，比价态降级为「无法比对」（fail-open）: cause={}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 订单聚合 → 含订单项的详情 VO（列表页不返回订单项，故仅详情使用）。
     *
     * <p>快照字段只来自 {@code order_item} 自身（不回查活表）；比价读模型同批跑一次，
     * 失败不影响本返回值（fail-open、不缩行）。
     */
    private OrderVO toDetailVO(Order order) {
        OrderVO vo = orderConverter.toVO(order);
        List<OrderItem> items = orderRepository.findItemsByOrderId(order.getId());
        // 读时增强：与装配同一批明细，异常仅告警（结果当前不出线格式，见类注释）
        compareStates(items);
        vo.setItems(orderConverter.toItemVOs(items));
        return vo;
    }

    /** 订单聚合 → 跨服务摘要（totalAmount=原价, payAmount=应付）。 */
    private OrderSummaryCO toSummary(Order order) {
        if (order == null) {
            return null;
        }
        OrderSummaryCO dto = new OrderSummaryCO();
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPayAmount(order.getPayAmount());
        dto.setStatus(order.getStatus());
        return dto;
    }
}

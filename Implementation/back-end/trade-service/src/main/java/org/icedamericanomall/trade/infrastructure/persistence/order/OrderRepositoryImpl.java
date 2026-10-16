package org.icedamericanomall.trade.infrastructure.persistence.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.model.order.PageResult;
import org.icedamericanomall.trade.domain.model.order.ShipPackageResult;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单仓储实现 —— PO 与聚合互转在本类完成，外部只见领域对象。
 *
 * <p>状态迁移全部走条件更新（WHERE 带期望状态），保证并发下不会发生非法跃迁；
 * 归属校验（本人订单 / 本店订单）在此下沉，编排层只见领域对象与受检异常。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    /**
     * 批量保存订单 + 订单项（同一本地事务）—— MyBatis-Plus 逐条 insert 由事务保证原子。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order saveWithItems(Order order, List<OrderItem> items) {
        OrderPO po = toPO(order);
        po.setCreateTime(LocalDateTime.now());
        orderMapper.insert(po);
        order.setId(po.getId());
        order.setCreateTime(po.getCreateTime());
        for (OrderItem item : items) {
            OrderItemPO itemPO = toPO(item);
            itemPO.setOrderId(order.getId());
            orderItemMapper.insert(itemPO);
            item.setId(itemPO.getId());
            item.setOrderId(order.getId());
        }
        return order;
    }

    @Override
    public Order findByOrderNo(String orderNo) {
        return toDomain(orderMapper.selectOne(
                new LambdaQueryWrapper<OrderPO>().eq(OrderPO::getOrderNo, IdGenerator.toBytesOrNull(orderNo))));
    }

    @Override
    public Order findById(Long id) {
        return toDomain(orderMapper.selectById(id));
    }

    @Override
    public Order findOwnedOrder(String orderNo, Long userId) {
        Order order = findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权查看该订单");
        }
        return order;
    }

    @Override
    public Order findSellerOrder(String orderNo, Long sellerId) {
        Order order = findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        if (!order.getSellerId().equals(sellerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权查看该订单");
        }
        return order;
    }

    @Override
    public List<OrderItem> findItemsByOrderId(Long orderId) {
        return orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItemPO>().eq(OrderItemPO::getOrderId, orderId))
                .stream().map(OrderRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<OrderItem> findItemsByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItemPO>().in(OrderItemPO::getOrderId, orderIds))
                .stream().map(OrderRepositoryImpl::toDomain).toList();
    }

    @Override
    public PageResult<Order> pageOrders(Long userId, Long sellerId, Integer status, String keyword,
                                        LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        var wrapper = new LambdaQueryWrapper<OrderPO>();
        if (userId != null) wrapper.eq(OrderPO::getUserId, userId);
        if (sellerId != null) wrapper.eq(OrderPO::getSellerId, sellerId);
        if (status != null) wrapper.eq(OrderPO::getStatus, status);
        applyKeywordFilter(wrapper, keyword);
        if (startTime != null) wrapper.ge(OrderPO::getCreateTime, startTime);
        if (endTime != null) wrapper.le(OrderPO::getCreateTime, endTime);
        wrapper.orderByDesc(OrderPO::getCreateTime);
        // 框架分页信封（IPage）在持久化边界内消化：域层只收框架无关的 PageResult（ADR-038 守卫）
        IPage<OrderPO> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getRecords().stream().map(OrderRepositoryImpl::toDomain).toList(),
                result.getTotal(), page, size);
    }

    /**
     * 关键词筛选（A2 收口，AD12/T12 口径）：<b>订单号精确</b> / <b>收货人姓名·手机号模糊</b> /
     * <b>明细 SPU 名模糊</b>，三选一命中即返回。
     *
     * <p><b>为何订单号用精确匹配</b>：订单号是业务键（{@code orders.order_no BINARY(16)}，
     * 32-hex），模糊匹配在 {@code BINARY} 列上语义不可靠且无法走唯一索引；其密文/十六进制形态
     * 也不适合「部分输入」。收货人与商品名是自然文本字段，模糊匹配符合运营检索习惯。
     *
     * <p><b>为何明细名走 EXISTS 子查询而非 JOIN</b>：JOIN 会让「一单多明细命中多行」放大结果集
     * （分页 total 失真）；EXISTS 保持「一单一行的分页语义」，且命中即短路。
     *
     * <p>实现为「外层 AND + 内层 OR」（{@code and(w -> w.or(...).or(...))}），
     * 避免 OR 把上述 userId/sellerId/status 过滤条件旁路掉（越权读数风险）。
     */
    private void applyKeywordFilter(LambdaQueryWrapper<OrderPO> wrapper, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String trimmed = keyword.trim();
        String likePattern = "%" + trimmed + "%";
        byte[] exactOrderNo = IdGenerator.toBytesOrNull(trimmed);
        wrapper.and(w -> w
                .eq(exactOrderNo != null, OrderPO::getOrderNo, exactOrderNo)
                .or().like(OrderPO::getReceiverName, likePattern)
                .or().like(OrderPO::getReceiverPhone, likePattern)
                .or().exists("SELECT 1 FROM order_item oi WHERE oi.order_id = orders.id "
                        + "AND oi.spu_name LIKE {0}", likePattern));
    }

    /**
     * <pre>
     * Scenario: 用户取消待付款订单
     *   Given 订单状态为"待付款"且当前用户是订单所属人
     *   When 用户请求取消订单
     *   Then 条件更新状态为"已取消"（WHERE status=待付款），库存/券回滚由编排层处理
     * Scenario: 非待付款状态拒绝取消 / 非订单所属人无权操作 → 抛出异常
     * Scenario: 并发下状态已被他人变更（受影响行数=0）→ 抛出 ORDER_STATUS_INVALID
     * </pre>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByUser(String orderNo, Long userId) {
        Order order = findOwnedOrder(orderNo, userId);
        int expected = OrderStatusEnum.PENDING_PAYMENT.getCode();
        if (order.getStatus() != expected) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "仅待付款订单可取消");
        }
        boolean updated = orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderPO>()
                        .eq(OrderPO::getId, order.getId())
                        .eq(OrderPO::getStatus, expected)
                        .set(OrderPO::getStatus, OrderStatusEnum.CANCELLED.getCode())
                        .set(OrderPO::getCloseTime, LocalDateTime.now())) > 0;
        if (!updated) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单状态已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceiptByUser(String orderNo, Long userId) {
        Order order = findOwnedOrder(orderNo, userId);
        int expected = OrderStatusEnum.PENDING_RECEIPT.getCode();
        if (order.getStatus() != expected) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "仅待收货订单可确认收货");
        }
        boolean updated = orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderPO>()
                        .eq(OrderPO::getId, order.getId())
                        .eq(OrderPO::getStatus, expected)
                        .set(OrderPO::getStatus, OrderStatusEnum.COMPLETED.getCode())
                        .set(OrderPO::getEndTime, LocalDateTime.now())) > 0;
        if (!updated) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单状态已变更，请刷新后重试");
        }
    }

    /**
     * 商家发货一个包裹（F3 拆单发货，V5.6）—— 本地事务内的完整发货动作：
     * 归属 + 状态校验 → 解析本包裹明细 → 打包裹号（条件更新）→ 判定是否全部发完 → 订单状态迁移。
     *
     * <p><b>状态语义</b>：部分发货订单保持待发货(2)，全部明细均有 {@code package_no} 后才迁移待收货(3)；
     * {@code consignTime} 在<b>首包</b>写入。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShipPackageResult shipPackage(String orderNo, Long sellerId, List<String> skuIds) {
        Order order = findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        if (!order.getSellerId().equals(sellerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
        int expected = OrderStatusEnum.PENDING_SHIPMENT.getCode();
        if (order.getStatus() != expected) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "仅待发货订单可发货");
        }

        List<OrderItem> items = findItemsByOrderId(order.getId());
        List<OrderItem> targets = resolvePackageItems(items, skuIds);
        int packageNo = nextPackageNo(items);

        List<Long> itemIds = targets.stream().map(OrderItem::getId).toList();
        int rows = orderItemMapper.markPackageShipped(order.getId(), itemIds, packageNo);
        if (rows != itemIds.size()) {
            // 条件更新未全命中：并发下同一明细已被另一请求发货 → 拒绝（一明细只发一次）
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "发货明细已被处理，请刷新后重试");
        }

        boolean allShipped = orderItemMapper.countUnshipped(order.getId()) == 0;
        LocalDateTime now = LocalDateTime.now();
        if (allShipped) {
            boolean updated = orderMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderPO>()
                            .eq(OrderPO::getId, order.getId())
                            .eq(OrderPO::getStatus, expected)
                            .set(OrderPO::getStatus, OrderStatusEnum.PENDING_RECEIPT.getCode())
                            .set(order.getConsignTime() == null, OrderPO::getConsignTime, now)) > 0;
            if (!updated) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单状态已变更，请刷新后重试");
            }
        } else if (order.getConsignTime() == null) {
            // 部分发货：订单保持待发货(2)，但「首包」应记录发货时间
            orderMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderPO>()
                            .eq(OrderPO::getId, order.getId())
                            .set(OrderPO::getConsignTime, now));
        }

        List<String> shippedSkuIds = targets.stream().map(OrderItem::getSkuId).toList();
        log.info("包裹发货: orderNo={}, packageNo={}, skuIds={}, allShipped={}",
                orderNo, packageNo, shippedSkuIds, allShipped);
        return new ShipPackageResult(order.getId(), packageNo, shippedSkuIds, allShipped);
    }

    /**
     * 解析并校验本包裹的明细（F3）：先验归属（skuIds 全部属于本订单），再验「一明细只发一次」。
     *
     * @param items  订单全部明细
     * @param skuIds 请求明细；空 = 全部未发货明细
     * @return 本包裹应标记的明细
     */
    private List<OrderItem> resolvePackageItems(List<OrderItem> items, List<String> skuIds) {
        Map<String, OrderItem> bySkuId = items.stream()
                .collect(Collectors.toMap(OrderItem::getSkuId, Function.identity(), (a, b) -> a));

        if (skuIds == null || skuIds.isEmpty()) {
            // 空 skuIds = 全部剩余未发货明细（单包裹语义兼容）
            List<OrderItem> unshipped = items.stream()
                    .filter(item -> item.getPackageNo() == null)
                    .toList();
            if (unshipped.isEmpty()) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单明细已全部发货");
            }
            return unshipped;
        }

        List<String> distinctSkuIds = skuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctSkuIds.isEmpty()) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "发货明细为空");
        }
        // 第一遍：归属校验（含他单 sku → PARAM_ERROR，无任何状态变更）
        for (String skuId : distinctSkuIds) {
            if (!bySkuId.containsKey(skuId)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "明细不属于本订单: skuId=" + skuId);
            }
        }
        // 第二遍：一明细只发一次（重复发货同一 sku → ORDER_STATUS_INVALID）
        for (String skuId : distinctSkuIds) {
            OrderItem item = bySkuId.get(skuId);
            if (item.getPackageNo() != null) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID,
                        "该明细已发货，不可重复发货: skuId=" + skuId);
            }
        }
        return distinctSkuIds.stream().map(bySkuId::get).toList();
    }

    /** 下一个包裹号 = 订单内已出现的最大包裹号 + 1（无已发包裹时为 1）。 */
    private int nextPackageNo(List<OrderItem> items) {
        int max = 0;
        for (OrderItem item : items) {
            if (item.getPackageNo() != null && item.getPackageNo() > max) {
                max = item.getPackageNo();
            }
        }
        return max + 1;
    }

    /**
     * 支付结果回写：仅允许 待付款→待发货（支付成功）/ 待付款→已取消（支付超时）两条合法边。
     * 条件更新保证并发下支付成功与超时取消只有一个能生效。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transitionFromPendingPayment(String orderNo, Integer targetStatus) {
        if (targetStatus == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "目标状态不能为空");
        }
        int to = resolvePaymentTransitionTarget(targetStatus);
        Order order = findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在: " + orderNo);
        }
        int expected = OrderStatusEnum.PENDING_PAYMENT.getCode();
        LocalDateTime now = LocalDateTime.now();
        boolean updated = orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderPO>()
                        .eq(OrderPO::getId, order.getId())
                        .eq(OrderPO::getStatus, expected)
                        .set(OrderPO::getStatus, to)
                        .set(to == OrderStatusEnum.PENDING_SHIPMENT.getCode(), OrderPO::getPayTime, now)
                        .set(to == OrderStatusEnum.CANCELLED.getCode(), OrderPO::getCloseTime, now)) > 0;
        if (!updated) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单状态已变更，拒绝回写: " + orderNo);
        }
    }

    /** 支付回调只允许「待付款 → 待发货」「待付款 → 已取消」两种目标状态。 */
    private int resolvePaymentTransitionTarget(Integer targetStatus) {
        if (targetStatus == OrderStatusEnum.PENDING_SHIPMENT.getCode()) {
            return OrderStatusEnum.PENDING_SHIPMENT.getCode();
        }
        if (targetStatus == OrderStatusEnum.CANCELLED.getCode()) {
            return OrderStatusEnum.CANCELLED.getCode();
        }
        throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "非法状态回写: " + targetStatus);
    }

    @Override
    public List<Order> listTimeoutPending(LocalDateTime cutoff) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderPO>()
                .eq(OrderPO::getStatus, OrderStatusEnum.PENDING_PAYMENT.getCode())
                .lt(OrderPO::getCreateTime, cutoff))
                .stream().map(OrderRepositoryImpl::toDomain).toList();
    }

    @Override
    public boolean closeTimeoutOrder(Long orderId) {
        return orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderPO>()
                        .eq(OrderPO::getId, orderId)
                        .eq(OrderPO::getStatus, OrderStatusEnum.PENDING_PAYMENT.getCode())
                        .set(OrderPO::getStatus, OrderStatusEnum.CANCELLED.getCode())
                        .set(OrderPO::getCloseTime, LocalDateTime.now())) > 0;
    }

    /**
     * 系统取消已支付订单（F13 拼团退款闭环，ADR-049）—— 条件更新 仅待发货(2)→已取消(5)，
     * 无归属校验（调用方为系统闭环编排）；单条原子 UPDATE，无需显式事务。
     * 受影响行数 0 = 订单已非待发货（已取消/已发货等，幂等无操作），由编排层跳过后续补偿。
     */
    @Override
    public boolean cancelPaidBySystem(String orderNo) {
        return orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderPO>()
                        .eq(OrderPO::getOrderNo, IdGenerator.toBytes(orderNo))
                        .eq(OrderPO::getStatus, OrderStatusEnum.PENDING_SHIPMENT.getCode())
                        .set(OrderPO::getStatus, OrderStatusEnum.CANCELLED.getCode())
                        .set(OrderPO::getCloseTime, LocalDateTime.now())) > 0;
    }

    // ==================== 统计读模型（CQRS 直查） ====================

    @Override
    public long countAll() {
        return orderMapper.selectCount(null);
    }

    @Override
    public long countByStatus(Integer status) {
        return orderMapper.selectCount(new LambdaQueryWrapper<OrderPO>().eq(OrderPO::getStatus, status));
    }

    @Override
    public long countByCreateTimeAfter(LocalDateTime start) {
        return orderMapper.selectCount(new LambdaQueryWrapper<OrderPO>().ge(OrderPO::getCreateTime, start));
    }

    @Override
    public long countByCreateTimeBetween(LocalDateTime start, LocalDateTime end) {
        return orderMapper.selectCount(new LambdaQueryWrapper<OrderPO>()
                .ge(OrderPO::getCreateTime, start).lt(OrderPO::getCreateTime, end));
    }

    @Override
    public List<Order> findByStatus(Integer status) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderPO>().eq(OrderPO::getStatus, status))
                .stream().map(OrderRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Order> findByStatusAndCreateTimeAfter(Integer status, LocalDateTime start) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderPO>()
                        .eq(OrderPO::getStatus, status).ge(OrderPO::getCreateTime, start))
                .stream().map(OrderRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Order> findByStatusAndCreateTimeBetween(Integer status, LocalDateTime start, LocalDateTime end) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderPO>()
                        .eq(OrderPO::getStatus, status)
                        .ge(OrderPO::getCreateTime, start).lt(OrderPO::getCreateTime, end))
                .stream().map(OrderRepositoryImpl::toDomain).toList();
    }

    @Override
    public long countBySellerAndCreateTimeAfter(Long sellerId, LocalDateTime start) {
        return orderMapper.selectCount(new LambdaQueryWrapper<OrderPO>()
                .eq(OrderPO::getSellerId, sellerId).ge(OrderPO::getCreateTime, start));
    }

    @Override
    public long countBySellerAndStatus(Long sellerId, Integer status) {
        return orderMapper.selectCount(new LambdaQueryWrapper<OrderPO>()
                .eq(OrderPO::getSellerId, sellerId).eq(OrderPO::getStatus, status));
    }

    @Override
    public List<Order> findBySellerAndCreateTimeAfter(Long sellerId, LocalDateTime start) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderPO>()
                        .eq(OrderPO::getSellerId, sellerId).ge(OrderPO::getCreateTime, start))
                .stream().map(OrderRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Order> findBySellerAndStatusAndCreateTimeAfter(Long sellerId, Integer status, LocalDateTime start) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderPO>()
                        .eq(OrderPO::getSellerId, sellerId)
                        .eq(OrderPO::getStatus, status).ge(OrderPO::getCreateTime, start))
                .stream().map(OrderRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Order> findBySellerAndStatusAndEndTimeBetween(Long sellerId, Integer status,
                                                              LocalDateTime start, LocalDateTime end) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderPO>()
                        .eq(OrderPO::getSellerId, sellerId)
                        .eq(OrderPO::getStatus, status)
                        .ge(OrderPO::getEndTime, start).lt(OrderPO::getEndTime, end))
                .stream().map(OrderRepositoryImpl::toDomain).toList();
    }

    // ==================== PO <-> 聚合 ====================

    static Order toDomain(OrderPO po) {
        if (po == null) {
            return null;
        }
        Order order = new Order();
        order.setId(po.getId());
        order.setOrderNo(IdGenerator.toHexString(po.getOrderNo()));
        order.setUserId(po.getUserId());
        order.setSellerId(po.getSellerId());
        order.setTotalAmount(po.getTotalAmount());
        order.setPayAmount(po.getPayAmount());
        order.setDiscountAmount(po.getDiscountAmount());
        order.setPostFee(po.getPostFee());
        order.setRemark(po.getRemark());
        order.setDisplayCurrency(po.getDisplayCurrency());
        order.setDisplayRate(po.getDisplayRate());
        order.setStatus(po.getStatus());
        order.setOrderType(po.getOrderType());
        order.setPaymentType(po.getPaymentType());
        order.setReceiverName(po.getReceiverName());
        order.setReceiverPhone(po.getReceiverPhone());
        order.setReceiverAddress(po.getReceiverAddress());
        order.setCreateTime(po.getCreateTime());
        order.setPayTime(po.getPayTime());
        order.setConsignTime(po.getConsignTime());
        order.setEndTime(po.getEndTime());
        order.setCloseTime(po.getCloseTime());
        order.setCommentTime(po.getCommentTime());
        order.setVersion(po.getVersion());
        order.setUpdateTime(po.getUpdateTime());
        return order;
    }

    static OrderPO toPO(Order order) {
        OrderPO po = new OrderPO();
        po.setId(order.getId());
        po.setOrderNo(IdGenerator.toBytes(order.getOrderNo()));
        po.setUserId(order.getUserId());
        po.setSellerId(order.getSellerId());
        po.setTotalAmount(order.getTotalAmount());
        po.setPayAmount(order.getPayAmount());
        po.setDiscountAmount(order.getDiscountAmount());
        po.setPostFee(order.getPostFee());
        po.setRemark(order.getRemark());
        po.setDisplayCurrency(order.getDisplayCurrency());
        po.setDisplayRate(order.getDisplayRate());
        po.setStatus(order.getStatus());
        po.setOrderType(order.getOrderType());
        po.setPaymentType(order.getPaymentType());
        po.setReceiverName(order.getReceiverName());
        po.setReceiverPhone(order.getReceiverPhone());
        po.setReceiverAddress(order.getReceiverAddress());
        po.setCreateTime(order.getCreateTime());
        po.setPayTime(order.getPayTime());
        po.setConsignTime(order.getConsignTime());
        po.setEndTime(order.getEndTime());
        po.setCloseTime(order.getCloseTime());
        po.setCommentTime(order.getCommentTime());
        po.setVersion(order.getVersion());
        po.setUpdateTime(order.getUpdateTime());
        return po;
    }

    static OrderItem toDomain(OrderItemPO po) {
        if (po == null) {
            return null;
        }
        OrderItem item = new OrderItem();
        item.setId(po.getId());
        item.setOrderId(po.getOrderId());
        // BINARY(16) → 32-hex 只在此处发生（仓储边界，ADR-050 编解码唯一出口）
        item.setSkuId(IdGenerator.toHexString(po.getSkuId()));
        item.setSpuId(IdGenerator.toHexString(po.getSpuId()));
        item.setSpuName(po.getSpuName());
        item.setSkuSpec(po.getSkuSpec());
        item.setPrice(po.getPrice());
        item.setQuantity(po.getQuantity());
        item.setSubTotal(po.getSubTotal());
        item.setImage(po.getImage());
        item.setPackageNo(po.getPackageNo());
        item.setCreateTime(po.getCreateTime());
        item.setUpdateTime(po.getUpdateTime());
        return item;
    }

    static OrderItemPO toPO(OrderItem item) {
        OrderItemPO po = new OrderItemPO();
        po.setId(item.getId());
        po.setOrderId(item.getOrderId());
        // 32-hex → BINARY(16)：非法/缺省空值经 toBytesOrNull 归一（写路径非 NOT NULL 列语义）
        po.setSkuId(IdGenerator.toBytesOrNull(item.getSkuId()));
        po.setSpuId(IdGenerator.toBytesOrNull(item.getSpuId()));
        po.setSpuName(item.getSpuName());
        po.setSkuSpec(item.getSkuSpec());
        po.setPrice(item.getPrice());
        po.setQuantity(item.getQuantity());
        po.setSubTotal(item.getSubTotal());
        po.setImage(item.getImage());
        po.setPackageNo(item.getPackageNo());
        po.setCreateTime(item.getCreateTime());
        po.setUpdateTime(item.getUpdateTime());
        return po;
    }
}

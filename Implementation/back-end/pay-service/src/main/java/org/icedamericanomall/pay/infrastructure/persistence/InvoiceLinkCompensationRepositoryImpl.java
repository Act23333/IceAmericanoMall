package org.icedamericanomall.pay.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkCompensationStatusEnum;
import org.icedamericanomall.pay.domain.model.invoicelink.InvoiceLinkCompensation;
import org.icedamericanomall.pay.domain.repository.InvoiceLinkCompensationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发票联动补偿仓储实现 —— MyBatis-Plus 访问 {@code invoice_link_compensation} 表，
 * PO ↔ 聚合转换内聚于此（PO 不越过基础设施层）。
 *
 * <p><b>幂等实现 = 先插后捕</b>：直接 {@code INSERT}，撞 {@code uk_order_scene_kind} 时捕获
 * Spring 翻译后的 {@link DuplicateKeyException} 并归一为「幂等命中」（返回 {@code false}）。
 * 不用「先查后插」—— 并发下两个线程可能同时查空再同时插，唯一键才是真正的幂等锚点
 * （与 cart 去重、{@code RefundOrderRepositoryImpl} 撞 {@code uk_biz_ref} 同一思路）。
 *
 * <p><b>状态迁移一律条件更新</b>：{@code WHERE id = ? AND status = PENDING} 落在
 * {@link InvoiceLinkCompensationMapper} 的显式 SQL 上（不变式需契约化时用显式 SQL，
 * 理由见该 Mapper 类注释）。本类只负责「状态码取自枚举 + 摘要按列宽截断 + 聚合 ↔ PO 转换」，
 * 命中行数即成败 —— <b>绝不</b>把 {@code status} 读到内存里判完再写回。
 *
 * <p><b>事务</b>：单条 {@code INSERT} / {@code UPDATE} 自提交，本类刻意不加 {@code @Transactional}
 * —— 调用点 {@code PayEventNotifier} 与补偿重试任务都调 Feign，事务边界必须留在外层之外。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class InvoiceLinkCompensationRepositoryImpl implements InvoiceLinkCompensationRepository {

    /** 期望当前状态（重试任务只推进待补偿行；终态行一律不动）。 */
    private static final int PENDING = InvoiceLinkCompensationStatusEnum.PENDING.getCode();

    /** 目标状态：已补偿。 */
    private static final int DONE = InvoiceLinkCompensationStatusEnum.DONE.getCode();

    /** 目标状态：永久失败。 */
    private static final int FAILED_PERMANENT = InvoiceLinkCompensationStatusEnum.FAILED_PERMANENT.getCode();

    private final InvoiceLinkCompensationMapper invoiceLinkCompensationMapper;

    @Override
    public boolean createPending(InvoiceLinkCompensation compensation) {
        InvoiceLinkCompensationPO po = toPO(compensation);
        try {
            invoiceLinkCompensationMapper.insert(po);
            compensation.setId(po.getId());
            return true;
        } catch (DuplicateKeyException e) {
            // 幂等命中：同一 (order_no, scene_ref, kind) 已有补偿记录，无需重复堆积
            log.info("[refund-invoice][compensation][exists] 补偿记录已存在，幂等命中（uk_order_scene_kind）: "
                            + "orderNo={}, sceneRef={}, kind={}",
                    compensation.getOrderNo(), compensation.getSceneRef(), compensation.getKind());
            return false;
        }
    }

    @Override
    public List<InvoiceLinkCompensation> findDueForRetry(int limit) {
        return invoiceLinkCompensationMapper.selectDueForRetry(PENDING, LocalDateTime.now(), limit)
                .stream().map(InvoiceLinkCompensationRepositoryImpl::toDomain).toList();
    }

    @Override
    public boolean markDone(Long id) {
        boolean updated = invoiceLinkCompensationMapper.markDoneIfPending(id, PENDING, DONE) > 0;
        if (!updated) {
            log.info("[refund-invoice][retry][skip] 记录已不在待补偿态，置已补偿不生效: id={}", id);
        }
        return updated;
    }

    @Override
    public boolean scheduleRetry(Long id, int retryCount, LocalDateTime nextRetryAt, String lastError) {
        boolean updated = invoiceLinkCompensationMapper.scheduleRetryIfPending(
                id, PENDING, retryCount, nextRetryAt, truncate(lastError)) > 0;
        if (!updated) {
            log.info("[refund-invoice][retry][skip] 记录已不在待补偿态，退避写入不生效: id={}", id);
        }
        return updated;
    }

    @Override
    public boolean markFailedPermanent(Long id, String lastError) {
        boolean updated = invoiceLinkCompensationMapper.markFailedPermanentIfPending(
                id, PENDING, FAILED_PERMANENT, truncate(lastError)) > 0;
        if (!updated) {
            log.info("[refund-invoice][retry][skip] 记录已不在待补偿态，置永久失败不生效: id={}", id);
        }
        return updated;
    }

    /**
     * 摘要按列宽截断（{@link InvoiceLinkCompensation#LAST_ERROR_MAX_LENGTH}，与 {@code VARCHAR(500)} 一致）。
     *
     * <p>聚合工厂 {@code newPending} 已做一次截断，但重试路径的摘要来自运行期异常拼接
     * （异常类型 + 错误码 + {@code category} + message，message 无长度上限），必须在此再截一次 ——
     * 否则一条超长上游报文会让「记录失败原因」这一步自身失败（MySQL 严格模式下直接报错）。
     */
    private static String truncate(String lastError) {
        if (lastError == null || lastError.length() <= InvoiceLinkCompensation.LAST_ERROR_MAX_LENGTH) {
            return lastError;
        }
        return lastError.substring(0, InvoiceLinkCompensation.LAST_ERROR_MAX_LENGTH);
    }

    /**
     * PO → 聚合（{@code order_no} 经 {@link IdGenerator#toWire(byte[])} 还原 32 位 hex 业务键，ADR-062）。
     *
     * <p><b>走唯一编解码出口</b>：{@code byte[]} → String 只许 `IdGenerator.toWire` /
     * `toHexString` 一处实现；仓储各自手写 {@code HexFormat} 是 ADR-062 §决策 1 明确禁止的。
     * 还原出的 32-hex 才是对外口径 —— 补偿重试要把它原样放进 {@code InvoiceVoidCO.orderNo}
     * 传给 trade-service（trade 侧按 wire 形态寻址，不认 BINARY）。
     */
    private static InvoiceLinkCompensation toDomain(InvoiceLinkCompensationPO po) {
        return InvoiceLinkCompensation.builder()
                .id(po.getId())
                .orderNo(IdGenerator.toWire(po.getOrderNo()))
                .sceneRef(po.getSceneRef())
                .kind(po.getKind())
                .status(po.getStatus())
                .retryCount(po.getRetryCount())
                .nextRetryAt(po.getNextRetryAt())
                .lastError(po.getLastError())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    /**
     * 聚合 → PO（{@code order_no} 经 {@link IdGenerator#toBytes} 转 BINARY(16) 原始字节，ADR-050）。
     *
     * <p>{@code create_time} / {@code update_time} 交给审计字段填充器
     * （{@code AuditFieldMetaObjectHandler}），此处不赋值。
     */
    private static InvoiceLinkCompensationPO toPO(InvoiceLinkCompensation compensation) {
        InvoiceLinkCompensationPO po = new InvoiceLinkCompensationPO();
        po.setOrderNo(IdGenerator.toBytes(compensation.getOrderNo()));
        po.setSceneRef(compensation.getSceneRef());
        po.setKind(compensation.getKind());
        po.setStatus(compensation.getStatus());
        po.setRetryCount(compensation.getRetryCount());
        po.setNextRetryAt(compensation.getNextRetryAt());
        po.setLastError(compensation.getLastError());
        return po;
    }
}

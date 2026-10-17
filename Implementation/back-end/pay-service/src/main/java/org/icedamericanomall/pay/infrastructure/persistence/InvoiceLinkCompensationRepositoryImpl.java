package org.icedamericanomall.pay.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.pay.domain.model.invoicelink.InvoiceLinkCompensation;
import org.icedamericanomall.pay.domain.repository.InvoiceLinkCompensationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 发票联动补偿仓储实现 —— MyBatis-Plus 访问 {@code invoice_link_compensation} 表，
 * PO ↔ 聚合转换内聚于此（PO 不越过基础设施层）。
 *
 * <p><b>幂等实现 = 先插后捕</b>：直接 {@code INSERT}，撞 {@code uk_order_scene_kind} 时捕获
 * Spring 翻译后的 {@link DuplicateKeyException} 并归一为「幂等命中」（返回 {@code false}）。
 * 不用「先查后插」—— 并发下两个线程可能同时查空再同时插，唯一键才是真正的幂等锚点
 * （与 cart 去重、{@code RefundOrderRepositoryImpl} 撞 {@code uk_biz_ref} 同一思路）。
 *
 * <p><b>事务</b>：单条 {@code INSERT} 自提交，本类刻意不加 {@code @Transactional}
 * —— 调用点 {@code PayEventNotifier} 调 Feign，事务边界必须留在外层之外。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class InvoiceLinkCompensationRepositoryImpl implements InvoiceLinkCompensationRepository {

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

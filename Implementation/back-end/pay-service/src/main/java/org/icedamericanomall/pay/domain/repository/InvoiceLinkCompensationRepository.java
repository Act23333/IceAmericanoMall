package org.icedamericanomall.pay.domain.repository;

import org.icedamericanomall.pay.domain.model.invoicelink.InvoiceLinkCompensation;

/**
 * 发票联动补偿仓储（领域端口）—— 实现方：{@code infrastructure.persistence.InvoiceLinkCompensationRepositoryImpl}。
 *
 * <p><b>幂等锚点</b>：{@code (order_no, scene_ref, kind)}（{@code uk_order_scene_kind}）。
 * 同一退款场景的同一类联动重复失败只留一条记录；重复写入<b>不是错误</b>，
 * 由数据库唯一键兜底并在此归一为「幂等命中」。
 */
public interface InvoiceLinkCompensationRepository {

    /**
     * 新增「待补偿」记录（幂等）。
     *
     * <p><b>不抛 {@code DuplicateKeyException}</b>：撞唯一键 = 该补偿已存在，返回 {@code false} 即可。
     * 其余持久化异常（连接不可用等）照常向上抛 —— 是否吞掉由调用方决定（退款主流程会吞并告警）。
     *
     * @param compensation 待补偿记录（由 {@link InvoiceLinkCompensation#newPending} 装配，{@code id} 忽略）
     * @return {@code true} 新建成功；{@code false} 幂等命中（同 {@code (order_no, scene_ref, kind)} 已存在）
     */
    boolean createPending(InvoiceLinkCompensation compensation);
}

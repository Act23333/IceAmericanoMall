package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发票联动补偿记录实体 —— 持久化模型（{@code invoice_link_compensation} 表）。
 *
 * <pre>
 * 状态机 (统一见 {@link org.icedamericanomall.pay.domain.enums.InvoiceLinkCompensationStatusEnum}):
 *   0-PENDING → 1-DONE
 *             ↘ 2-FAILED_PERMANENT
 *
 * 不变量:
 *   1. (order_no, scene_ref, kind) 唯一（uk_order_scene_kind）—— 幂等锚点
 *   2. status 落库初态恒为 0（PENDING），retry_count 初值恒为 0
 *   3. next_retry_at 落库即当前时间（调度器就绪后即刻可拾取）
 *   4. lease_until 只在「已被某个执行者认领且尚未释放」期间非 NULL；三条状态迁移
 *      （DONE / 退避 / FAILED_PERMANENT）一律把它置回 NULL —— 终态行与退避行不携带无用租约
 * </pre>
 *
 * <p><b>BINARY(16) ↔ byte[]</b>：与 {@code refund_order} / {@code pay_order} 同口径（ADR-050）——
 * {@code order_no} 在 PO 上是 {@code byte[]}，32 位 hex 形态只存在于领域层，编解码一律经
 * {@code IdGenerator}（包装器查询传 {@code String} 会静默查空，禁止）。
 */
@Data
@TableName("invoice_link_compensation")
public class InvoiceLinkCompensationPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联订单号（orders.order_no 复制，BINARY(16) ↔ byte[]，ADR-050） */
    private byte[] orderNo;

    /** 联动场景号（= refund_order.biz_ref，如 {@code AS:123} / {@code GB:{orderNo}}；VARCHAR，直传 String） */
    private String sceneRef;

    /** 补偿类型：1=VOID 全额冲红（I56）/ 2=PARTIAL 部分退款置标（I60）。 */
    private Integer kind;

    /** 状态：0=待补偿 / 1=已补偿 / 2=永久失败。 */
    private Integer status;

    /** 已重试次数。 */
    private Integer retryCount;

    /** 下次可拾取时间（落库即当前时间）。 */
    private LocalDateTime nextRetryAt;

    /**
     * 认领租约到期时间（{@code NULL} = 未认领）。
     *
     * <p><b>仅在持久化层存在</b>：它是「谁正在处理这一行」的<b>技术租约</b>，不是业务状态 ——
     * 故不映射到领域聚合 {@code InvoiceLinkCompensation}（四层纪律：领域模型只表达业务语义，
     * 租约/时间盒属基础设施的并发控制机制）。到期后该行自动回到可认领集合，无需回收任务。
     */
    private LocalDateTime leaseUntil;

    /** 最近一次失败原因摘要（≤500 字符）。 */
    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

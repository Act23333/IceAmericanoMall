package org.icedamericanomall.pay.domain.enums;

import lombok.Getter;

/**
 * 发票联动补偿状态枚举 —— 对应 {@code invoice_link_compensation.status}。
 *
 * <pre>
 * 状态流转:
 *   0-PENDING(待补偿) → 1-DONE(已补偿)              [重试/人工补发成功]
 *                    ↘ 2-FAILED_PERMANENT(永久失败) [重试次数耗尽或人工终止]
 * </pre>
 *
 * <p><b>本批范围</b>：只写 {@link #PENDING}（落库即待补偿，{@code next_retry_at} 写入当前时间，
 * 调度器就绪后即刻可拾取）；{@link #DONE} / {@link #FAILED_PERMANENT} 由后续批次的重试任务与
 * 管理端人工收敛推进，本批不产生该两种状态 —— 枚举先行登记是为了让状态语义只有一处定义，
 * 避免后续批次在调度器里手写 {@code 1} / {@code 2} 魔法值。
 */
@Getter
public enum InvoiceLinkCompensationStatusEnum {

    /** 待补偿（落库初态，等待重试任务或人工处理）。 */
    PENDING(0, "待补偿"),

    /** 已补偿（重试/人工补发成功，终态）。 */
    DONE(1, "已补偿"),

    /** 永久失败（重试次数耗尽或人工终止，终态）。 */
    FAILED_PERMANENT(2, "永久失败");

    private final int code;
    private final String desc;

    InvoiceLinkCompensationStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

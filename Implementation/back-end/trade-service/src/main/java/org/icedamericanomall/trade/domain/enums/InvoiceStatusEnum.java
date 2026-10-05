package org.icedamericanomall.trade.domain.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 发票单状态机（PRD §15.4.1，F14 发票）。
 *
 * <pre>
 *   1-PENDING_ISSUE 待开具 → 2-ISSUED 已开具（管理端模拟开具）
 *                          ↘ 4-VOIDED 已作废（买家撤回 / 全额退款守卫 / 管理端作废）
 *   2-ISSUED 已开具 → 3-RED_FLUSHED 已冲红（全额退款自动 / 管理端冲红 / 换开前置）
 *   3-RED_FLUSHED / 4-VOIDED 为终态，不可再迁出（重复触发 = 幂等空操作）
 * </pre>
 *
 * <p>守卫红线：已开具不可作废（只能冲红，防"作废已开票"的合规漏洞）；
 * 所有迁移由仓储「条件更新」（WHERE 带期望状态）承载，0 行 = 状态冲突。
 */
@Getter
public enum InvoiceStatusEnum {

    /** 待开具（买家申请成功后的初始态，占有效票槽位） */
    PENDING_ISSUE(1, "待开具"),

    /** 已开具（模拟开具成功，含模拟票号与样票 PDF） */
    ISSUED(2, "已开具"),

    /** 已冲红（终态：全额退款自动 / 管理端冲红 / 换开前置） */
    RED_FLUSHED(3, "已冲红"),

    /** 已作废（终态：买家撤回 / 全额退款守卫 / 管理端作废） */
    VOIDED(4, "已作废");

    private final int code;
    private final String desc;

    InvoiceStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按状态码解析枚举。
     *
     * @param code 状态码
     * @return 命中的枚举；未命中返回 {@code null}
     */
    public static InvoiceStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElse(null);
    }
}

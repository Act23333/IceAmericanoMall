package org.icedamericanomall.user.domain.enums;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;

/**
 * 用户状态（领域枚举，纯 POJO —— 无 MyBatis 注解）。
 *
 * <p>取值与 {@code user.status} 列一致：{@code 1}=正常，{@code 0}=禁用。
 * 持久化层用 {@code Integer} 映射该列，PO ↔ 领域模型转换在
 * {@code infrastructure/persistence/user/UserConverter} 完成，
 * 避免把 ORM 的 {@code @EnumValue} 依赖带进领域层。
 */
public enum UserStatusEnum {

    FROZEN(0, "禁止使用"),
    NORMAL(1, "已激活"),
    ;

    private final Integer code;
    private final String description;

    UserStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 状态码（与 {@code user.status} 列取值一致）。 */
    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 从状态码还原；<b>非法值 = 参数非法</b>，抛 {@code PARAM_ERROR(1101) / HTTP 400}
     * （管理后台入参校验）。
     *
     * <p><b>语义订正（2026-10-18 批次 ⑤b）</b>：此前抛
     * {@code USER_STATUS_ABNORMAL(3008)} —— 该码的状态码已被调成 {@code 401}（为满足登录链路的
     * 「未认证 → 401」），于是<b>与鉴权毫无关系</b>的一次「status 传了 99」被回成 401：
     * 前端据此清凭证并跳登录页，把一个后台表单填错变成了「用户被登出」。
     * 按 {@code collaboration/09-constraints.md} §三·六：<b>参数非法 → 400</b>；
     * {@code USER_STATUS_ABNORMAL} 只表示「账号状态异常」，<b>不得</b>用于鉴权失败或入参校验。
     *
     * <p>文案保持不变（{@code 账户状态错误}）：本次只订正 HTTP 状态码与业务码，
     * 不改变对外的可读提示，也不改变「0=禁用 / 1=正常」的取值口径。
     *
     * @param value 状态码（0=禁用，1=正常）
     * @return 对应枚举
     * @throws BadRequestException 取值不是 0/1（400 {@code PARAM_ERROR}）
     */
    public static UserStatusEnum of(int value) {
        if (value == 0) {
            return FROZEN;
        }
        if (value == 1) {
            return NORMAL;
        }
        throw new BadRequestException(ErrorCode.PARAM_ERROR, "账户状态错误");
    }

    /** 从状态码还原（null 安全，供持久化转换使用）。 */
    public static UserStatusEnum fromCode(Integer value) {
        return value == null ? null : of(value);
    }
}

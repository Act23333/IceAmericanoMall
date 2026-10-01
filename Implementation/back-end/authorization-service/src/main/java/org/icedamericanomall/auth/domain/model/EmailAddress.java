package org.icedamericanomall.auth.domain.model;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 邮箱地址（值对象）—— 不可变、自封装（创建即校验并归一化）。
 *
 * <p>为什么需要它：邮箱既是"登录标识"又是"验证码投递目标"，且有两处必须保持一致的规则——
 * <ol>
 *   <li><b>格式校验</b>：非法邮箱不得发起发码/校验（避免打爆下游与污染数据）</li>
 *   <li><b>归一化</b>：邮箱大小写不敏感，统一 {@code trim + lowercase} 后，
 *       盲索引（HMAC）、Redis 验证码 Key（{@code email:code:{target}}）与
 *       user-service 的建号标识才能三方对齐——否则 {@code A@x.com} 与 {@code a@x.com}
 *       会被当成两个用户/两条验证码</li>
 * </ol>
 *
 * <p>纯业务值对象：不含任何框架依赖，不携带技术主键。
 */
public record EmailAddress(String value) {

    /** 邮箱格式（与前端 {@code @Email} 校验保持同源语义：本地部分 + 域名 + 至少两位顶级域） */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    /** RFC 5321 限制的邮箱最大长度 */
    private static final int MAX_LENGTH = 254;

    /** 紧凑构造器：归一化 + 校验（不合法即拒绝创建） */
    public EmailAddress {
        value = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "邮箱不能为空");
        }
        if (value.length() > MAX_LENGTH || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "邮箱格式不正确");
        }
    }

    /**
     * 从明文创建邮箱值对象（归一化 + 校验）。
     *
     * @param raw 用户提交的邮箱
     * @return 邮箱值对象
     * @throws BizException 格式不合法
     */
    public static EmailAddress of(String raw) {
        return new EmailAddress(raw);
    }

    /**
     * 盲索引（HMAC-SHA256）：用于按邮箱查询本地认证凭证，明文不落库检索列。
     *
     * @return 十六进制盲索引
     */
    public String blindIndex() {
        return BlindIndexUtil.hmac(value);
    }
}

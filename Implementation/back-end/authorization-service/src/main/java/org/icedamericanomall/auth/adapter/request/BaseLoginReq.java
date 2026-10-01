package org.icedamericanomall.auth.adapter.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 登录请求公共字段（各端登录请求继承）。
 *
 * <p>人机验证参数（全部可选，<b>增量字段</b>，旧前端不传不受影响）：
 * <ul>
 *   <li>字段与极验 v4「二次校验」四要素一一对应，由适配层映射为
 *       {@code LoginCommand.captchaVerify}</li>
 *   <li>{@code captchaId} 由服务端生成/下发（随 1016 CAPTCHA_REQUIRED 的 payload 返回给前端），
 *       <b>不接受</b>前端传入，避免前端伪造 captcha_id 打其他站点</li>
 * </ul>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseLoginReq {

    /**
     * 验证码凭证（可选）。
     *
     * @deprecated 历史字段，从未被服务端读取；人机验证请使用
     * {@link #lotNumber} / {@link #captchaOutput} / {@link #passToken} / {@link #genTime}。
     * 保留字段仅为兼容旧前端请求体（服务端忽略，遇到非空值时打 WARN 提示迁移）。
     */
    @Deprecated(since = "V5.1")
    private String captcha;

    /** 人机验证厂商（可选，缺省取服务端配置的默认厂商，如 GEETEST） */
    private String captchaProvider;

    /** 极验 v4：验证流水号 */
    private String lotNumber;

    /** 极验 v4：验证输出信息 */
    private String captchaOutput;

    /** 极验 v4：验证通过标识 */
    private String passToken;

    /** 极验 v4：验证通过时间戳（毫秒） */
    private String genTime;
}

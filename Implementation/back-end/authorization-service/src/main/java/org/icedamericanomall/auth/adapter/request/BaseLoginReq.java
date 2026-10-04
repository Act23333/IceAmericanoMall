package org.icedamericanomall.auth.adapter.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * 登录请求公共字段（各端登录请求继承）。
 *
 * <p>人机验证参数（厂商中立，全部可选，<b>增量字段</b>，旧前端不传不受影响）：
 * <ul>
 *   <li>{@code captchaProvider} + {@code captchaParams} 由适配层映射为
 *       {@code LoginCommand.captchaVerify}，原样透传 verification-service 按厂商解释
 *       （极验 v4 的 key：lotNumber / captchaOutput / passToken / genTime）。
 *       更换人机验证厂商时本契约不变，只换 {@code captchaParams} 的 key 集</li>
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
     * {@link #captchaProvider} + {@link #captchaParams}。
     * 保留字段仅为兼容旧前端请求体（服务端忽略，遇到非空值时打 WARN 提示迁移）。
     */
    @Deprecated(since = "V5.1")
    private String captcha;

    /** 人机验证厂商（可选，缺省取服务端配置的默认厂商，如 GEETEST） */
    private String captchaProvider;

    /** 人机验证参数集（厂商中立；key 由前端验证组件的厂商 schema 决定） */
    private Map<String, String> captchaParams;
}

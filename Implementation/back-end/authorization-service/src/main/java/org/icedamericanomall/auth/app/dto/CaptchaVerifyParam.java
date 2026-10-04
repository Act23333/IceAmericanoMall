package org.icedamericanomall.auth.app.dto;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 人机验证凭证（应用层入参值对象）—— 厂商中立的"厂商 + 不透明参数集"。
 *
 * <p>本上下文不感知厂商 schema：params 原样透传 verification-service（经 ia-api
 * 厂商中立契约 {@code CaptchaVerifyReqCO}），由后者按 provider 解包并路由厂商适配器
 * （ADR-007：二次校验收口 verification-service）。参数完整性校验同样在
 * verification-service 按厂商定义执行（残缺 → 校验失败，fail-closed），
 * 本值对象只判定"是否携带"。
 *
 * @param provider 人机验证厂商（GEETEST / ...），为空时由基础设施层取配置默认厂商
 * @param params   厂商校验参数集（key 由厂商 schema 决定；极验 v4：lotNumber/captchaOutput/passToken/genTime）
 * @param clientIp 客户端 IP（风控归因，由适配层解析）
 */
public record CaptchaVerifyParam(String provider, Map<String, String> params, String clientIp) {

    /** 紧凑构造器：防御性拷贝并剔除 null 项（前端可能显式传 null 值字段）。 */
    public CaptchaVerifyParam {
        params = params == null ? Map.of()
                : params.entrySet().stream()
                        .filter(e -> e.getKey() != null && e.getValue() != null)
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 装配入口（适配层调用）：未携带任一参数时返回 {@code null}（{@code LoginCommand.captchaVerify}
     * 为空 → 应用层跳过人机验证）；携带则整组构造。
     */
    public static CaptchaVerifyParam of(String provider, Map<String, String> params, String clientIp) {
        CaptchaVerifyParam param = new CaptchaVerifyParam(provider, params, clientIp);
        return param.isProvided() ? param : null;
    }

    /**
     * 是否携带了人机验证参数（参数集中任一非空即算携带）。
     *
     * <p>用「任一非空」而非「全部非空」：否则前端只传一个残缺字段就会被当成"未携带"而
     * <b>跳过</b>校验，形成绕过面。残缺参数会照常提交，由 verification-service 按厂商
     * schema 判定并拒绝（fail-closed）。
     */
    public boolean isProvided() {
        return params.values().stream().anyMatch(v -> !v.isBlank());
    }
}

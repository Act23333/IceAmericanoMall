package org.icedamericanomall.verification.app.command;

import java.util.Map;

/**
 * 人机验证命令（应用层入参）。
 *
 * <p><b>厂商中立</b>：命令只携带 provider + 不透明参数集，厂商专属字段（如极验四要素）
 * 由基础设施层 {@code CaptchaVerifyServiceImpl} 按 provider 解包，应用层与领域层不感知厂商。
 *
 * @param provider 人机验证厂商（GEETEST / ...），为空时用默认厂商
 * @param params   厂商校验参数集（key 由厂商 schema 决定，如极验 v4 四要素）
 * @param clientIp 客户端 IP（风控归因）
 */
public record CaptchaVerifyCommand(String provider,
                                   Map<String, String> params,
                                   String clientIp) {

    /** 紧凑构造器：防御性拷贝，null 视为空参数集。 */
    public CaptchaVerifyCommand {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}

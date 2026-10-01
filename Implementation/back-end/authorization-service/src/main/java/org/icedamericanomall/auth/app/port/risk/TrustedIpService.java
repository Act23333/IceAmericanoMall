package org.icedamericanomall.auth.app.port.risk;

/**
 * IP 黑白名单校验能力（应用层端口，风控维度子端口）。
 */
public interface TrustedIpService {

    /** IP 是否在黑名单中 */
    boolean isBlacklisted(String clientIp);

    /** IP 是否在白名单中 */
    boolean isTrusted(String clientIp);
}

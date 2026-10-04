package org.icedamericanomall.auth.app.port;

/**
 * 访问令牌吊销能力（应用层端口，基础设施层用 Redis 实现）—— <b>单 token（jti）粒度，仅登出使用</b>。
 *
 * <p>用户级吊销（改密 / 重置密码 / 管理端禁用）不走本端口，见
 * {@link TokenVersionService}（令牌版本号 + 全端 refresh 会话拆除，ADR-031）。
 *
 * <p><b>跨服务契约键</b>：实现写入 {@code auth:blacklist:{jti}}，由 gateway-service 的
 * {@code JwtBlacklistFilter} 读取（与 {@code user:info:} / {@code user:perms:} 同级，见 ADR-027）。
 * 键名与 TTL 语义固化在实现内，调用方只描述"哪个令牌该失效"。
 *
 * <p>调用语义：幂等、尽力而为（fail-open）—— 令牌缺失或存储异常时仅记录日志，
 * 不抛异常、不阻断调用方主流程（登出不得因缓存故障而失败，与网关读侧 fail-open 对称）。
 */
public interface TokenBlacklistService {

    /**
     * 拉黑一个访问令牌（写入网关可读的吊销名单）。
     *
     * @param accessToken 原始访问令牌（不含 {@code Bearer } 前缀）；为 {@code null}/空白或
     *                    缺少 {@code jti} 时跳过并记录 DEBUG 日志
     */
    void blacklist(String accessToken);
}

package org.icedamericanomall.auth.app.port;

/**
 * 用户级令牌版本能力（应用层端口，基础设施层用 Redis 实现）。
 *
 * <p>与 {@link TokenBlacklistService}（单 token = jti 粒度，仅登出使用）互补，
 * 本端口提供<b>用户级</b>吊销：改密 / 重置密码 / 管理端禁用时推进版本号，
 * 该用户此前签发的全部 access token 因 {@code ver} claim 落后而立即失效（见 ADR-031）。
 *
 * <p><b>跨服务契约键</b>：实现读写 {@code auth:tokenver:{userId}}，由 gateway-service 的
 * {@code JwtBlacklistFilter} 读取（与 {@code user:info:} / {@code user:perms:} /
 * {@code auth:blacklist:} 同级，见 ADR-031）。键名与语义固化在实现内，调用方只描述"哪个用户该被吊销"。
 *
 * <p><b>版本语义</b>：
 * <ul>
 *   <li>键缺失 = 版本 {@code 0}（存量令牌无 {@code ver} claim，同样按 {@code 0} 处理，无需迁移）；</li>
 *   <li>推进版本使用 {@code INCR}，键<b>不设 TTL</b> —— 过期会使版本回落为 {@code 0}，把已吊销令牌"复活"；</li>
 *   <li>调用语义：幂等（重复推进只是版本更靠前，不影响正确性）、尽力而为（fail-open）——
 *       存储异常时仅记录 WARN，不抛异常、不阻断调用方主流程（改密 / 禁用不得因缓存故障而失败，
 *       与网关读侧 fail-open 对称）。</li>
 * </ul>
 */
public interface TokenVersionService {

    /**
     * 读取用户的当前令牌版本。
     *
     * @param userId 用户技术主键
     * @return 当前版本；键缺失或值不可解析时按 {@code 0} 处理（与"键缺失 = 版本 0"的兼容规则一致）
     */
    long currentVersion(Long userId);

    /**
     * 推进用户的令牌版本（{@code INCR}），使其已签发的全部 access token 立即失效。
     *
     * @param userId 用户技术主键
     * @return 推进后的新版本；存储异常时 {@code log.warn} 后返回 {@link #INCREMENT_FAILED}（fail-open，不抛出）
     */
    long incrementVersion(Long userId);

    /** {@link #incrementVersion(Long)} 的失败哨兵：真实版本从 {@code 1} 起，永不为负。 */
    long INCREMENT_FAILED = -1L;
}

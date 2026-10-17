package org.icedamericanomall.auth.domain.ability.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.LoginFailureReason;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
import org.icedamericanomall.auth.domain.port.PasswordVerifier;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;

import java.util.Optional;

/**
 * 密码登录策略（领域能力）。
 *
 * <p>支持 USERNAME / PHONE / EMAIL 三种身份类型 + 密码凭证：
 * <ul>
 *   <li>PHONE / EMAIL：敏感标识，走盲索引（HMAC）查询</li>
 *   <li>USERNAME：非敏感标识，走明文查询</li>
 * </ul>
 * 密码哈希比较通过 {@link PasswordVerifier} 端口注入，领域层不依赖 Spring Security。
 *
 * <h3>登录失败口径（2026-10-18 批次 ④，抗账号枚举）——本类的核心不变量</h3>
 *
 * <p>「账号不存在」「密码错误」「账号状态不可登录」三类失败，对外<b>必须完全一致</b>：
 * 同一 HTTP 状态码（{@code 401}）、同一业务码（{@link ErrorCode#PASSWORD_ERROR}，3007）、
 * 同一句中性文案（{@code 账号或密码不正确}）。此前三者分别是
 * {@code USER_NOT_FOUND(1102)/404}、{@code PASSWORD_ERROR(3007)/200}、{@code USER_STATUS_ABNORMAL(3008)/200}
 * —— 攻击者<b>只看状态码</b>就能枚举「某账号是否存在 / 是否被冻结」，甚至不用猜密码。
 * 原因细分（{@link LoginFailureReason}）只进服务端 WARN 日志。
 *
 * <h3>dummy verify（时序防护）—— 为什么必须有、以及它为什么不是安全强度</h3>
 *
 * <p>BCrypt 校验一次约几十毫秒（cost=10），是登录请求里最重的一段 CPU。若「账号不存在」直接
 * 抛出而不做哈希比对，这类响应会比「账号存在 + 密码错」<b>快一个数量级</b>：攻击者即使拿不到
 * 状态码与文案差异，也能用<b>响应时间</b>把账号存在性读出来（经典 timing oracle）。
 * 因此账号不存在（或该凭证行从未设过密码）时，也必须用<b>预置的合法 BCrypt 哈希</b>
 * （{@link #DUMMY_PASSWORD_HASH}）对提交的密码做一次真实的 {@code matches}，
 * <b>再丢弃结果</b>。
 *
 * <p><b>它不提供任何安全强度</b>：比对的一方是公开的常量哈希，提交什么都不会「通过」，
 * 它<b>不增加</b>抗暴力破解能力、<b>不改变</b>成功/失败判定、也<b>不参与</b>任何业务分支。
 * 它唯一的作用是<b>消除可观测的响应时间差</b>，让两条失败路径的耗时落在同一量级。
 * 把这句话写在这里，是为了防止后来者误以为「多了一次哈希 = 更安全」而据此放宽别处的防护
 * （例如以为不必再限流）。
 *
 * <p><b>不得提前 return</b>：任何「查到/查不到就 return」的写法都会重新打开时间差，
 * 故本类把「定位 → 比对 → 状态」写成一条顺序流，失败出口只有一个。
 *
 * <p>风格先例：{@code ADR-051} 的注册前置快拒绝（先做一次本地判断再决定要不要走重流程），
 * 同样把「能省的成本」与「不能省的一致性」分清楚。
 */
@Slf4j
@RequiredArgsConstructor
public class PasswordLoginStrategy implements LoginStrategy {

    /**
     * dummy verify 使用的<b>预置合法 BCrypt 哈希</b>（cost=10）。
     *
     * <p>来源：对一个随机生成、从未使用、也不存在于任何库表中的口令取 BCrypt 哈希
     * （因此「猜中它」没有意义），仅作「可被 {@code BCryptPasswordEncoder} 正常解析并完整计算」
     * 的占位符 —— 若换成非法形态的占位串，Spring Security 会<b>跳过</b>整个计算只打一条警告，
     * 时间差就会原地复发。
     *
     * <p>可见性为 {@code public}：它不是秘密（对应口令已被丢弃、不在任何库表中），
     * 暴露它不降低任何安全性；公开的唯一目的是让测试（含跨包集成测试）能断言
     * 「确实用这个哈希做了假比对」。
     */
    public static final String DUMMY_PASSWORD_HASH =
            "$2a$10$i.kci6HU0Hxhz37d0x8UOOLxxqURG3zLIaoHzXwzwvgE7HEsVuW6G";

    /** 提交密码为空时的替身（BCrypt 不接受 null；空串仍会走完整的哈希计算） */
    private static final String EMPTY_CREDENTIAL = "";

    private final UserAuthRepository userAuthRepository;
    private final PasswordVerifier passwordVerifier;

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return credentialType == CredentialTypeEnum.PASSWORD
                && (identityType == IdentityTypeEnum.USERNAME
                || identityType == IdentityTypeEnum.PHONE
                || identityType == IdentityTypeEnum.EMAIL);
    }

    /**
     * 密码登录。
     *
     * <p>顺序固定为「定位凭证 → 密码比对（含 dummy） → 状态判定 → 唯一失败出口」，
     * 三层失败<b>不改变</b>对外报文。
     *
     * @param spec 登录尝试（身份类型 + 标识 + 明文密码）
     * @return 可登录的认证凭证聚合
     * @throws BizException 任一失败原因（统一 {@link ErrorCode#PASSWORD_ERROR} / HTTP 401 / 中性文案）
     */
    @Override
    public UserAuth login(LoginSpec spec) {
        // 1. 定位凭证：查不到不 return —— 继续往下走一次 dummy 哈希比对，消除时间差
        Optional<UserAuth> found = findUserAuth(spec);
        UserAuth userAuth = found.orElse(null);

        // 2. 密码比对（账号不存在 / 凭证无密码时用预置哈希做假比对，结果丢弃）
        String submitted = spec.credential() == null ? EMPTY_CREDENTIAL : spec.credential();
        if (!verifyPassword(userAuth, submitted)) {
            throw loginFailure(userAuth == null
                    ? LoginFailureReason.ACCOUNT_NOT_FOUND
                    : LoginFailureReason.PASSWORD_MISMATCH, spec);
        }

        // 3. 状态判定：密码正确但账号不可登录 —— 对外与密码错完全一致（不得按"被冻结"区分）
        if (!userAuth.isActive()) {
            throw loginFailure(LoginFailureReason.STATUS_NOT_ACTIVE, spec);
        }

        return userAuth;
    }

    /**
     * 密码比对 —— 无可用哈希时执行 dummy verify。
     *
     * @param userAuth  已定位到的凭证（{@code null} 表示账号不存在）
     * @param submitted 提交的明文密码（非 null）
     * @return true 仅当真实哈希比对通过；dummy 路径恒为 false
     */
    private boolean verifyPassword(UserAuth userAuth, String submitted) {
        if (userAuth == null || !userAuth.hasPasswordCredential()) {
            // dummy verify：真实计算一次 BCrypt，结果丢弃。不这么做 = 免费提供「账号是否存在」的计时探针。
            passwordVerifier.matches(submitted, DUMMY_PASSWORD_HASH);
            return false;
        }
        return userAuth.verifyPassword(submitted, passwordVerifier);
    }

    /**
     * 登录失败的唯一出口：统一业务码 + 中性文案，原因只进日志。
     *
     * @param reason 内部原因（不得进入响应）
     * @param spec   登录尝试（日志用，标识已脱敏）
     * @return 待抛出的业务异常
     */
    private BizException loginFailure(LoginFailureReason reason, LoginSpec spec) {
        // 日志保留细分原因以便排障；状态码/业务码/文案一律中性（不传自定义文案，
        // 让 ErrorCode.PASSWORD_ERROR 的中性 message 成为唯一文案来源）
        log.warn("[login][password][fail] reason={}({}), identityType={}, identifier={}, platform={}",
                reason.name(), reason.getDesc(), spec.identityType(),
                DesensitizeUtil.mask(spec.identifier()), spec.platform());
        return new BizException(ErrorCode.PASSWORD_ERROR);
    }

    /**
     * 根据 identityType 决定查询方式。
     * <ul>
     *   <li>PHONE / EMAIL：走盲索引查询</li>
     *   <li>USERNAME：走明文字段查询</li>
     * </ul>
     *
     * @param spec 登录尝试
     * @return 命中的凭证；<b>查无此人返回空 Optional 而非抛异常</b>（失败口径由上层的唯一出口决定）
     * @throws BizException 身份类型不受本策略支持（参数问题，与「账号是否存在」无关）
     */
    private Optional<UserAuth> findUserAuth(LoginSpec spec) {
        IdentityTypeEnum type = spec.identityType();
        return switch (type) {
            case PHONE, EMAIL -> userAuthRepository.findByIdentityHash(
                    type, BlindIndexUtil.hmac(spec.identifier()));
            case USERNAME -> userAuthRepository.findByIdentity(type, spec.identifier());
            default -> throw new BizException(
                    ErrorCode.PARAM_ERROR, "不支持的登录身份类型: " + type);
        };
    }
}

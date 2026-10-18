package org.icedamericanomall.user.app.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.dto.TokenRevokeCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.app.oplog.OperationAuditConstants;
import org.icedamericanomall.user.app.oplog.OperationLogManager;
import org.icedamericanomall.user.app.rbac.RoleTypeDerivation;
import org.icedamericanomall.user.domain.enums.UserStatusEnum;
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台用户运营（应用层编排）—— 用户分页列表（角色派生）、启用/禁用、角色设置。
 *
 * <p>依赖倒置：只依赖领域仓储，不出现 Mapper / PO；分页响应由应用层组装
 * MyBatis-Plus 分页信封，属既有契约形状。
 *
 * <p>V5.2（ADR-017）：{@code roleType} 不再读写 {@code user.role_type} 列，
 * 一律由 RBAC 五表角色编码派生（{@link RoleTypeDerivation}）。
 * 角色设置经 {@link AuthContextClient} 委托 authorization-service（ADR-018），
 * Feign 调用收敛在本 Manager，Controller 不直接持有客户端。
 *
 * <p><b>审计（ADR-032 管道二）</b>：本类的写操作在成功路径上显式调用
 * {@link OperationLogManager#record(Long, String, String, String)} 落 {@code operation_log}
 * 台账（管理端可查询），摘要只含动作与目标用户标识，不含敏感值。
 *
 * <p><b>禁用即下线（ADR-031）</b>：状态置为 {@link UserStatusEnum#FROZEN} 时，除写本地状态外
 * 还需调用 authorization-service 的用户级吊销（令牌版本 INCR + 拆除全部 refresh 会话）。
 * 该 Feign 调用<b>必须在事务之外</b>（硬约束：事务内调远端），因此本地「状态变更 + 审计留痕」
 * 用 {@link TransactionTemplate} 显式圈定（保住 ADR-032 的"改了状态必留痕"），
 * 提交后再发起吊销；吊销失败走 Feign 降级 fail-open（告警但不阻断禁用主流程）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAdminManager {

    /** 吊销终结理由（写进 {@code detail}，仅供人读；对账只按 action + target 判定）。 */
    private static final String REVOKE_OK = "ok";
    private static final String REVOKE_PERMANENT = "permanent-4xx";


    private final UserRepository userRepository;
    private final AuthContextClient authContextClient;
    private final OperationLogManager operationLogManager;
    private final TransactionTemplate transactionTemplate;

    /** 管理后台：按注册时间倒序分页查询用户列表（roleType 由五表角色批量派生）。 */
    public IPage<UserInfoResp> pageUsers(int pageNum, int size) {
        long total = userRepository.countAll();
        Page<UserInfoResp> page = new Page<>(pageNum, size);
        page.setTotal(total);
        if (total == 0) {
            return page;
        }
        List<User> users = userRepository.findPageDescByCreateTime((pageNum - 1) * size, size);

        // 角色批量派生（一次远端查询避免逐行 N+1；RBAC 归 authorization，ADR-018）
        Map<Long, Set<String>> rolesByUserId = authContextClient.rolesByUserIds(
                users.stream().map(User::getId).collect(Collectors.toSet()));

        page.setRecords(users.stream()
                .map(user -> {
                    UserInfoResp resp = UserInfoResp.from(user);
                    resp.setRoleType(RoleTypeDerivation.fromRoleCodes(rolesByUserId.get(user.getId())));
                    return resp;
                })
                .toList());
        page.setPages((long) Math.ceil((double) total / size));
        return page;
    }

    /**
     * 管理后台：启用/禁用用户（status 见 {@link UserStatusEnum}；<b>非法值抛
     * {@code PARAM_ERROR(1101)} / 400</b> —— 参数非法，不是鉴权失败，见
     * {@link UserStatusEnum#of(int)} 的语义订正说明）。
     *
     * <p>本地「状态变更 + 审计留痕」在同一个 {@link TransactionTemplate} 事务内提交
     * （ADR-032 管道二：不允许「改了状态却没留痕」）；随后的用户级吊销是 Feign 调用，
     * 刻意置于事务提交<b>之后</b>，避免"事务内调远端"占用数据库连接且无法回滚。
     *
     * <p>置为 {@link UserStatusEnum#FROZEN}（禁用/冻结）时，调用 authorization-service
     * 用户级吊销（ADR-031）：推进 {@code auth:tokenver:{userId}} + 拆除该用户全部 refresh 会话，
     * 使被禁用账号的已签发 access token 立即失效。启用（{@link UserStatusEnum#NORMAL}）不触发吊销。
     * 吊销失败<b>不阻断</b>禁用主流程：降级工厂已改为抛，调用点在此处接住并按
     * 4xx（登记终结）/ 不可用（留待对账）分流，详见 {@link #revokeTokensAfterDisable(Long)}。
     *
     * @param id     目标用户技术主键
     * @param status 目标状态码（0=禁用，1=正常）
     * @throws org.icedamericanomall.common.exception.BadRequestException status 非法
     */
    public void updateStatus(Long id, Integer status) {
        UserStatusEnum targetStatus = UserStatusEnum.of(status);

        transactionTemplate.executeWithoutResult(tx -> {
            userRepository.updateStatusById(id, targetStatus);
            // 审计与状态变更**同库同事务**：user-service 单数据源，record 的 @Transactional(REQUIRED)
            // 会加入本事务 ⇒「有禁用审计」与「状态已是 FROZEN」原子成立，对账候选集不会有幻影。
            // target 存纯业务键（"7"），禁用走独立 action（对账只按 action 判定，禁止解析 detail）。
            operationLogManager.record(UserContext.getUserId(), OperationAuditConstants.MODULE_USER,
                    targetStatus == UserStatusEnum.FROZEN
                            ? OperationAuditConstants.ACTION_DISABLE_USER
                            : OperationAuditConstants.ACTION_UPDATE_USER_STATUS,
                    String.valueOf(id),
                    "targetUserId=" + id + ", status=" + targetStatus.name());
        });

        if (targetStatus == UserStatusEnum.FROZEN) {
            revokeTokensAfterDisable(id);
        }
    }

    /**
     * 禁用<b>提交之后</b>吊销该用户全部令牌（ADR-031）。
     *
     * <p><b>为什么在这里吞掉异常</b>：本地状态已提交，异常外溢会让一次<b>已生效</b>的禁用对外报 5xx。
     * 但「吞」不等于「静默」—— 两类失败留不同轨迹：
     * <ul>
     *   <li><b>4xx（CLIENT，确定性失败）</b>：重试必然重复失败 ⇒ 写<b>终结记录</b>，<b>永不进对账</b>；
     *       否则对账会无限重放同一次必然失败的调用（死循环）。</li>
     *   <li><b>不可用（TRANSIENT / BUG）</b>：<b>不写</b>终结记录 ⇒ 对账 Job 重放；
     *       超出时间窗仍未成功则转人工（{@code TokenRevokeReconcileManager}）。</li>
     * </ul>
     * 记录写入本身失败也吞掉（只打 ERROR）：DB 抖动不得让一次已生效的禁用失败。
     */
    private void revokeTokensAfterDisable(Long id) {
        try {
            authContextClient.revokeUserTokens(TokenRevokeCO.of(id));
            recordRevokeOutcome(id, REVOKE_OK);
            log.info("[admin][user-disable] 已触发用户级令牌吊销: userId={}", id);
        } catch (Exception e) {
            boolean deterministic = isDeterministicFailure(e);
            log.error("[admin][user-disable] 令牌吊销失败（{}）: userId={}",
                    deterministic ? "4xx 确定性失败，登记终结、不再重试" : "不可用，留待对账重放", id, e);
            if (deterministic) {
                recordRevokeOutcome(id, REVOKE_PERMANENT);
            }
        }
    }

    /**
     * 写吊销<b>终结</b>记录（ok / permanent-4xx）。
     *
     * <p><b>为什么这里也吞掉异常（权衡过的，不是偷懒）</b>：写失败<b>不会</b>让状态变错 ——
     * 缺一条终结记录的唯一后果是「对账 Job 会再重放一次 {@code revokeUserTokens}」，
     * 而该操作<b>幂等</b>（单调推进令牌版本号），重复调用无副作用，只多一次 RPC。
     * 反过来，若让写库异常外溢，就会把一次<b>已经提交成功</b>的禁用变成对外 5xx。
     * 用「一次冗余 RPC」换「一个假失败」，这个交换是划算的。
     *
     * <p><b>⚠ 别把这段当 bug 修掉</b>：去掉 {@code catch}「让异常正常抛」会让链路变成
     * 「禁用成功 → 记录失败 → 接口报错 → 管理员重试禁用 → 又一次记录失败」，对账候选集反而更脏。
     * 该做的是让记录<b>更能写成功</b>（索引/容量），不是把容错删掉。
     */
    private void recordRevokeOutcome(Long id, String outcome) {
        try {
            operationLogManager.record(UserContext.getUserId(), OperationAuditConstants.MODULE_USER,
                    OperationAuditConstants.ACTION_REVOKE_USER_TOKENS, String.valueOf(id), outcome);
        } catch (Exception e) {
            log.error("[admin][user-disable] 吊销终结记录写入失败（不影响禁用本身，但对账将重复尝试）: "
                    + "userId={}, outcome={}", id, outcome, e);
        }
    }

    /** 4xx（CLIENT）属确定性失败：重试必然重复失败 ⇒ 必须登记终结，不能进对账。 */
    private static boolean isDeterministicFailure(Throwable e) {
        UpstreamException upstream = UpstreamException.findIn(e);
        return upstream != null && upstream.getCategory() == ErrorCategory.CLIENT;
    }

    /**
     * 管理后台：修改用户角色（ADR-018 委托 authorization-service）。
     *
     * <p>只写 RBAC 五表（{@code sys_user_role} 为唯一权威口径）；远端在变更后主动失效
     * 该用户的权限缓存。
     *
     * <p>本方法<b>不加事务</b>：方法内含 Feign 调用，事务内调远端属硬约束禁止项。
     * 因此审计落库自成独立事务 —— 角色变更成功后审计写入若失败，不回滚已完成的中台变更
     * （残余风险见 ADR-032）。
     *
     * @param id       目标用户技术主键
     * @param roleType 目标角色类型码
     */
    public void setUserRole(Long id, Integer roleType) {
        authContextClient.setUserRole(id, roleType);
        operationLogManager.record(UserContext.getUserId(), OperationAuditConstants.MODULE_USER,
                OperationAuditConstants.ACTION_SET_USER_ROLE, String.valueOf(id),
                "targetUserId=" + id + ", roleType=" + roleType);
    }
}

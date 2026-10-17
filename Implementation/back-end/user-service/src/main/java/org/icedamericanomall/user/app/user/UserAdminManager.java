package org.icedamericanomall.user.app.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.dto.TokenRevokeCO;
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
     * 吊销失败由 Feign 降级 fail-open 兜底，不阻断禁用主流程（仅告警）。
     *
     * @param id     目标用户技术主键
     * @param status 目标状态码（0=禁用，1=正常）
     * @throws org.icedamericanomall.common.exception.BadRequestException status 非法
     */
    public void updateStatus(Long id, Integer status) {
        UserStatusEnum targetStatus = UserStatusEnum.of(status);

        transactionTemplate.executeWithoutResult(tx -> {
            userRepository.updateStatusById(id, targetStatus);
            operationLogManager.record(UserContext.getUserId(), OperationAuditConstants.MODULE_USER,
                    OperationAuditConstants.ACTION_UPDATE_USER_STATUS,
                    "targetUserId=" + id + ", status=" + targetStatus.name());
        });

        if (targetStatus == UserStatusEnum.FROZEN) {
            authContextClient.revokeUserTokens(TokenRevokeCO.of(id));
            log.info("[admin][user-disable] 已触发用户级令牌吊销: userId={}", id);
        }
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
                OperationAuditConstants.ACTION_SET_USER_ROLE,
                "targetUserId=" + id + ", roleType=" + roleType);
    }
}

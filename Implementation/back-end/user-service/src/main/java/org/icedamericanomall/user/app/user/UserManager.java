package org.icedamericanomall.user.app.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.UserAuthCO;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.app.rbac.RoleTypeDerivation;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.model.UserStatusEnum;
import org.icedamericanomall.user.domain.repository.RoleRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.icedamericanomall.user.domain.service.UserProvisioner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户查询与账户管理（应用层编排）—— 用户资料读取、管理后台用户/余额操作、
 * 跨服务幂等建档（ADR-006 {@code /internal/user/getOrCreate}）。
 *
 * <p>依赖倒置：只依赖领域仓储 / 领域服务，不出现 Mapper / DO / 框架类型
 * （分页响应由应用层组装 MyBatis-Plus 分页信封，属既有契约形状）。
 *
 * <p>V5.2（ADR-017）：{@code roleType} 不再读写 {@code user.role_type} 列，
 * 一律由 RBAC 五表角色编码派生（{@link RoleTypeDerivation}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserManager {

    private final UserRepository userRepository;
    private final UserProvisioner userProvisioner;
    private final RoleRepository roleRepository;

    /**
     * 查询用户信息（前台 /api/user/info 与内部端点共用）。
     *
     * @throws BizException 用户不存在（USER_NOT_FOUND）/ 已冻结（USER_STATUS_ABNORMAL）
     */
    public UserInfoResp getByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        user.assertActive();
        return UserInfoResp.from(user);
    }

    /** 用户总数（内部统计端点）。 */
    public long countUsers() {
        return userRepository.countAll();
    }

    /** 管理后台：按注册时间倒序分页查询用户列表（roleType 由五表角色批量派生）。 */
    public IPage<UserInfoResp> pageUsers(int pageNum, int size) {
        long total = userRepository.countAll();
        Page<UserInfoResp> page = new Page<>(pageNum, size);
        page.setTotal(total);
        if (total == 0) {
            return page;
        }
        List<User> users = userRepository.findPageDescByCreateTime((pageNum - 1) * size, size);

        // 角色批量派生（一次查询，避免逐行 N+1）
        Map<Long, Set<String>> rolesByUserId = roleRepository.findRoleCodesByUserIds(
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

    /** 管理后台：启用/禁用用户（status 见 UserStatusEnum；非法值抛 USER_STATUS_ABNORMAL）。 */
    public void updateStatus(Long id, Integer status) {
        userRepository.updateStatusById(id, UserStatusEnum.of(status));
    }

    /**
     * 查询账户余额（单位：分）。
     *
     * @throws BizException 用户不存在
     */
    public int getBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        return user.getBalance() == null ? 0 : user.getBalance();
    }

    /**
     * 原子扣减余额（余额支付）；余额不足抛 BALANCE_INSUFFICIENT。
     *
     * <p>单聚合单语句（条件 UPDATE），不声明事务 —— 原子性由 SQL 条件承担（ADR-005）。
     */
    public void deductBalance(Long userId, int amount) {
        if (amount <= 0) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "扣减金额必须大于0");
        }
        boolean ok = userRepository.deductBalance(userId, amount) > 0;
        if (!ok) {
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "余额不足");
        }
    }

    /** 增加余额（充值/退款）；用户不存在抛 USER_NOT_FOUND。 */
    public void addBalance(Long userId, int amount) {
        if (amount <= 0) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "金额必须大于0");
        }
        boolean ok = userRepository.creditBalance(userId, amount) > 0;
        if (!ok) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
    }

    /**
     * 按登录标识查找或创建用户（ADR-006 幂等端点，authorization-service 注册/登录后回查技术主键）。
     *
     * <p>幂等性由领域服务 {@link UserProvisioner} 保证（先查短路 + 业务键互斥锁 + 唯一键冲突回查）；
     * 新建分支在同一漏斗内完成：默认昵称（可被 {@code nickname} 覆盖）→ user 行 + 默认角色
     * （本地事务）→ 新用户建档奖励（旁路，失败不阻断）。
     *
     * <p>幂等口径（V5.2）：PHONE / EMAIL 按盲索引（phone_hash / email_hash）短路；
     * WECHAT 类标识的 user 表无定位列，幂等由调用方凭证唯一键（user_auth）保证（ADR-017）。
     *
     * @return 用户技术主键（跨服务 user_id 口径）
     */
    public Long getOrCreateUser(UserAuthCO userAuthDTO) {
        User user = userProvisioner.findOrCreate(
                userAuthDTO.getIdentityType(), userAuthDTO.getIdentifier(), userAuthDTO.getNickname());
        return user.getId();
    }
}

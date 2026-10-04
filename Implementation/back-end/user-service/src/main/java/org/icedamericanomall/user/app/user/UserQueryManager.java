package org.icedamericanomall.user.app.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * 用户信息查询（应用层）—— 单用户读取（前台 {@code /api/user/info} 与内部端点共用）
 * 与用户总数统计（内部统计端点）。
 *
 * <p>依赖倒置：只依赖领域仓储，不出现 Mapper / PO / 框架类型。
 *
 * <p>拆分说明（ADR-025）：原 {@code UserManager} 混装了"用户查询 / 管理端运营 / 跨服务幂等开通"
 * 三类用例族，现按用例族拆为 {@code UserQueryManager}（本类）、{@code UserAdminManager}、
 * {@code UserProvisionManager}；HTTP 契约与行为不变。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserQueryManager {

    private final UserRepository userRepository;

    /**
     * 查询用户信息。
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
}

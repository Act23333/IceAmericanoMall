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
     * <p>冻结用户在此<b>被拒绝</b>（{@code User#assertActive}）：用户已认证、只是无权 →
     * HTTP <b>403 {@code FORBIDDEN}</b>（{@code 09-constraints} §三·六）。批次 ⑤b 之前该路径返回
     * 401（{@code USER_STATUS_ABNORMAL}），前端据此清凭证跳登录页 —— 冻结用户「登录成功 →
     * 立刻被登出」，既进不去也退不出登录页。
     *
     * @param userId 用户技术主键
     * @return 用户信息
     * @throws BizException 用户不存在（{@code USER_NOT_FOUND} / 404）
     * @throws org.icedamericanomall.common.exception.ForbiddenException 账号已冻结（403）
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

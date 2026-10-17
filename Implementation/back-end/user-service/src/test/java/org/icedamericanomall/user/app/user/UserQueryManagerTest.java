package org.icedamericanomall.user.app.user;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.domain.enums.UserStatusEnum;
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link UserQueryManager} —— {@code GET /api/user/info} 的应用层口径（含 批次 ⑤b 的 403 订正）。
 *
 * <p>本测试盯的是<b>外部可观测行为</b>：同一个端点对「不存在」「已冻结」「正常」三种用户
 * 分别应当回什么状态码。批次 ⑤b 之前，冻结用户走的是 401（{@code USER_STATUS_ABNORMAL}），
 * 前端据此<b>清凭证并跳登录页</b> —— 于是冻结用户「登录成功 → 立刻被登出」，永远退不出登录页。
 * 按 {@code collaboration/09-constraints.md} §三·六：<b>未认证 → 401，已认证但无权 → 403</b>。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryManager — 用户信息查询（404 / 403 / 200）")
class UserQueryManagerTest {

    private static final Long USER_ID = 1001L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserQueryManager userQueryManager;

    @BeforeAll
    static void initCrypto() {
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    /**
     * ★ 批次 ⑤b 的验收点：冻结用户 → <b>403</b>（不是 401）。
     *
     * <p>若有人把这条改回 401 / {@code USER_STATUS_ABNORMAL}，前端会再次「清凭证 + 跳登录页」，
     * 冻结用户陷入登录死循环 —— 本断言同时锁死业务码与 HTTP 状态码两个维度。
     */
    @Test
    @DisplayName("冻结用户 → 403 FORBIDDEN（已认证但无权；不得回 401 让前端清凭证）")
    void shouldRejectFrozenUser_with403() {
        User frozen = User.create(IdentityTypeEnum.PHONE, "13800138000");
        frozen.disable();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(frozen));

        assertThatThrownBy(() -> userQueryManager.getByUserId(USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .isNotInstanceOf(BizException.class) // 不是「业务规则拒绝」那一路
                .satisfies(thrown -> {
                    ForbiddenException forbidden = (ForbiddenException) thrown;
                    assertThat(forbidden.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(forbidden.getErrorCode().getHttpStatus())
                            .as("冻结用户已认证，只是无权 → 403")
                            .isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(forbidden.getErrorCode())
                            .as("不得复用 401 语义的 3008")
                            .isNotEqualTo(ErrorCode.USER_STATUS_ABNORMAL);
                });
    }

    @Test
    @DisplayName("用户不存在 → 404 USER_NOT_FOUND（与「冻结」可区分，因为都是已认证的管理面事实）")
    void shouldRejectMissingUser_with404() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userQueryManager.getByUserId(USER_ID))
                .isInstanceOf(BizException.class)
                .satisfies(thrown -> assertThat(((BizException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("正常用户 → 返回 VO（PII 为掩码，状态码原样透出）")
    void shouldReturnProfile_whenUserActive() {
        User active = User.create(IdentityTypeEnum.PHONE, "13800138000");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(active));

        UserInfoResp resp = userQueryManager.getByUserId(USER_ID);

        assertThat(resp.getUserId()).isEqualTo(active.getUserId());
        assertThat(resp.getStatus()).isEqualTo(UserStatusEnum.NORMAL.getCode());
        assertThat(resp.getPhone()).as("手机号只出掩码").contains("****");
    }
}

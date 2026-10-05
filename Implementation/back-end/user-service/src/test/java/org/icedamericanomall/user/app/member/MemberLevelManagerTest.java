package org.icedamericanomall.user.app.member;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.MemberGrowthCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.user.domain.enums.MemberLevelEnum;
import org.icedamericanomall.user.domain.model.member.MemberGrowthLog;
import org.icedamericanomall.user.domain.model.member.MemberLevelRule;
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.user.domain.repository.MemberGrowthLogRepository;
import org.icedamericanomall.user.domain.repository.MemberLevelRuleRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 会员等级 Manager 单元测试（I45 权益查询 / I46 成长值入账幂等三态 —— ADR-044）。
 */
@DisplayName("会员等级 Manager — 权益查询与成长值入账")
@ExtendWith(MockitoExtension.class)
class MemberLevelManagerTest {

    private static final Long USER_ID = 5L;
    private static final String ORDER_NO = "O20261004001";

    @Mock
    private UserRepository userRepository;

    @Mock
    private MemberLevelRuleRepository memberLevelRuleRepository;

    @Mock
    private MemberGrowthLogRepository memberGrowthLogRepository;

    @InjectMocks
    private MemberLevelManager memberLevelManager;

    // ==================== I45 ====================

    @Test
    @DisplayName("I45 — 规则命中时返回等级与折扣率/积分倍率")
    void shouldReturnLevelBenefits_whenRuleExists() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(MemberLevelEnum.GOLD)));
        when(memberLevelRuleRepository.findByLevel(MemberLevelEnum.GOLD))
                .thenReturn(Optional.of(new MemberLevelRule(MemberLevelEnum.GOLD, 500000, 9800, 120, 2)));

        MemberLevelCO result = memberLevelManager.getLevel(USER_ID);

        assertEquals("GOLD", result.getLevel());
        assertEquals(9800, result.getDiscountRate());
        assertEquals(120, result.getPointsRate());
    }

    @Test
    @DisplayName("I45 — 规则缺行按枚举默认兜底（不折扣/1倍），等级如实返回")
    void shouldReturnDefaultBenefits_whenRuleRowMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(MemberLevelEnum.PLATINUM)));
        when(memberLevelRuleRepository.findByLevel(MemberLevelEnum.PLATINUM)).thenReturn(Optional.empty());

        MemberLevelCO result = memberLevelManager.getLevel(USER_ID);

        assertEquals("PLATINUM", result.getLevel());
        assertEquals(MemberLevelManager.DEFAULT_DISCOUNT_RATE, result.getDiscountRate());
        assertEquals(MemberLevelManager.DEFAULT_POINTS_RATE, result.getPointsRate());
    }

    @Test
    @DisplayName("I45 — 用户不存在抛 USER_NOT_FOUND")
    void shouldThrowUserNotFound_whenUserMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> memberLevelManager.getLevel(USER_ID));

        assertEquals(ErrorCode.USER_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("I45 — userId 为空抛 PARAM_ERROR，不触达仓储")
    void shouldThrowParamError_whenUserIdNull() {
        BizException e = assertThrows(BizException.class, () -> memberLevelManager.getLevel(null));

        assertEquals(ErrorCode.PARAM_ERROR, e.getErrorCode());
        verifyNoInteractions(userRepository);
    }

    // ==================== I46 首次入账 ====================

    @Test
    @DisplayName("I46 首次入账 — 插流水 + 等级升级（NORMAL→SILVER）+ 返回新等级权益")
    void shouldInsertLogAndUpgradeLevel_whenFirstGrowth() {
        mockFirstEntryFlow(user(MemberLevelEnum.NORMAL), 50000L);
        when(memberLevelRuleRepository.findByLevel(MemberLevelEnum.SILVER))
                .thenReturn(Optional.of(new MemberLevelRule(MemberLevelEnum.SILVER, 100000, 9900, 110, 1)));

        MemberLevelCO result = memberLevelManager.recordGrowth(growth(50000));

        assertEquals("SILVER", result.getLevel());
        assertEquals(9900, result.getDiscountRate());
        assertEquals(110, result.getPointsRate());
        ArgumentCaptor<MemberGrowthLog> logCaptor = ArgumentCaptor.forClass(MemberGrowthLog.class);
        verify(memberGrowthLogRepository).insert(logCaptor.capture());
        MemberGrowthLog inserted = logCaptor.getValue();
        assertEquals(USER_ID, inserted.getUserId());
        assertEquals(ORDER_NO, inserted.getOrderNo());
        assertEquals(50000, inserted.getGrowthAmount());
        assertEquals(MemberLevelEnum.SILVER, inserted.getAfterLevel());
        verify(userRepository).updateMemberLevelById(USER_ID, MemberLevelEnum.SILVER);
    }

    @Test
    @DisplayName("I46 门槛匹配低于当前等级 — 只升不降保持 GOLD，不更新等级列")
    void shouldKeepHigherLevel_whenMatchedLowerThanCurrent() {
        mockFirstEntryFlow(user(MemberLevelEnum.GOLD), 200000L);
        when(memberLevelRuleRepository.findByLevel(MemberLevelEnum.GOLD))
                .thenReturn(Optional.of(new MemberLevelRule(MemberLevelEnum.GOLD, 500000, 9800, 120, 2)));

        MemberLevelCO result = memberLevelManager.recordGrowth(growth(50000));

        assertEquals("GOLD", result.getLevel());
        ArgumentCaptor<MemberGrowthLog> logCaptor = ArgumentCaptor.forClass(MemberGrowthLog.class);
        verify(memberGrowthLogRepository).insert(logCaptor.capture());
        assertEquals(MemberLevelEnum.GOLD, logCaptor.getValue().getAfterLevel());
        verify(userRepository, never()).updateMemberLevelById(any(), any());
    }

    // ==================== I46 幂等 ====================

    @Test
    @DisplayName("I46 顺序重放 — 同 orderNo 已有流水，返回现有权益且不重复入账不重复迁级")
    void shouldReturnExistingBenefits_whenOrderNoReplayed() {
        MemberGrowthLog existing = MemberGrowthLog.builder()
                .userId(USER_ID).orderNo(ORDER_NO).growthAmount(100000)
                .afterLevel(MemberLevelEnum.SILVER).build();
        when(memberGrowthLogRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(existing));
        when(memberLevelRuleRepository.findByLevel(MemberLevelEnum.SILVER))
                .thenReturn(Optional.of(new MemberLevelRule(MemberLevelEnum.SILVER, 100000, 9900, 110, 1)));

        MemberLevelCO result = memberLevelManager.recordGrowth(growth(50000));

        assertEquals("SILVER", result.getLevel());
        assertEquals(9900, result.getDiscountRate());
        verify(memberGrowthLogRepository, never()).insert(any());
        verify(userRepository, never()).updateMemberLevelById(any(), any());
    }

    @Test
    @DisplayName("I46 并发撞 uk_order_no — 捕获 DuplicateKeyException 回落为查现有流水返回")
    void shouldFallBackToExistingLog_whenConcurrentDuplicateKey() {
        MemberGrowthLog winner = MemberGrowthLog.builder()
                .userId(USER_ID).orderNo(ORDER_NO).growthAmount(50000)
                .afterLevel(MemberLevelEnum.GOLD).build();
        when(memberGrowthLogRepository.findByOrderNo(ORDER_NO))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(MemberLevelEnum.NORMAL)));
        when(memberGrowthLogRepository.sumGrowthByUserId(USER_ID)).thenReturn(0L);
        when(memberLevelRuleRepository.findAll()).thenReturn(seedRules());
        when(memberLevelRuleRepository.findByLevel(MemberLevelEnum.GOLD))
                .thenReturn(Optional.of(new MemberLevelRule(MemberLevelEnum.GOLD, 500000, 9800, 120, 2)));
        doThrow(new DuplicateKeyException("uk_order_no")).when(memberGrowthLogRepository)
                .insert(any(MemberGrowthLog.class));

        MemberLevelCO result = memberLevelManager.recordGrowth(growth(50000));

        assertEquals("GOLD", result.getLevel());
        assertEquals(9800, result.getDiscountRate());
        verify(userRepository, never()).updateMemberLevelById(any(), any());
    }

    // ==================== I46 异常分支 ====================

    @Test
    @DisplayName("I46 — 用户不存在抛 USER_NOT_FOUND")
    void shouldThrowUserNotFound_whenGrowthUserMissing() {
        when(memberGrowthLogRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> memberLevelManager.recordGrowth(growth(50000)));

        assertEquals(ErrorCode.USER_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("I46 — payAmount 非正抛 PARAM_ERROR，不触达任何仓储")
    void shouldThrowParamError_whenPayAmountNotPositive() {
        BizException e = assertThrows(BizException.class, () -> memberLevelManager.recordGrowth(growth(0)));

        assertEquals(ErrorCode.PARAM_ERROR, e.getErrorCode());
        verifyNoInteractions(memberGrowthLogRepository, userRepository);
    }

    @Test
    @DisplayName("I46 — orderNo 空白抛 PARAM_ERROR")
    void shouldThrowParamError_whenOrderNoBlank() {
        MemberGrowthCO growth = new MemberGrowthCO(USER_ID, " ", 50000);

        BizException e = assertThrows(BizException.class, () -> memberLevelManager.recordGrowth(growth));

        assertEquals(ErrorCode.PARAM_ERROR, e.getErrorCode());
    }

    // ==================== 测试夹具 ====================

    /** 首次入账链路的公共打桩：无既有流水 + 用户在档 + 存量成长值 + seed 规则集。 */
    private void mockFirstEntryFlow(User user, long existingGrowth) {
        when(memberGrowthLogRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(memberGrowthLogRepository.sumGrowthByUserId(USER_ID)).thenReturn(existingGrowth);
        when(memberLevelRuleRepository.findAll()).thenReturn(seedRules());
    }

    private MemberGrowthCO growth(int payAmount) {
        return new MemberGrowthCO(USER_ID, ORDER_NO, payAmount);
    }

    private User user(MemberLevelEnum level) {
        return User.builder().id(USER_ID).memberLevel(level).build();
    }

    /** V5.8 seed 规则集（threshold 分：NORMAL 0 / SILVER 1000 元 / GOLD 5000 元 / PLATINUM 20000 元）。 */
    private List<MemberLevelRule> seedRules() {
        return List.of(
                new MemberLevelRule(MemberLevelEnum.NORMAL, 0, 10000, 100, 0),
                new MemberLevelRule(MemberLevelEnum.SILVER, 100000, 9900, 110, 1),
                new MemberLevelRule(MemberLevelEnum.GOLD, 500000, 9800, 120, 2),
                new MemberLevelRule(MemberLevelEnum.PLATINUM, 2000000, 9500, 150, 3));
    }
}

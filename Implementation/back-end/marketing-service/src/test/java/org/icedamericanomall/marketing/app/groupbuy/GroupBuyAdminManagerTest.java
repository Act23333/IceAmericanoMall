package org.icedamericanomall.marketing.app.groupbuy;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.request.GroupBuyCreateReq;
import org.icedamericanomall.marketing.adapter.request.GroupBuyUpdateReq;
import org.icedamericanomall.marketing.adapter.response.GroupBuyActivityVO;
import org.icedamericanomall.marketing.domain.enums.GroupBuyActivityStatusEnum;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupBuyActivity;
import org.icedamericanomall.marketing.domain.repository.GroupBuyActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GroupBuyAdminManager 管理端用例单元测试（V5.14 AD54-AD56，ADR-049）。
 *
 * <p>覆盖：创建三态时间窗推导（未来=未开始 / 当前=进行中 / 已过期拒绝——已结束不可能由创建产生）、
 * 字段守卫（拼团价≤0 / 成团人数&lt;2 / 时限&lt;1 → 1101）、时间窗无序 → 1101、
 * null-safe 局部更新 + 状态重推导、已结束禁编、进行中禁删、不存在 → 1103。
 * 装配器用真实 MapStruct 实现（验证 Req→聚合 与 null-safe 合并的真实行为）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GroupBuyAdminManager — AD54-AD56 创建/编辑/删除单元测试")
class GroupBuyAdminManagerTest {
    /** 出线格式的业务键（32-hex）+ 内部技术键（刻意不同值，保证「技术键被误当业务键」必挂）。 */
    private static final String SPU_BUSINESS_ID = "aabbccddeeff00112233445566778899";
    private static final String SKU_BUSINESS_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final Long SPU_PK = 1L;
    private static final Long SKU_PK = 2L;

    /** 读模型端口：技术键（回填）与业务键（创建端解析）两种入参都命中同一行。 */
    private final org.icedamericanomall.marketing.app.port.FlashSaleSpuBriefQuery briefQuery =
            new org.icedamericanomall.marketing.app.port.FlashSaleSpuBriefQuery() {
                @Override
                public java.util.Map<Long, org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief> findBySkuIds(
                        java.util.Collection<Long> skuIds) {
                    return java.util.Map.of(SKU_PK, brief());
                }

                @Override
                public java.util.Map<String, org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief>
                        findBySkuBusinessIds(java.util.Collection<String> skuBusinessIds) {
                    return java.util.Map.of(SKU_BUSINESS_ID, brief());
                }
            };

    private static org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief brief() {
        return new org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief(
                SKU_PK, SPU_PK, SKU_BUSINESS_ID, SPU_BUSINESS_ID, "拼团商品", "https://cdn/main.jpg", 12800);
    }


    private static final Long ACTIVITY_ID = 1L;
    private static final Long GENERATED_ID = 101L;
    private static final Long SELLER_ID = 500L;

    @Mock
    private GroupBuyActivityRepository groupBuyActivityRepository;

    /** 批 7 复核新增：删除活动前要确认没有"拼团中"的团（间接资金影响）。 */
    @Mock
    private org.icedamericanomall.marketing.domain.repository.GroupTeamRepository groupTeamRepository;

    private final MarketingConverter converter = Mappers.getMapper(MarketingConverter.class);

    private GroupBuyAdminManager groupBuyAdminManager;

    @BeforeEach
    void setUp() {
        groupBuyAdminManager = new GroupBuyAdminManager(groupBuyActivityRepository, groupTeamRepository, converter,
                new org.icedamericanomall.marketing.app.groupbuy.GroupBuyViewManager(converter, briefQuery));
        // 仓储 INSERT 主键回填（模拟 MyBatis-Plus 生成主键后写回聚合）
        lenient().doAnswer(invocation -> {
            GroupBuyActivity activity = invocation.getArgument(0);
            activity.setId(GENERATED_ID);
            return null;
        }).when(groupBuyActivityRepository).save(any(GroupBuyActivity.class));
    }

    // ==================== AD54 创建 ====================

    @Test
    @DisplayName("create — 未来时间窗 → 落库状态=未开始(1)，主键回填进 VO")
    void shouldDerivePendingStatus_whenCreateWithFutureWindow() {
        LocalDateTime now = LocalDateTime.now();

        GroupBuyActivityVO vo = groupBuyAdminManager.createActivity(
                createReq(now.plusHours(1), now.plusHours(3)));

        ArgumentCaptor<GroupBuyActivity> captor = ArgumentCaptor.forClass(GroupBuyActivity.class);
        verify(groupBuyActivityRepository).save(captor.capture());
        GroupBuyActivity saved = captor.getValue();
        assertEquals(GroupBuyActivityStatusEnum.PENDING.getCode(), saved.getStatus());
        assertEquals(GENERATED_ID, vo.getId());
        assertEquals(GroupBuyActivityStatusEnum.PENDING.getCode(), vo.getStatus());
    }

    @Test
    @DisplayName("create — 当前进行中的时间窗 → 落库状态=进行中(2)")
    void shouldDeriveActiveStatus_whenCreateWithCurrentWindow() {
        LocalDateTime now = LocalDateTime.now();

        groupBuyAdminManager.createActivity(createReq(now.minusMinutes(30), now.plusHours(2)));

        ArgumentCaptor<GroupBuyActivity> captor = ArgumentCaptor.forClass(GroupBuyActivity.class);
        verify(groupBuyActivityRepository).save(captor.capture());
        assertEquals(GroupBuyActivityStatusEnum.ACTIVE.getCode(), captor.getValue().getStatus());
    }

    @Test
    @DisplayName("create — 时间窗已整体过期 → PARAM_ERROR 拒绝，已结束状态不可能由创建产生")
    void shouldRejectCreate_whenTimeWindowAlreadyOver() {
        LocalDateTime now = LocalDateTime.now();

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.createActivity(
                        createReq(now.minusHours(2), now.minusHours(1))));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupBuyActivityRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — endTime 不晚于 startTime → PARAM_ERROR 拒绝")
    void shouldRejectCreate_whenEndTimeNotAfterStartTime() {
        LocalDateTime now = LocalDateTime.now();

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.createActivity(
                        createReq(now.plusHours(1), now.plusHours(1))));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupBuyActivityRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -100})
    @DisplayName("create — 拼团价 ≤0 → PARAM_ERROR 拒绝（聚合字段守卫）")
    void shouldRejectCreate_whenGroupPriceNotPositive(int groupPrice) {
        LocalDateTime now = LocalDateTime.now();
        GroupBuyCreateReq req = createReq(now.plusHours(1), now.plusHours(3));
        req.setGroupPrice(groupPrice);

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.createActivity(req));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupBuyActivityRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 0})
    @DisplayName("create — 成团人数 <2 → PARAM_ERROR 拒绝（1 人无拼团语义）")
    void shouldRejectCreate_whenRequiredMembersBelowTwo(int requiredMembers) {
        LocalDateTime now = LocalDateTime.now();
        GroupBuyCreateReq req = createReq(now.plusHours(1), now.plusHours(3));
        req.setRequiredMembers(requiredMembers);

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.createActivity(req));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupBuyActivityRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — 成团时限 <1 小时 → PARAM_ERROR 拒绝")
    void shouldRejectCreate_whenTimeLimitHoursBelowOne() {
        LocalDateTime now = LocalDateTime.now();
        GroupBuyCreateReq req = createReq(now.plusHours(1), now.plusHours(3));
        req.setTimeLimitHours(0);

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.createActivity(req));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupBuyActivityRepository, never()).save(any());
    }

    // ==================== AD55 编辑 ====================

    @Test
    @DisplayName("update — 仅传 name → null-safe 只改名称，其余字段不动")
    void shouldMergeOnlyProvidedFields_whenUpdatePartialBody() {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(3);
        GroupBuyActivity existing = activity(start, end);
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(existing);

        GroupBuyUpdateReq req = new GroupBuyUpdateReq();
        req.setName("iPhone 拼团");

        groupBuyAdminManager.updateActivity(ACTIVITY_ID, req);

        assertEquals("iPhone 拼团", existing.getName());
        assertEquals(SELLER_ID, existing.getSellerId());
        assertEquals(9900, existing.getGroupPrice());
        assertEquals(3, existing.getRequiredMembers());
        assertEquals(24, existing.getTimeLimitHours());
        assertEquals(start, existing.getStartTime());
        assertEquals(end, existing.getEndTime());
        assertEquals(GroupBuyActivityStatusEnum.ACTIVE.getCode(), existing.getStatus());
        verify(groupBuyActivityRepository).update(existing);
        verify(groupBuyActivityRepository, never()).save(any());
    }

    @Test
    @DisplayName("update — 名称传空白串 → 归一为 null 不更新（防空串覆盖）")
    void shouldNormalizeBlankNameToNull_whenUpdate() {
        GroupBuyActivity existing = activity(LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(3));
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(existing);

        GroupBuyUpdateReq req = new GroupBuyUpdateReq();
        req.setName("   ");

        groupBuyAdminManager.updateActivity(ACTIVITY_ID, req);

        assertEquals("进行中拼团活动", existing.getName());
        verify(groupBuyActivityRepository).update(existing);
    }

    @Test
    @DisplayName("update — 时间窗整体后移 → 状态重推导为未开始(1)落库")
    void shouldRederiveStatus_whenUpdateShiftsWindow() {
        GroupBuyActivity existing = activity(LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(3));
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(existing);
        LocalDateTime now = LocalDateTime.now();

        GroupBuyUpdateReq req = new GroupBuyUpdateReq();
        req.setStartTime(now.plusHours(2));
        req.setEndTime(now.plusHours(5));

        groupBuyAdminManager.updateActivity(ACTIVITY_ID, req);

        assertEquals(GroupBuyActivityStatusEnum.PENDING.getCode(), existing.getStatus());
        verify(groupBuyActivityRepository).update(existing);
    }

    @Test
    @DisplayName("update — 合并后成团人数 <2 → PARAM_ERROR 拒绝，不落库")
    void shouldRejectUpdate_whenMergedRequiredMembersBelowTwo() {
        GroupBuyActivity existing = activity(LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3));
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(existing);

        GroupBuyUpdateReq req = new GroupBuyUpdateReq();
        req.setRequiredMembers(1);

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.updateActivity(ACTIVITY_ID, req));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupBuyActivityRepository, never()).update(any());
    }

    @Test
    @DisplayName("update — 已结束活动 → PARAM_ERROR 拒绝编辑，不落库")
    void shouldRejectUpdate_whenActivityAlreadyEnded() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activity(
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1)));

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.updateActivity(ACTIVITY_ID, new GroupBuyUpdateReq()));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupBuyActivityRepository, never()).update(any());
    }

    @Test
    @DisplayName("update — 活动不存在 → NO_RESOURCE_FOUND(1103)")
    void shouldThrowNoResourceFound_whenUpdateMissingActivity() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.updateActivity(ACTIVITY_ID, new GroupBuyUpdateReq()));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    // ==================== AD56 删除 ====================

    @Test
    @DisplayName("delete — 进行中活动 → PARAM_ERROR 禁删，不物理删除")
    void shouldRejectDelete_whenActivityActive() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activity(
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(3)));

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.deleteActivity(ACTIVITY_ID));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupBuyActivityRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("delete — 已结束但仍有「拼团中」的团 → 拒删（间接资金影响，批 7 复核新增守卫）")
    void shouldRejectDelete_whenEndedButTeamsOngoing() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activity(
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1)));
        // 团还没成/还没退：删掉活动会让这批用户失去可追溯的活动配置，且退款依赖结团 Job
        when(groupTeamRepository.findTeamsByActivityAndStatus(
                eq(ACTIVITY_ID),
                eq(org.icedamericanomall.marketing.domain.enums.GroupTeamStatusEnum.ONGOING.getCode()),
                eq(1)))
                .thenReturn(java.util.List.of(
                        org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeam.builder()
                                .id(9L).activityId(ACTIVITY_ID).build()));

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.deleteActivity(ACTIVITY_ID));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupBuyActivityRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("delete — 已结束且无进行中的团 → 物理删除成功（守卫不得误伤）")
    void shouldDelete_whenEndedAndNoOngoingTeams() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activity(
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1)));
        when(groupTeamRepository.findTeamsByActivityAndStatus(anyLong(), anyInt(), anyInt()))
                .thenReturn(java.util.List.of());

        groupBuyAdminManager.deleteActivity(ACTIVITY_ID);

        verify(groupBuyActivityRepository).deleteById(ACTIVITY_ID);
    }

    @Test
    @DisplayName("delete — 已结束活动 → 物理删除成功")
    void shouldDelete_whenActivityEnded() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activity(
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1)));
        when(groupTeamRepository.findTeamsByActivityAndStatus(anyLong(), anyInt(), anyInt()))
                .thenReturn(java.util.List.of());

        groupBuyAdminManager.deleteActivity(ACTIVITY_ID);

        verify(groupBuyActivityRepository).deleteById(ACTIVITY_ID);
    }

    @Test
    @DisplayName("delete — 未开始活动 → 物理删除成功")
    void shouldDelete_whenActivityNotStarted() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activity(
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3)));
        when(groupTeamRepository.findTeamsByActivityAndStatus(anyLong(), anyInt(), anyInt()))
                .thenReturn(java.util.List.of());

        groupBuyAdminManager.deleteActivity(ACTIVITY_ID);

        verify(groupBuyActivityRepository).deleteById(ACTIVITY_ID);
    }

    @Test
    @DisplayName("delete — 活动不存在 → NO_RESOURCE_FOUND(1103)")
    void shouldThrowNoResourceFound_whenDeleteMissingActivity() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> groupBuyAdminManager.deleteActivity(ACTIVITY_ID));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        verify(groupBuyActivityRepository, never()).deleteById(any());
    }

    // ==================== 构造辅助 ====================

    private GroupBuyCreateReq createReq(LocalDateTime start, LocalDateTime end) {
        GroupBuyCreateReq req = new GroupBuyCreateReq();
        req.setName("iPhone 拼团");
        req.setSellerId(SELLER_ID);
        req.setSpuId(SPU_BUSINESS_ID);
        req.setSkuId(SKU_BUSINESS_ID);
        req.setGroupPrice(9900);
        req.setRequiredMembers(3);
        req.setTimeLimitHours(24);
        req.setStartTime(start);
        req.setEndTime(end);
        return req;
    }

    private GroupBuyActivity activity(LocalDateTime start, LocalDateTime end) {
        return GroupBuyActivity.builder()
                .id(ACTIVITY_ID)
                .name("进行中拼团活动")
                .sellerId(SELLER_ID)
                .spuId(1L)
                .skuId(2L)
                .groupPrice(9900)
                .requiredMembers(3)
                .timeLimitHours(24)
                .status(GroupBuyActivityStatusEnum.ACTIVE.getCode())
                .version(0)
                .startTime(start)
                .endTime(end)
                .build();
    }
}

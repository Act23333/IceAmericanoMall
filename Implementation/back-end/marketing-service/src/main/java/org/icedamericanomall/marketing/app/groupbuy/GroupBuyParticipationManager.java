package org.icedamericanomall.marketing.app.groupbuy;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.GroupBuyTeamClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.GroupJoinCO;
import org.icedamericanomall.dto.GroupTeamCO;
import org.icedamericanomall.dto.GroupTeamRefundCO;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.GroupBuyActivityVO;
import org.icedamericanomall.marketing.adapter.response.GroupTeamVO;
import org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief;
import org.icedamericanomall.marketing.domain.enums.GroupBuyActivityStatusEnum;
import org.icedamericanomall.marketing.domain.enums.GroupTeamStatusEnum;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupBuyActivity;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeam;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeamMember;
import org.icedamericanomall.marketing.domain.repository.GroupBuyActivityRepository;
import org.icedamericanomall.marketing.domain.repository.GroupTeamMemberRepository;
import org.icedamericanomall.marketing.domain.repository.GroupTeamRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拼团参与用例族编排（应用层 · C 端浏览 M15-M17 + 内部参团联动 I52/I53 + 结团，V5.14，ADR-049）。
 *
 * <p>与 {@code GroupBuyAdminManager}（AD54-56 活动管理）按用例族拆分（ADR-025）。
 * I53 join 为纯本地多语句写，事务边界在本层；结团退款联动经 ia-api {@code GroupBuyTeamClient}
 * （I54，fail-open + 逐团 try/catch），Feign 收敛在应用层（硬约束）。
 *
 * <p><b>幂等口径</b>：I53 以 {@code uk_order_no}/{@code uk_team_user} 双唯一键兜底——
 * 重放/撞键回落为返回现状；参团竞态由 {@code current_members} 条件更新兜底（0 行 = 团满/已结
 * → 抛异常回滚成员行）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyParticipationManager {

    /** M16 详情页「最近进行中团」条数上限（契约口径 ≤10）。 */
    private static final int TEAM_LIST_LIMIT = 10;

    /** 结团 Job 单批扫描上限（批次口径，分钟级调度下无积压风险）。 */
    private static final int EXPIRE_BATCH_LIMIT = 100;

    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final GroupTeamRepository groupTeamRepository;
    private final GroupTeamMemberRepository groupTeamMemberRepository;
    private final MarketingConverter marketingConverter;
    private final GroupBuyTeamClient groupBuyTeamClient;
    private final GroupBuyViewManager groupBuyViewManager;

    // ==================== C 端只读（M15-M17，🌐 匿名） ====================

    /** M15：进行中拼团活动分页（时间窗推导口径，id 降序；业务键 + SPU 名整页一次回填）。 */
    public IPage<GroupBuyActivityVO> pageActiveActivities(int pageNum, int pageSize) {
        IPage<GroupBuyActivity> page =
                groupBuyActivityRepository.findActiveActivities(LocalDateTime.now(), pageNum, pageSize);
        List<GroupBuyActivityVO> vos = groupBuyViewManager.toViewList(page.getRecords());
        IPage<GroupBuyActivityVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(vos);
        result.setPages(page.getPages());
        return result;
    }

    /** M16：活动详情 + 最近进行中团 ≤10（团队列表不含他人 userId）。 */
    public GroupBuyActivityVO getActivityDetail(Long id) {
        GroupBuyActivity activity = requireActivity(id);
        GroupBuyActivityVO vo = groupBuyViewManager.toView(activity);
        vo.setTeams(marketingConverter.toGroupTeamVOList(groupTeamRepository
                .findTeamsByActivityAndStatus(activity.getId(), GroupTeamStatusEnum.ONGOING.getCode(), TEAM_LIST_LIMIT)));
        return vo;
    }

    /** M17：团进度查询（按团号；不出任何 userId）。 */
    public GroupTeamVO getTeamDetail(String teamNo) {
        GroupTeam team = groupTeamRepository.findByTeamNo(teamNo);
        if (team == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "拼团不存在: " + teamNo);
        }
        return marketingConverter.toVO(team);
    }

    // ==================== I52 下单前置校验（fail-closed） ====================

    /**
     * I52：拼团下单前置校验（trade T27 建单依据，拼团价是计价业务事实，绝不静默降级）。
     *
     * <p>校验面：活动进行中（时间窗推导）+ 参团时团存在/拼团中/未满/用户未在该团
     * （uk_team_user 预检）。
     *
     * @param query {@code GroupBuyValidateCO}（teamNo 空 = 开团校验）
     * @return 拼团价等建单依据
     * @throws BizException 活动无效/团不存在/团已结/团满/重复参团（{@link ErrorCode#PARAM_ERROR}，400）
     */
    public GroupBuyValidateResultCO validate(GroupBuyValidateCO query) {
        GroupBuyActivity activity = requireActiveActivity(query.getActivityId());
        if (query.getTeamNo() != null && !query.getTeamNo().isBlank()) {
            ensureTeamJoinable(requireTeamByNo(query.getTeamNo()), query.getUserId());
        }
        // 出参 SKU 一律业务键（ADR-061 §决策 2）：I52 回执 → trade 下单取数（I13）与订单项标识列
        // 同口径；活动行本身存技术键，故经读模型换算一次（解析不到即视为活动配置异常，
        // 抛 PARAM_ERROR 让下单 fail-closed，而不是下出一个没有商品标识的订单）
        FlashSaleSpuBrief brief = groupBuyViewManager.findBySkuTechnicalId(activity.getSkuId());
        if (brief == null || brief.skuBusinessId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "拼团活动绑定的 SKU 不存在或已删除");
        }
        return new GroupBuyValidateResultCO(activity.getId(), query.getTeamNo(),
                activity.getGroupPrice(), activity.getRequiredMembers(), activity.getTimeLimitHours(),
                brief.skuBusinessId());
    }

    // ==================== I53 支付成功参团（fail-closed，uk 幂等） ====================

    /**
     * I53：开团（teamNo 空）/参团（非空）。两段式参团的第二段——支付成功才入团。
     *
     * <p>开团 = 创建团（leader=本人，{@code expire_time=now+timeLimitHours}，currentMembers=1）
     * + 首个成员行；参团 = 成员行 + 条件更新人数 + 满员条件置已成团。
     * {@code uk_order_no}/{@code uk_team_user} 撞键回落为返回现状（幂等重放）。
     *
     * @param join {@code GroupJoinCO}（orderNo 为幂等锚点）
     * @return 团状态回执（满员时 status=2）
     * @throws BizException 活动无效/团不存在/团已结/团满/重复参团/条件更新 0 行
     *                      （{@link ErrorCode#PARAM_ERROR}，400；trade 捕获后走退款闭环）
     */
    @Transactional(rollbackFor = Exception.class)
    public GroupTeamCO join(GroupJoinCO join) {
        GroupTeamMember replayed = groupTeamMemberRepository.findByOrderNo(join.getOrderNo());
        if (replayed != null) {
            // uk_order_no 幂等重放：一单一团一次，直接返回其已入团的现状
            return toTeamCO(requireTeam(replayed.getTeamId()));
        }
        GroupBuyActivity activity = requireActiveActivity(join.getActivityId());
        if (join.getTeamNo() == null || join.getTeamNo().isBlank()) {
            return openTeam(activity, join);
        }
        return joinExistingTeam(join);
    }

    // ==================== 结团（GroupBuyExpireJob 入口） ====================

    /**
     * 过期团结团：扫拼团中且已过期的团 → 条件置已失败（1→3）→ 逐团触发 I54 退款联动。
     *
     * <p>单团异常 try/catch 继续下一团（不中断整批）；退款联动 fail-open（降级已在
     * {@code GroupBuyTeamClientFallback} 告警承担，失败单依赖 refund_order 失败态补偿，ADR-049）。
     */
    public void expireOverdueTeams() {
        List<GroupTeam> expiredTeams = groupTeamRepository.listExpiredTeams(LocalDateTime.now(), EXPIRE_BATCH_LIMIT);
        for (GroupTeam team : expiredTeams) {
            try {
                int updated = groupTeamRepository.updateTeamStatusConditional(team.getId(),
                        GroupTeamStatusEnum.ONGOING.getCode(), GroupTeamStatusEnum.FAILED.getCode());
                if (updated > 0) {
                    triggerTeamRefund(team);
                }
            } catch (Exception e) {
                log.warn("[拼团结团] [单团失败继续] [teamNo={}]", team.getTeamNo(), e);
            }
        }
    }

    // ==================== 内部方法 ====================

    /** 开团：创建团（团长自占一位）+ 首个成员行；团号 UUID，截止时间 = now + 活动时限。 */
    private GroupTeamCO openTeam(GroupBuyActivity activity, GroupJoinCO join) {
        GroupTeam team = GroupTeam.open(activity.getId(), join.getUserId(), activity.getRequiredMembers(),
                IdGenerator.nextId(), LocalDateTime.now().plusHours(activity.getTimeLimitHours()));
        groupTeamRepository.save(team);
        groupTeamMemberRepository.save(GroupTeamMember.join(team.getId(), join.getUserId(), join.getOrderNo()));
        return toTeamCO(team);
    }

    /** 参团：成员行插入（uk 幂等回落）→ 条件更新 +1 → 满员条件置已成团 → 回读现状。 */
    private GroupTeamCO joinExistingTeam(GroupJoinCO join) {
        GroupTeam team = requireTeamByNo(join.getTeamNo());
        ensureTeamJoinable(team, join.getUserId());
        try {
            groupTeamMemberRepository.save(GroupTeamMember.join(team.getId(), join.getUserId(), join.getOrderNo()));
        } catch (DuplicateKeyException e) {
            return resolveDuplicateJoin(team, join);
        }
        if (groupTeamRepository.incrementCurrentMembers(team.getId()) == 0) {
            // 竞态兜底：0 行 = 团满/已结；抛异常触发事务回滚，刚插的成员行一并撤销
            throw new BizException(ErrorCode.PARAM_ERROR, "团已满或已成团，参团失败");
        }
        groupTeamRepository.markTeamSuccessIfFull(team.getId());
        return toTeamCO(requireTeam(team.getId()));
    }

    /** 参团前置校验（I52 与 I53 共用）：拼团中 + 未满 + 用户未在该团。 */
    private void ensureTeamJoinable(GroupTeam team, Long userId) {
        if (!team.isOngoing()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "团已成团或已失败，不能加入");
        }
        if (team.isFull()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "团已满，不能加入");
        }
        if (groupTeamMemberRepository.existsByTeamIdAndUserId(team.getId(), userId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已参与该团，不能重复参团");
        }
    }

    /** uk 撞键回落为返回现状：uk_order_no 重放（可能已入其他团）或 uk_team_user（同人同团）。 */
    private GroupTeamCO resolveDuplicateJoin(GroupTeam team, GroupJoinCO join) {
        GroupTeamMember byOrder = groupTeamMemberRepository.findByOrderNo(join.getOrderNo());
        if (byOrder != null) {
            return toTeamCO(requireTeam(byOrder.getTeamId()));
        }
        if (groupTeamMemberRepository.existsByTeamIdAndUserId(team.getId(), join.getUserId())) {
            return toTeamCO(requireTeam(team.getId()));
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "参团冲突，请重试");
    }

    /** 置失败成功后触发 I54 退款联动（收集成员 orderNos，trade 侧逐单取消+恢复库存+退款）。 */
    private void triggerTeamRefund(GroupTeam team) {
        List<String> orderNos = groupTeamMemberRepository.listByTeamId(team.getId()).stream()
                .map(GroupTeamMember::getOrderNo).toList();
        groupBuyTeamClient.refundTeam(new GroupTeamRefundCO(team.getTeamNo(), orderNos));
        log.info("[拼团结团] [置已失败并触发退款] [teamNo={}] [orderCount={}]", team.getTeamNo(), orderNos.size());
    }

    /** 按主键取活动（M16 C 端口径：不存在 → 1103）。 */
    private GroupBuyActivity requireActivity(Long id) {
        GroupBuyActivity activity = groupBuyActivityRepository.findById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "拼团活动不存在: " + id);
        }
        return activity;
    }

    /** 活动存在且进行中（I52/I53 口径：无效 → 400，活动是拼团价的计价前提）。 */
    private GroupBuyActivity requireActiveActivity(Long activityId) {
        GroupBuyActivity activity = groupBuyActivityRepository.findById(activityId);
        if (activity == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "拼团活动不存在: " + activityId);
        }
        if (activity.deriveStatus(LocalDateTime.now()) != GroupBuyActivityStatusEnum.ACTIVE.getCode()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "拼团活动未在进行中");
        }
        return activity;
    }

    /** 按团号取团（M16/I52/I53 口径：不存在 → 1103）。 */
    private GroupTeam requireTeamByNo(String teamNo) {
        GroupTeam team = groupTeamRepository.findByTeamNo(teamNo);
        if (team == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "拼团不存在: " + teamNo);
        }
        return team;
    }

    /** 按主键取团（回读现状用；缺失属数据异常 → 1103）。 */
    private GroupTeam requireTeam(Long teamId) {
        GroupTeam team = groupTeamRepository.findById(teamId);
        if (team == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "拼团不存在: " + teamId);
        }
        return team;
    }

    private GroupTeamCO toTeamCO(GroupTeam team) {
        return marketingConverter.toCO(team);
    }
}

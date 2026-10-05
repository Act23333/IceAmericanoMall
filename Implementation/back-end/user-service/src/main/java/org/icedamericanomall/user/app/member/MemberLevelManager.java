package org.icedamericanomall.user.app.member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.MemberGrowthCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.user.domain.enums.MemberLevelEnum;
import org.icedamericanomall.user.domain.model.member.MemberGrowthLog;
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.user.domain.repository.MemberGrowthLogRepository;
import org.icedamericanomall.user.domain.repository.MemberLevelRuleRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.icedamericanomall.user.domain.service.MemberLevelPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 会员等级与权益（应用层编排，F6 会员等级 —— ADR-044）。
 *
 * <p>用例族：I45 权益查询（trade 定价中心消费）与 I46 成长值入账 + 等级重算（trade 确认收货后）。
 * 依赖倒置：只依赖领域仓储与重算纯函数，不出现 Mapper / PO / 框架类型。
 *
 * <p><b>降级定类（ADR-044）：fail-open</b> —— 等级是准静态数据，异常方向是「用户少得优惠」
 * （体验损失、可补偿）而非平台资损；规则缺行按枚举默认（不折扣/1 倍）兜底并告警，等级如实返回。
 *
 * <p><b>I46 幂等三态</b>（锚点 = {@code member_growth_log.uk_order_no}，一单一次成长）：
 * ① 顺序重放（同 orderNo 已有流水）→ 返回现有流水入账后权益；② 并发撞唯一键
 * （{@link DuplicateKeyException}）→ 回落为「查现有流水返回」，不向调用方透出
 * （ADR-035 {@code refund_order} 先例）；③ 首次入账 → 插流水 + 等级迁移（同一本地事务）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberLevelManager {

    /** 规则缺行时的权益兜底口径（与 seed NORMAL 行一致：不折扣 / 1 倍积分，ADR-044）。 */
    static final int DEFAULT_DISCOUNT_RATE = 10000;
    static final int DEFAULT_POINTS_RATE = 100;

    private final UserRepository userRepository;
    private final MemberLevelRuleRepository memberLevelRuleRepository;
    private final MemberGrowthLogRepository memberGrowthLogRepository;

    /**
     * I45 查询会员等级与权益（读 {@code user.member_level} + 按 level 匹配规则取折扣率/积分倍率）。
     *
     * <p>只读不校验账号状态 —— 冻结用户的在途订单仍需正确计价（fail-open 口径）。
     *
     * @param userId 买家 user_id（跨服务技术主键口径）
     * @return 等级权益（等级如实返回；规则缺行时权益按枚举默认兜底）
     * @throws BizException userId 为空（PARAM_ERROR）/ 用户不存在（USER_NOT_FOUND）
     */
    public MemberLevelCO getLevel(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "userId 不能为空");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        return benefitsOf(user.getMemberLevel());
    }

    /**
     * I46 成长值入账 + 等级重算（订单完成通知，幂等锚点 orderNo）。
     *
     * <p>编排（插流水 + 更新 user 为纯本地写，同一本地事务；无 Feign/MQ）：幂等前置检查 →
     * 累计成长值求和 → 等级重算（{@link MemberLevelPolicy#recalculate} 纯函数，只升不降）→
     * 插流水 → 等级迁移 → 返回入账后权益。
     *
     * @param growth 入账契约（userId/orderNo/payAmount；payAmount 为分且 &gt; 0，= 本单成长值）
     * @return 入账后等级权益（重放/并发回落时返回现有流水的入账后等级权益）
     * @throws BizException 入参非法（PARAM_ERROR）/ 用户不存在（USER_NOT_FOUND）
     */
    @Transactional(rollbackFor = Exception.class)
    public MemberLevelCO recordGrowth(MemberGrowthCO growth) {
        validateGrowth(growth);
        Optional<MemberGrowthLog> existing = memberGrowthLogRepository.findByOrderNo(growth.getOrderNo());
        if (existing.isPresent()) {
            log.info("[member][growth][idempotent] orderNo={} 已入账，返回现有权益 level={}",
                    growth.getOrderNo(), existing.get().getAfterLevel());
            return benefitsOf(existing.get().getAfterLevel());
        }
        try {
            return doRecord(growth);
        } catch (DuplicateKeyException e) {
            // 并发孪生请求已入账（uk_order_no 兜底）：回落为「查现有流水返回」，不向调用方抛；
            // findByOrderNo 为锁定读，可读到胜者已提交行。无法定位既有流水时原样上抛
            // （此时本次事务整体回滚，防御极端脏数据，参照 ADR-035 RefundManager 先例）
            MemberGrowthLog concurrent = memberGrowthLogRepository.findByOrderNo(growth.getOrderNo())
                    .orElseThrow(() -> e);
            log.info("[member][growth][idempotent][concurrent] orderNo={} 并发入账，返回既有权益 level={}",
                    growth.getOrderNo(), concurrent.getAfterLevel());
            return benefitsOf(concurrent.getAfterLevel());
        }
    }

    /**
     * 首次入账领域流程（调用方已保证同 orderNo 无流水）。
     *
     * <p>等级迁移用定向更新（只写 member_level 列，{@code updateMemberLevelById}），
     * 不做全量 update，避免与资料编辑在乐观锁上相互踩踏。
     */
    private MemberLevelCO doRecord(MemberGrowthCO growth) {
        User user = userRepository.findById(growth.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        long accumulated = memberGrowthLogRepository.sumGrowthByUserId(user.getId()) + growth.getPayAmount();
        MemberLevelEnum target = MemberLevelPolicy.recalculate(
                user.getMemberLevel(), accumulated, memberLevelRuleRepository.findAll());
        memberGrowthLogRepository.insert(
                MemberGrowthLog.of(growth.getUserId(), growth.getOrderNo(), growth.getPayAmount(), target));
        if (target != user.getMemberLevel()) {
            userRepository.updateMemberLevelById(user.getId(), target);
            log.info("[member][level][upgrade] userId={} {} -> {}", user.getId(), user.getMemberLevel(), target);
        }
        return benefitsOf(target);
    }

    /** I46 入参兜底校验（控制器 {@code @Validated} 之外的第二道防线，Manager 单测亦直接调用）。 */
    private void validateGrowth(MemberGrowthCO growth) {
        if (growth == null || growth.getUserId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "userId 不能为空");
        }
        if (growth.getOrderNo() == null || growth.getOrderNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "orderNo 不能为空");
        }
        if (growth.getPayAmount() == null || growth.getPayAmount() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "payAmount 必须为正整数（分）");
        }
    }

    /**
     * 等级 → 权益装配（I45/I46 响应共用）。
     *
     * <p>规则缺行（含等级 code 无法归一化的存量脏值，按 NORMAL 处理）按枚举默认权益兜底并告警，
     * 等级仍如实返回 —— fail-open 方向 = 少得优惠（ADR-044）。
     */
    private MemberLevelCO benefitsOf(MemberLevelEnum level) {
        MemberLevelEnum safeLevel = level == null ? MemberLevelEnum.NORMAL : level;
        return memberLevelRuleRepository.findByLevel(safeLevel)
                .map(rule -> new MemberLevelCO(safeLevel.getCode(), rule.discountRate(), rule.pointsRate()))
                .orElseGet(() -> defaultBenefits(safeLevel));
    }

    /** 规则缺行兜底：不折扣 / 1 倍积分（与 seed NORMAL 行一致）。 */
    private MemberLevelCO defaultBenefits(MemberLevelEnum level) {
        log.warn("[member][level][rule-missing] 等级规则缺行，按枚举默认权益兜底 level={}", level);
        return new MemberLevelCO(level.getCode(), DEFAULT_DISCOUNT_RATE, DEFAULT_POINTS_RATE);
    }
}

package org.icedamericanomall.user.domain.model.member;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.domain.enums.MemberLevelEnum;

/**
 * 会员等级规则（领域值对象，{@code record} 不可变 —— F6 会员等级，ADR-044）。
 *
 * <p>映射 {@code member_level_rule} 表一行（Phase 1 为 seed 预置的产品决策，无管理端编辑，
 * 改表 seed 即生效）。相等于所有属性（无技术主键语义）。
 *
 * <p><b>等级比较基准（选型说明）</b>：等级序以 {@link #threshold}（累计成长值门槛，分）为准 ——
 * 与 {@code sort}（升序）在 seed 数据中同序等价，选 threshold 因等级匹配/升级判断本身以
 * 成长值门槛为语义，避免维护两套比较基准；{@code sort} 仅作仓储 SQL 排序用。
 *
 * @param level        等级（与 {@link MemberLevelEnum} 对齐）
 * @param threshold    累计成长值门槛（分，≥ 0；0 = 注册即得）
 * @param discountRate 会员折扣率万分比（10000 = 不折扣，9800 = 98%）
 * @param pointsRate   积分倍率百分比（100 = 1 倍，作用于下单奖励积分）
 * @param sort         比较顺序（升序，与 threshold 同序）
 */
public record MemberLevelRule(MemberLevelEnum level, int threshold, int discountRate, int pointsRate, int sort) {

    /**
     * 紧凑构造器 —— 创建即校验合法性（自封装）。
     *
     * @throws BizException level 为空或数值字段为负（PARAM_ERROR）
     */
    public MemberLevelRule {
        if (level == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "会员等级规则 level 不能为空");
        }
        if (threshold < 0 || discountRate < 0 || pointsRate < 0 || sort < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "会员等级规则数值字段不能为负: " + level.getCode());
        }
    }
}

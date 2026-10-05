package org.icedamericanomall.user.domain.model.member;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.domain.enums.MemberLevelEnum;

import java.time.LocalDateTime;

/**
 * 会员成长值入账流水（聚合根，纯 POJO —— F6 会员等级，ADR-044）。
 *
 * <p>业务唯一键 = {@code orderNo}（{@code member_growth_log.uk_order_no} 幂等锚点：
 * 一单一次成长，重复请求不重复入账、不报错，直接返回当前等级权益）。
 *
 * <p>成长值口径 = 订单实付金额（分），订单完成（确认收货）后经 I46 入账；
 * {@code afterLevel} 记录入账后等级（只升不降，Phase 1 —— 年度降级考核留 roadmap），
 * 流水按单累计，天然可审计。
 */
@Data
@Builder
public class MemberGrowthLog {

    /** 技术主键（业务定位一律走 {@code orderNo} 唯一键） */
    private Long id;

    /** 所属用户 ID（跨服务 user_id 语义，技术主键） */
    private Long userId;

    /** 来源订单号（业务唯一键，幂等锚点） */
    private String orderNo;

    /** 本单成长值（= 订单实付金额，分，必须 &gt; 0） */
    private Integer growthAmount;

    /** 入账后等级（持久化/报文口径为 String code） */
    private MemberLevelEnum afterLevel;

    /** 入账时间 */
    private LocalDateTime createTime;

    /**
     * 新建一条成长值入账流水（业务键 {@code orderNo}）。
     *
     * @param userId       所属用户
     * @param orderNo      来源订单号（幂等锚点，不得为空）
     * @param growthAmount 本单成长值（分，必须 &gt; 0）
     * @param afterLevel   入账后等级
     * @return 待持久化的流水聚合
     * @throws BizException 入参非法（PARAM_ERROR）
     */
    public static MemberGrowthLog of(Long userId, String orderNo, Integer growthAmount, MemberLevelEnum afterLevel) {
        if (userId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "成长值入账 userId 不能为空");
        }
        if (orderNo == null || orderNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "成长值入账 orderNo 不能为空");
        }
        if (growthAmount == null || growthAmount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "成长值必须为正整数（分）");
        }
        if (afterLevel == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "成长值入账 afterLevel 不能为空");
        }
        return MemberGrowthLog.builder()
                .userId(userId)
                .orderNo(orderNo)
                .growthAmount(growthAmount)
                .afterLevel(afterLevel)
                .build();
    }
}

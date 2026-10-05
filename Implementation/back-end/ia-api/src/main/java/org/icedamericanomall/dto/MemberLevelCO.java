package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员等级与权益（trade-service 定价中心消费，契约 §4.14.1b I45，V5.8）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberLevelCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 等级（NORMAL/SILVER/GOLD/PLATINUM） */
    private String level;

    /** 会员折扣率万分比（10000=不折扣，9800=98%） */
    private Integer discountRate;

    /** 积分倍率百分比（100=1倍，作用于下单奖励积分） */
    private Integer pointsRate;
}

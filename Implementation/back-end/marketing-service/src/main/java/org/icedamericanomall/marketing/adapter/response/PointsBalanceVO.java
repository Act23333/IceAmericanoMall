package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 积分余额视图对象（适配层出参）—— {@code GET /api/points/balance} 响应。
 *
 * <p>原实现返回 {@code Map<String, Object>}（弱类型，见 09-constraints「Controller 返回 Map」反模式），
 * 现改为强类型 VO：JSON 字段名与形状保持不变（{@code userId} / {@code balance}），仅消除弱类型返回。
 */
@Data
public class PointsBalanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前登录用户ID。 */
    private Long userId;

    /** 积分总余额。 */
    private long balance;
}

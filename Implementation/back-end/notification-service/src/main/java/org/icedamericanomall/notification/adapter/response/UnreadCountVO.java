package org.icedamericanomall.notification.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 未读站内信计数视图对象（U29 {@code GET /api/notifications/unread-count} 出参）。
 *
 * <p><b>批次 D</b>：改造前在 Controller 内就地拼 {@code Map.of("unread", …)}（适配层拼弱类型
 * 报文），现改为强类型 VO，由 Controller 以静态工厂装配 —— 与同控制器 {@code GET /api/notifications}
 * 用 {@link InAppMessageVO#fromPage} 装配分页报文的既有形态一致。
 *
 * <p><b>线上键（无 BREAKING）</b>：{@code unread} 为单词，{@code SNAKE_CASE} 下线上键与改造前
 * Map 字面键<b>逐字节相同</b>；前端 {@code packages/api/types} 的 {@code UnreadCountVO.unread}
 * 与小程序 {@code pages/profile} 的 {@code data.unread} 读取点均<b>无需改动</b>。
 *
 * <p><b>口径</b>：{@code notification} 表中 {@code recipient_id = 当前用户} 且
 * {@code is_read = 0} 的行数；0 是合法业务态。
 */
@Data
public class UnreadCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 未读站内信条数（{@code notification.is_read = 0}，单位：条；非负）。 */
    private Integer unread;

    /**
     * 装配入口（未读条数 → VO）。
     *
     * @param unread 未读条数（非负；仓储 {@code countUnread} 的 {@code int} 返回值）
     * @return 未读计数视图对象
     */
    public static UnreadCountVO of(int unread) {
        UnreadCountVO vo = new UnreadCountVO();
        vo.setUnread(unread);
        return vo;
    }
}

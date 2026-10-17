package org.icedamericanomall.notification.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.notification.adapter.response.InAppMessageVO;
import org.icedamericanomall.notification.adapter.response.UnreadCountVO;
import org.icedamericanomall.notification.app.InAppMessageManager;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内信公开端点（适配层）—— 消息通知中心。
 *
 * <p>承接原 user-service {@code /api/user/notifications} 的全部职责（ADR-016 收尾）。
 * 端点清单（报文形状与迁移前一致，仅路径变化）：
 * <pre>
 * GET    /api/notifications                通知列表（分页，可按类型筛选）
 * GET    /api/notifications/unread-count   未读计数
 * PUT    /api/notifications/{id}/read      标记单条已读
 * PUT    /api/notifications/read-all       全部已读
 * DELETE /api/notifications/{id}           删除
 * </pre>
 *
 * <p>用户身份取网关透传的 {@link UserContext}；归属校验在仓储条件内兜底。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final InAppMessageManager inAppMessageManager;

    /** 通知列表（分页，可按类型筛选）。 */
    @GetMapping
    public Result<IPage<InAppMessageVO>> list(@RequestParam(required = false) String type,
                                              @RequestParam(name = "page_num", defaultValue = "1") long pageNum,
                                              @RequestParam(name = "page_size", defaultValue = "10") long pageSize) {
        Long userId = UserContext.getUserId();
        return Result.ok(InAppMessageVO.fromPage(
                inAppMessageManager.pageInApp(userId, type, pageNum, pageSize)));
    }

    /**
     * 未读计数（U29）。
     *
     * <p><b>批次 D</b>：出参由 {@code Result<Map<String, Integer>>} 改为
     * {@code Result<UnreadCountVO>}（线上键 {@code unread} 不变 → <b>无 BREAKING</b>）。
     * 装配用 VO 静态工厂 {@link UnreadCountVO#of}，与同控制器 {@code list} 用
     * {@link InAppMessageVO#fromPage} 装配分页报文同形（适配层不手写 Map）。
     */
    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount() {
        return Result.ok(UnreadCountVO.of(inAppMessageManager.countUnread(UserContext.getUserId())));
    }

    /** 标记单条已读。 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        inAppMessageManager.markRead(id, UserContext.getUserId());
        return Result.ok();
    }

    /** 全部已读。 */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        inAppMessageManager.markAllRead(UserContext.getUserId());
        return Result.ok();
    }

    /** 删除通知。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        inAppMessageManager.deleteInApp(id, UserContext.getUserId());
        return Result.ok();
    }
}

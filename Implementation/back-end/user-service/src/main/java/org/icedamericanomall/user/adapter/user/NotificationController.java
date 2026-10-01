package org.icedamericanomall.user.adapter.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.NotificationVO;
import org.icedamericanomall.user.app.notification.NotificationManager;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 消息通知中心 — V5.0
 *
 * <p>京东/淘宝标准: 点赞/回复/订单通知，支持分类型筛选、已读/未读、全部已读。
 * 报文形状与重构前一致（records[i] 含 unread 派生字段）。
 */
@RestController
@RequestMapping("/api/user/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationManager notificationManager;

    /** 通知列表 (支持按类型筛选) */
    @GetMapping
    public Result<IPage<NotificationVO>> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        return Result.ok(notificationManager.pageByUser(userId, type, page, size));
    }

    /** 未读计数 */
    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> unreadCount() {
        Long userId = UserContext.getUserId();
        return Result.ok(Map.of("count", notificationManager.countUnread(userId)));
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        notificationManager.markRead(id, userId);
        return Result.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        Long userId = UserContext.getUserId();
        notificationManager.markAllRead(userId);
        return Result.ok();
    }

    /** 删除通知 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        notificationManager.deleteById(id, userId);
        return Result.ok();
    }
}

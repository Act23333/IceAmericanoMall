package org.icedamericanomall.controller.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.NotificationEntity;
import org.icedamericanomall.service.NotificationService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 消息通知中心 — V5.0
 *
 * 京东/淘宝标准: 点赞/回复/订单通知，支持分类型筛选、已读/未读、全部已读
 */
@RestController
@RequestMapping("/api/user/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 通知列表 (支持按类型筛选) */
    @GetMapping
    public Result<IPage<NotificationEntity>> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        return Result.ok(notificationService.pageByUser(userId, type, page, size));
    }

    /** 未读计数 */
    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> unreadCount() {
        Long userId = UserContext.getUserId();
        return Result.ok(Map.of("count", notificationService.countUnread(userId)));
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        notificationService.markRead(id, userId);
        return Result.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        Long userId = UserContext.getUserId();
        notificationService.markAllRead(userId);
        return Result.ok();
    }

    /** 删除通知 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        notificationService.deleteById(id, userId);
        return Result.ok();
    }
}

package org.icedamericanomall.controller.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.service.HistoryService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 浏览足迹 Controller — V5.0 京东标准
 *
 * POST   /api/user/history?productId=        记录浏览
 * GET    /api/user/history?page=&size=&keyword=&sort=  分页查询(含商品详情+浏览时间)
 * DELETE /api/user/history/{id}              删除单条
 * DELETE /api/user/history                   清空全部
 */
@RestController
@RequestMapping("/api/user/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @PostMapping
    public Result<?> record(@RequestParam Long productId) {
        historyService.record(UserContext.getUserId(), productId);
        return Result.ok();
    }

    @GetMapping
    public Result<IPage<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "newest") String sort) {
        return Result.ok(historyService.page(UserContext.getUserId(), page, size, keyword, sort));
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteById(@PathVariable Long id) {
        historyService.deleteById(UserContext.getUserId(), id);
        return Result.ok();
    }

    @DeleteMapping
    public Result<?> clear() {
        historyService.clear(UserContext.getUserId());
        return Result.ok();
    }
}

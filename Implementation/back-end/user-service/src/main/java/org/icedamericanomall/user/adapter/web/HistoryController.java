package org.icedamericanomall.user.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.HistoryVO;
import org.icedamericanomall.user.app.history.HistoryManager;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.web.bind.annotation.*;

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

    private final HistoryManager historyManager;

    /** 记录商品浏览（同一商品重复浏览仅刷新浏览时间）。 */
    @PostMapping
    public Result<Void> record(@RequestParam Long productId) {
        historyManager.record(UserContext.getUserId(), productId);
        return Result.ok();
    }

    /** 分页查询本人足迹（含商品详情与浏览时间，可按商品名搜索、按时间排序）。 */
    @GetMapping
    public Result<IPage<HistoryVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "newest") String sort) {
        return Result.ok(historyManager.page(UserContext.getUserId(), page, size, keyword, sort));
    }

    /** 删除单条足迹（仅限本人）。 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable Long id) {
        historyManager.deleteById(UserContext.getUserId(), id);
        return Result.ok();
    }

    /** 清空本人全部足迹。 */
    @DeleteMapping
    public Result<Void> clear() {
        historyManager.clear(UserContext.getUserId());
        return Result.ok();
    }
}

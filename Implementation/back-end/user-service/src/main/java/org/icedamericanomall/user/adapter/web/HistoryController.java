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
 * POST   /api/user/history?spu_id=        记录浏览
 * GET    /api/user/history?page=&size=&keyword=&sort=  分页查询(含商品详情+浏览时间)
 * DELETE /api/user/history/{historyId}       删除单条
 * DELETE /api/user/history                   清空全部
 *
 * <p><b>路径变量口径（ADR-062 §决策 2 表 C 末行 · 勘误）</b>：{@code {historyId}} 是
 * <b>足迹行主键</b>（{@code spu_view_log.id}），<b>不是</b> SPU 标识 —— 契约原写
 * {@code {productId}} 是「命名说 A、实现是 B」型漂移，本批订正为 {@code {historyId}}
 * 且保持 {@code Long}（ADR-058 §1 例外：{@code id} 类资源行主键）。
 */
@RestController
@RequestMapping("/api/user/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryManager historyManager;

    /** 记录商品浏览（同一商品重复浏览仅刷新浏览时间）。{@code spu_id} 为 SPU <b>业务键</b> 32-hex（§1.8②）。 */
    @PostMapping
    public Result<Void> record(@RequestParam(name = "spu_id") String spuId) {
        historyManager.record(UserContext.getUserId(), spuId);
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

    /** 删除单条足迹（仅限本人）。{@code historyId} 为足迹行主键（非 SPU 标识，见类注释）。 */
    @DeleteMapping("/{historyId}")
    public Result<Void> deleteById(@PathVariable Long historyId) {
        historyManager.deleteById(UserContext.getUserId(), historyId);
        return Result.ok();
    }

    /** 清空本人全部足迹。 */
    @DeleteMapping
    public Result<Void> clear() {
        historyManager.clear(UserContext.getUserId());
        return Result.ok();
    }
}

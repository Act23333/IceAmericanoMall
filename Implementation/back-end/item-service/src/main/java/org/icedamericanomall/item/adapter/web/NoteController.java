package org.icedamericanomall.item.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.item.adapter.request.NotePublishReq;
import org.icedamericanomall.item.adapter.response.NoteCardVO;
import org.icedamericanomall.item.adapter.response.NoteDetailVO;
import org.icedamericanomall.item.adapter.response.NoteVO;
import org.icedamericanomall.item.app.content.NoteManager;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 种草笔记 Controller（F12 内容社区 Phase 1，P28-P33，ADR-048）。
 *
 * <p>认证口径：P28/P31/P32/P33 为 🔒（网关强制认证，user_id 取登录态）；
 * P29/P30 为 🌐（GET 白名单匿名放行，详情 liked 匿名恒 false）。业务逻辑一律在
 * {@link NoteManager}，本类只做参数校验 + 编排调用 + {@code Result} 包装。
 */
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteManager noteManager;

    /** P28 发布种草笔记（发布即上架，status 恒 1，无草稿）。 */
    @PostMapping
    public Result<NoteVO> publish(@Valid @RequestBody NotePublishReq req) {
        return Result.ok(noteManager.publish(UserContext.getUserId(), req));
    }

    /** P29 笔记流（已发布，发布时间倒序分页，轻卡片不含正文）。 */
    @GetMapping
    public Result<IPage<NoteCardVO>> feed(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(noteManager.pagePublished(page, size));
    }

    /** P30 笔记详情（🌐 匿名可看；liked 按登录视角回填，关联商品在售过滤）。 */
    @GetMapping("/{id}")
    public Result<NoteDetailVO> detail(@PathVariable Long id) {
        return Result.ok(noteManager.noteDetail(id, UserContext.getUserId()));
    }

    /** P31 点赞（uk 幂等：重复点赞空操作不报错；首次成功后计数原子 +1）。 */
    @PostMapping("/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        noteManager.like(UserContext.getUserId(), id);
        return Result.ok();
    }

    /** P32 取消点赞（未点赞空操作；取消成功后计数原子 -1 且不为负）。 */
    @DeleteMapping("/{id}/like")
    public Result<Void> unlike(@PathVariable Long id) {
        noteManager.unlike(UserContext.getUserId(), id);
        return Result.ok();
    }

    /** P33 删除笔记（仅作者本人，非本人 403；软删 status=2，流与详情不可见）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noteManager.delete(UserContext.getUserId(), id);
        return Result.ok();
    }

    /** P34 相似笔记（I51 kNN 命中本地组装发布态卡；推荐位口径——降级/无命中空列表不报错）。 */
    @GetMapping("/{id}/similar")
    public Result<List<NoteCardVO>> similar(@PathVariable Long id,
                                            @RequestParam(defaultValue = "5") int size) {
        return Result.ok(noteManager.similarNotes(id, size));
    }
}

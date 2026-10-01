package org.icedamericanomall.user.adapter.seller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.KnowledgeVO;
import org.icedamericanomall.user.app.knowledge.KnowledgeBaseManager;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * V3.4: 商家知识库管理（Adapter → Manager 分层；报文形状与重构前一致）。
 */
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasRole('ROLE_SELLER')")
public class KnowledgeBaseController {

    private final KnowledgeBaseManager knowledgeBaseManager;

    @GetMapping("/knowledge")
    public Result<List<KnowledgeVO>> list(
            @RequestParam(required = false) String category) {
        return Result.ok(knowledgeBaseManager.listBySeller(UserContext.getUserId(), category));
    }

    @PostMapping("/knowledge")
    public Result<KnowledgeVO> create(@RequestBody KnowledgeVO req) {
        return Result.ok(knowledgeBaseManager.create(UserContext.getUserId(),
                req.getTitle(), req.getContent(), req.getCategory()));
    }

    @PutMapping("/knowledge/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody KnowledgeVO req) {
        boolean updated = knowledgeBaseManager.update(id, UserContext.getUserId(),
                req.getTitle(), req.getContent(), req.getCategory());
        return updated ? Result.ok() : Result.error(404, "知识条目不存在");
    }

    @DeleteMapping("/knowledge/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseManager.softDelete(id, UserContext.getUserId());
        return Result.ok();
    }
}

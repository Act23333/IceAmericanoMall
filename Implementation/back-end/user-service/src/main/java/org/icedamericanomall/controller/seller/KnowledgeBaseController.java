package org.icedamericanomall.controller.seller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.MerchantKnowledgeEntity;
import org.icedamericanomall.service.KnowledgeBaseService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * V3.4: 商家知识库管理（DDD: Controller→Service→Mapper）。
 */
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasRole('ROLE_SELLER')")
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @GetMapping("/knowledge")
    public Result<List<MerchantKnowledgeEntity>> list(
            @RequestParam(required = false) String category) {
        return Result.ok(kbService.listBySeller(UserContext.getUserId(), category));
    }

    @PostMapping("/knowledge")
    public Result<MerchantKnowledgeEntity> create(@RequestBody MerchantKnowledgeEntity entity) {
        entity.setSellerId(UserContext.getUserId());
        kbService.create(entity);
        return Result.ok(entity);
    }

    @PutMapping("/knowledge/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MerchantKnowledgeEntity req) {
        Long sellerId = UserContext.getUserId();
        MerchantKnowledgeEntity e = kbService.getById(id, sellerId);
        if (e == null) return Result.error(404, "知识条目不存在");
        if (req.getTitle() != null) e.setTitle(req.getTitle());
        if (req.getContent() != null) e.setContent(req.getContent());
        if (req.getCategory() != null) e.setCategory(req.getCategory());
        kbService.update(e);
        return Result.ok();
    }

    @DeleteMapping("/knowledge/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbService.softDelete(id, UserContext.getUserId());
        return Result.ok();
    }
}

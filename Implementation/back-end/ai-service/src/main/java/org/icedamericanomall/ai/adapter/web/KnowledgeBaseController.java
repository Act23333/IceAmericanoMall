package org.icedamericanomall.ai.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.ai.adapter.assembler.KnowledgeConvertor;
import org.icedamericanomall.ai.adapter.request.KnowledgeSaveReq;
import org.icedamericanomall.ai.adapter.request.KnowledgeUpdateReq;
import org.icedamericanomall.ai.adapter.response.KnowledgeVO;
import org.icedamericanomall.ai.app.knowledge.KnowledgeBaseManager;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.ForbiddenException;
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
 * 商家知识库管理（适配层，商家端入口）—— AI 客服 RAG 的知识源（{@code RAGSearchTool} 消费方），
 * 随客服域自 user-service 迁入（ADR-016 收尾；路径与报文形状不变）。
 *
 * <p>入参已对象化：新建用 {@link KnowledgeSaveReq}（title/content 必填）、
 * 局部更新用 {@link KnowledgeUpdateReq}（三个字段均可缺省）——原先直接以出参 VO 作
 * {@code @RequestBody} 的写法已消除；出参统一由 {@link KnowledgeConvertor} 装配为
 * {@link KnowledgeVO}（不再返回 {@code embeddingId}）。
 *
 * <p>角色守卫说明：本服务不引入 Spring Security（网关统一鉴权 + 请求头透传身份），
 * 卖家角色校验用 {@link UserContext#hasRole} 显式判断（语义等价于原 @PreAuthorize）。
 */
@RestController
@PreAuthorize("@ss.hasPermi('seller:knowledge')")
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseManager knowledgeBaseManager;
    private final KnowledgeConvertor knowledgeConvertor;

    /** 商家条目列表（可按分类筛选）。 */
    @GetMapping("/knowledge")
    public Result<List<KnowledgeVO>> list(
            @RequestParam(required = false) String category) {
        assertSeller();
        return Result.ok(knowledgeConvertor.toVOList(
                knowledgeBaseManager.listBySeller(UserContext.getUserId(), category)));
    }

    /** 新建条目。 */
    @PostMapping("/knowledge")
    public Result<KnowledgeVO> create(@RequestBody @Valid KnowledgeSaveReq req) {
        assertSeller();
        return Result.ok(knowledgeConvertor.toVO(knowledgeBaseManager.create(UserContext.getUserId(),
                req.getTitle(), req.getContent(), req.getCategory())));
    }

    /** 更新条目（仅限本人；局部更新，缺省字段不改）。 */
    @PutMapping("/knowledge/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid KnowledgeUpdateReq req) {
        assertSeller();
        boolean updated = knowledgeBaseManager.update(id, UserContext.getUserId(),
                req.getTitle(), req.getContent(), req.getCategory());
        return updated ? Result.ok() : Result.error(404, "知识条目不存在");
    }

    /** 软删除条目（仅限本人）。 */
    @DeleteMapping("/knowledge/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assertSeller();
        knowledgeBaseManager.softDelete(id, UserContext.getUserId());
        return Result.ok();
    }

    /** 卖家角色守卫（网关透传角色；非卖家一律拒绝）。 */
    private void assertSeller() {
        if (!UserContext.hasRole("ROLE_SELLER")) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "仅商家可操作知识库");
        }
    }
}

package org.icedamericanomall.ai.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 商家知识库新建入参（适配层）—— {@code POST /api/seller/knowledge}。
 *
 * <p>此前该端点直接用出参 VO（{@code KnowledgeVO}）作 {@code @RequestBody}：既不满足
 * 「入参对象化 *Req」规范，也把 embeddingId 等出参字段暴露为可写字段。现独立为入参对象，
 * JSON 字段名（{@code title}/{@code content}/{@code category}）保持不变。
 *
 * <p>校验口径对齐 05-API-Specification CH11：{@code title}、{@code content} 必填，
 * {@code category} 可选。
 */
@Data
public class KnowledgeSaveReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 条目标题。 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题长度不能超过255字符")
    private String title;

    /** 条目内容（AI 客服知识源）。 */
    @NotBlank(message = "内容不能为空")
    private String content;

    /** 分类（可选）。 */
    @Size(max = 64, message = "分类长度不能超过64字符")
    private String category;
}

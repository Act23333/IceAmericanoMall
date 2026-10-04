package org.icedamericanomall.ai.adapter.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 商家知识库更新入参（适配层）—— {@code PUT /api/seller/knowledge/{id}}。
 *
 * <p>更新语义为<b>局部更新</b>（05-API-Specification CH12：{@code { title?, content?, category? }}）：
 * 三个字段均可缺省，只更新非 null 字段，故此处不加 {@code @NotBlank}（与新建入参
 * {@link KnowledgeSaveReq} 分开定义，避免用校验分组引入额外包结构）。
 */
@Data
public class KnowledgeUpdateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 条目标题（缺省则不改）。 */
    @Size(max = 255, message = "标题长度不能超过255字符")
    private String title;

    /** 条目内容（缺省则不改）。 */
    private String content;

    /** 分类（缺省则不改）。 */
    @Size(max = 64, message = "分类长度不能超过64字符")
    private String category;
}

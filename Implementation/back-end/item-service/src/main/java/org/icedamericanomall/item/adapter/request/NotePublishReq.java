package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.icedamericanomall.item.domain.model.content.Note;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 发布种草笔记请求（P28）—— 条数/长度上限统一引用聚合常量（单一口径）；
 * 图集 URL 合法性与去重后条数由聚合工厂/应用层校验（单元测试口径）。
 */
@Data
public class NotePublishReq implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "标题不能为空")
    @Size(max = Note.TITLE_MAX_LEN, message = "标题长度不能超过50")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = Note.CONTENT_MAX_LEN, message = "正文长度不能超过2000")
    private String content;

    /** 封面图 URL（可空）。 */
    private String coverImage;

    /** 图集（≤9 个 URL，可空）。 */
    @Size(max = Note.MAX_IMAGES, message = "图片数量不能超过9")
    private List<String> images;

    /**
     * 关联商品（SPU 业务键 32-hex，≤5，服务端去重；可空）。
     *
     * <p><b>标识口径（ADR-061 §决策 2）</b>：入参是 {@code spu.spu_id} 的 32-hex 形态，
     * 与 {@code SpuVO.spuId} 同型同义；应用层解析为本域技术键后落 {@code note_spu}（表结构不动）。
     */
    @Size(max = Note.MAX_SPUS, message = "关联商品数量不能超过5")
    private List<String> spuIds;
}

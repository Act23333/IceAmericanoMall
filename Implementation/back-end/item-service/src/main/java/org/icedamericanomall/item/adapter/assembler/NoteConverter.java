package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.item.adapter.response.NoteCardVO;
import org.icedamericanomall.item.adapter.response.NoteDetailVO;
import org.icedamericanomall.item.adapter.response.NoteVO;
import org.icedamericanomall.item.domain.model.content.Note;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 种草笔记装配器（MapStruct）—— 领域聚合 → 视图对象；
 * {@code userId → authorId} 语义换名在此收口，{@code liked}/{@code products} 为
 * 登录视角/跨聚合装配字段，由 {@code NoteManager} 装配后填入。
 */
@Mapper(componentModel = "spring")
public interface NoteConverter {

    /** 发布结果卡（P28）：仅回填 id。 */
    NoteVO toVO(Note note);

    /** 流轻卡片（P29）：不含正文。 */
    @Mapping(target = "authorId", source = "userId")
    NoteCardVO toCardVO(Note note);

    /** 详情（P30）：liked/products 由应用层装配（登录视角 + 在售过滤）。 */
    @Mapping(target = "authorId", source = "userId")
    @Mapping(target = "liked", ignore = true)
    @Mapping(target = "products", ignore = true)
    NoteDetailVO toDetailVO(Note note);
}

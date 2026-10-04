package org.icedamericanomall.ai.adapter.assembler;

import org.icedamericanomall.ai.adapter.response.KnowledgeVO;
import org.icedamericanomall.ai.app.dto.KnowledgeDTO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商家知识库装配器（适配层转换器）—— 应用层 DTO → 视图对象。
 *
 * <p>出参 VO 的字段拷贝只在本转换器完成（控制器不手写转换）；
 * {@code embeddingId} 属向量库内部句柄，不进入 VO。
 */
@Component
public class KnowledgeConvertor {

    /**
     * 知识条目 DTO → 视图对象。
     *
     * @param dto 应用层 DTO
     * @return 视图对象；入参为 null 时返回 null
     */
    public KnowledgeVO toVO(KnowledgeDTO dto) {
        if (dto == null) {
            return null;
        }
        KnowledgeVO vo = new KnowledgeVO();
        vo.setId(dto.getId());
        vo.setSellerId(dto.getSellerId());
        vo.setTitle(dto.getTitle());
        vo.setContent(dto.getContent());
        vo.setCategory(dto.getCategory());
        vo.setStatus(dto.getStatus());
        vo.setCreateTime(dto.getCreateTime());
        vo.setUpdateTime(dto.getUpdateTime());
        return vo;
    }

    /** 知识条目 DTO 列表 → 视图对象列表。 */
    public List<KnowledgeVO> toVOList(List<KnowledgeDTO> dtoList) {
        return dtoList == null ? List.of() : dtoList.stream().map(this::toVO).toList();
    }
}

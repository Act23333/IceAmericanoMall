package org.icedamericanomall.item.infrastructure.persistence.content;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.model.content.Note;
import org.icedamericanomall.item.domain.repository.NoteRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 种草笔记仓储实现 —— MyBatis-Plus 访问 {@code note} 表；PO↔聚合转换内联（ADR-038）。
 *
 * <p>图集在聚合内是 {@code List<String>}、表内是 JSON 数组字符串列（ADR-048：不引入
 * TypeHandler），序列化/反序列化由本类显式承担；点赞计数增减委托 Mapper 单条原子 SQL。
 */
@Repository
@RequiredArgsConstructor
public class NoteRepositoryImpl implements NoteRepository {

    private final NoteMapper noteMapper;

    @Override
    public void save(Note note) {
        NotePO po = toPO(note);
        noteMapper.insert(po);
        note.setId(po.getId());
    }

    @Override
    public Note findById(Long noteId) {
        NotePO po = noteMapper.selectById(noteId);
        return po == null ? null : toDomain(po);
    }

    @Override
    public List<Note> findAllByIds(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return List.of();
        }
        return noteMapper.selectList(new LambdaQueryWrapper<NotePO>()
                        .in(NotePO::getId, noteIds))
                .stream().map(NoteRepositoryImpl::toDomain).toList();
    }

    @Override
    public IPage<Note> pagePublished(int page, int size) {
        return noteMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<NotePO>()
                        .eq(NotePO::getStatus, Note.STATUS_PUBLISHED)
                        .orderByDesc(NotePO::getCreateTime))
                .convert(NoteRepositoryImpl::toDomain);
    }

    @Override
    public int incrementLikeCount(Long noteId) {
        return noteMapper.incrementLikeCount(noteId);
    }

    @Override
    public int decrementLikeCount(Long noteId) {
        return noteMapper.decrementLikeCount(noteId);
    }

    @Override
    public void updateStatus(Long noteId, Integer status) {
        noteMapper.update(null, new LambdaUpdateWrapper<NotePO>()
                .eq(NotePO::getId, noteId)
                .set(NotePO::getStatus, status));
    }

    // ==================== PO ↔ 聚合（内联转换） ====================

    static NotePO toPO(Note note) {
        NotePO po = new NotePO();
        po.setId(note.getId());
        po.setUserId(note.getUserId());
        po.setTitle(note.getTitle());
        po.setContent(note.getContent());
        po.setCoverImage(note.getCoverImage());
        po.setImages(toJson(note.getImages()));
        po.setStatus(note.getStatus());
        po.setLikeCount(note.getLikeCount());
        return po;
    }

    static Note toDomain(NotePO po) {
        return Note.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .title(po.getTitle())
                .content(po.getContent())
                .coverImage(po.getCoverImage())
                .images(parseImages(po.getImages()))
                .status(po.getStatus())
                .likeCount(po.getLikeCount())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    /** 图集列表 → JSON 数组字符串（空/null 落 NULL，走列默认）。 */
    private static String toJson(List<String> images) {
        return (images == null || images.isEmpty()) ? null : JSONUtil.toJsonStr(images);
    }

    /** JSON 数组字符串 → 图集列表（空/脏数据防御为空列表）。 */
    private static List<String> parseImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return JSONUtil.toList(json, String.class);
    }
}

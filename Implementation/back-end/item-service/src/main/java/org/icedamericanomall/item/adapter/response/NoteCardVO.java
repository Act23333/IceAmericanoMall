package org.icedamericanomall.item.adapter.response;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 笔记流卡片视图对象（P29）—— 轻卡片不含正文（流性能口径）；
 * 按 status=1、发布时间倒序分页返回。
 */
@Data
public class NoteCardVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String coverImage;

    private Integer likeCount;

    /** 作者 user_id。 */
    private Long authorId;

    private LocalDateTime createTime;
}

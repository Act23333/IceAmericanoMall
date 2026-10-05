package org.icedamericanomall.item.adapter.response;

import java.io.Serializable;

import lombok.Data;

/**
 * 笔记发布结果视图对象（P28）—— 契约仅含回填技术主键 {@code id}（发布即上架，无草稿）。
 */
@Data
public class NoteVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
}

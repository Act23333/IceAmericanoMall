package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品分类视图对象。
 */
@Data
public class CategoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Long parentId;
    private Integer level;
    private Integer sortOrder;
}

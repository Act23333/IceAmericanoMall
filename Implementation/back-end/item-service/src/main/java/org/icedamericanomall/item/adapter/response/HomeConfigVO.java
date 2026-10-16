package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 首页配置视图对象。
 */
@Data
public class HomeConfigVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String slot;
    private Integer type;
    private String title;
    private String spuIds;
    private String image;
    private String linkUrl;
    private Integer sortOrder;
    private Integer status;
}

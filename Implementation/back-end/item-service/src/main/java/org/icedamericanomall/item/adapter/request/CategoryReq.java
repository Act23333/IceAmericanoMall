package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增/修改商品分类请求。id 由路径变量提供，不参与请求体。
 */
@Data
public class CategoryReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50")
    private String name;

    private Long parentId;

    private Integer level;

    private Integer sortOrder;
}

package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增/修改首页配置请求。id 由路径变量提供，不参与请求体。
 */
@Data
public class HomeConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "位置标识不能为空")
    @Size(max = 30, message = "位置标识长度不能超过30")
    private String slot;

    @NotNull(message = "配置类型不能为空")
    private Integer type;

    @Size(max = 100, message = "标题长度不能超过100")
    private String title;

    @Size(max = 500, message = "商品ID列表长度不能超过500")
    private String productIds;

    private String image;

    private String linkUrl;

    private Integer sortOrder;

    private Integer status;
}

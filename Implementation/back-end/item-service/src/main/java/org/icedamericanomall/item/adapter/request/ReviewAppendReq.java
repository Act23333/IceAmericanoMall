package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户追评请求。
 */
@Data
public class ReviewAppendReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "追评内容不能为空")
    @Size(max = 500, message = "追评内容长度不能超过500")
    private String content;

    /** 追评图片，逗号分隔。 */
    @Size(max = 1000, message = "追评图片长度不能超过1000")
    private String mediaUrls;
}

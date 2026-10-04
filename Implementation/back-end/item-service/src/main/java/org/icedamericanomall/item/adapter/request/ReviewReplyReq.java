package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家回复评价请求。
 */
@Data
public class ReviewReplyReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容长度不能超过500")
    private String content;
}

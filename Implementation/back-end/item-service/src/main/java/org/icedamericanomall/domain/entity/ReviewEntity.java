package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("review")
public class ReviewEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    private Long orderId;
    private Long skuId;
    private Integer rating;
    private String content;
    private String images;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

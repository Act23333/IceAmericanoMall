package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("flash_sale")
public class FlashSaleEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long skuId;
    private Integer flashPrice;
    private Integer stock;
    private Integer soldCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

package org.icedamericanomall.user.infrastructure.persistence.favorite;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("favorite")
public class FavoriteDO implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

package org.icedamericanomall.user.infrastructure.persistence.history;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("spu_view_log")
public class SpuViewLogPO implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long spuId;
    private LocalDateTime viewTime;
}

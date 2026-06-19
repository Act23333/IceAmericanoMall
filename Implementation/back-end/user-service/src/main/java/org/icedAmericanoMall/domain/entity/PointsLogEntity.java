package org.icedAmericanoMall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("points_log")
public class PointsLogEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer points;
    /** 1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期 */
    private Integer type;
    private String source;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

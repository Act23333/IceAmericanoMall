package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("settlement")
public class SettlementEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String settlementNo;
    private Long sellerId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer orderCount;
    private Integer totalAmount;
    private Integer commission;
    private Integer settlementAmount;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

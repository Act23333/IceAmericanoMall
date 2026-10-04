package org.icedamericanomall.marketing.infrastructure.persistence.flash;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀活动表映射对象（PO）—— {@code flash_sale} 表，仅存在于基础设施持久化层。
 *
 * <p>{@code version} 为乐观锁列：异步统计 soldCount 时以版本号比对，冲突即重试。
 */
@Data
@TableName("flash_sale")
public class FlashSalePO implements Serializable {
    @Serial
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

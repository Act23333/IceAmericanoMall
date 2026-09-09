package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * V4.1: 标记废弃。秒杀订单已统一走 trade-service 的 orders 表（order_type=3）。
 * 本实体保留仅用于 flash_order 表的存量数据查询，新代码不应使用。
 *
 * @deprecated 请使用 trade-service 的 OrderEntity + orderType=FLASH_SALE。
 */
@Deprecated
@Data
@TableName("flash_order")
public class FlashOrderEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long flashId;
    private Long userId;
    private Long skuId;
    private Integer quantity;
    private Integer flashPrice;
    private Integer status;
    private String failReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

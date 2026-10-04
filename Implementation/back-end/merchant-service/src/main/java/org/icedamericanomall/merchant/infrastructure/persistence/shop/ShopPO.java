package org.icedamericanomall.merchant.infrastructure.persistence.shop;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@code shop} 表映射对象（PO）—— 仅存在于基础设施持久化层，不外泄。
 *
 * <p>{@code status} 以原始类型映射（0=审核中，1=正常，2=冻结），
 * 领域枚举转换在 {@link ShopConverter} 完成。
 */
@Data
@TableName("shop")
public class ShopPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属商家 ID（{@code seller.id}） */
    private Long merchantId;

    private String shopName;

    private String shopLogo;

    private String contactPhone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    /** 状态：0=审核中，1=正常，2=冻结（ShopStatusEnum.code） */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

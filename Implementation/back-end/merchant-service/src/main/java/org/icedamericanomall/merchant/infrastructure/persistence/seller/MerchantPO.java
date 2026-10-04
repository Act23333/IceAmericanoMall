package org.icedamericanomall.merchant.infrastructure.persistence.seller;

import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@code seller} 表映射对象（PO）—— 仅存在于基础设施持久化层，不外泄。
 *
 * <p>V5.4（ADR-019）后 {@code seller} 只承载商家身份 + 生命周期，
 * 店铺资料列已迁到 {@code shop} 表（{@code ShopPO}）。
 * {@code status} 以原始类型映射（0=审核中，1=正常，2=冻结），
 * 领域枚举转换在 {@link MerchantConverter} 完成。
 */
@Data
@TableName("seller")
public class MerchantPO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 状态：0=审核中，1=正常，2=冻结（MerchantStatusEnum.code） */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

package org.icedamericanomall.user.infrastructure.persistence.seller;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@code seller} 表映射对象（DO）—— 仅存在于基础设施持久化层，不外泄。
 *
 * <p>{@code status} 以原始类型映射（0=审核中，1=正常，2=冻结），
 * 领域枚举转换在 {@link SellerConverter} 完成。
 */
@Data
@TableName("seller")
public class SellerDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String shopName;

    private String shopLogo;

    private String contactPhone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    /** 状态：0=审核中，1=正常，2=冻结（SellerStatusEnum.code） */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

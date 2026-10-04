package org.icedamericanomall.user.infrastructure.persistence.address;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收货地址持久化对象（{@code address} 表映射）。
 *
 * <p>仅限基础设施层使用，禁止泄漏到 Controller；领域侧对应 {@code domain.model.Address}，
 * 互转见 {@code AddressConverter}。敏感字段（电话）按三字段模型存储的落地口径随所属表定义。
 */

@Data
@TableName("address")
public class AddressPO {
    @TableId(type = IdType.AUTO)
    private Long id;                     // 技术主键(自增)

    private Long userId;                 // 所属用户ID

    private String receiver;             // 收货人姓名

    private String phone;                // 联系电话

    private String province;             // 省

    private String city;                 // 市

    private String district;             // 区/县

    private String street;               // 街道/镇

    private String detail;               // 详细地址（门牌号等）
    @TableField("is_default")
    private Boolean defaulted;           // 是否默认地址：1-是，0-否

    private String label;                // 地址标签（如“家”、“公司”）

    private BigDecimal longitude;        // 经度（可选）

    private BigDecimal latitude;         // 纬度（可选）

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;    // 创建时间

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;    // 更新时间
}

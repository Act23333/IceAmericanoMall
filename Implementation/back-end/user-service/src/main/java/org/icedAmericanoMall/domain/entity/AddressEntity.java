package org.icedAmericanoMall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @ClassName: Address
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/25 19:21
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.domain.entity
 */

@Data
@TableName("address")
public class AddressEntity {
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

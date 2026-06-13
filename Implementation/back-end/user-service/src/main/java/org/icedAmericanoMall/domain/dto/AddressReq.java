package org.icedAmericanoMall.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.icedAmericanoMall.group.CreateGroup;
import org.icedAmericanoMall.group.UpdateGroup;

import java.math.BigDecimal;

@Data
public class AddressReq {
    @NotNull(groups = UpdateGroup.class, message = "地址ID不能为空")
    private Long id;   // 修改时必传，新增时可选

    // 所属用户ID（通常从登录上下文获取，不依赖前端传递，但保留字段方便测试）
    private Long userId;

    @NotBlank(groups = {CreateGroup.class, UpdateGroup.class}, message = "收货人不能为空")
    private String receiver;

    @NotBlank(message = "联系电话不能为空")
    private String phone;

    @NotBlank(message = "省份不能为空")
    private String province;

    @NotBlank(message = "城市不能为空")
    private String city;

    @NotBlank(message = "区/县不能为空")
    private String district;

    @NotBlank(message = "街道/镇不能为空")
    private String street;

    @NotBlank(message = "详细地址不能为空")
    private String detail;

    private Boolean defaulted;   // 是否默认地址：1-是，0-否

    private String label;        // 地址标签（如“家”、“公司”）

    private BigDecimal longitude; // 经度（可选）

    private BigDecimal latitude;  // 纬度（可选）
}

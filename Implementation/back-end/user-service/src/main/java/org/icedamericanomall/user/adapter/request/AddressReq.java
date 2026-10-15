package org.icedamericanomall.user.adapter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.icedamericanomall.user.adapter.group.CreateGroup;
import org.icedamericanomall.user.adapter.group.UpdateGroup;

import java.math.BigDecimal;

/**
 * 收货地址请求体（契约 §4.2.2 U7/U9 —— {@code AddressReq { receiver, phone, province,
 * city, district, street, detail, defaulted?, label?, longitude?, latitude? }}）。
 *
 * <p><b>不含 {@code id}</b>：修改端点的地址 id 由 path（{@code PUT /update/{id}}）提供。
 * 历史实现曾对 {@code id} 标 {@code @NotNull(groups = UpdateGroup)}，导致按契约（body 无 id）
 * 实现的客户端一律 400；已按契约移除。
 *
 * <p><b>必填项显式归类</b>：两个端点分别以 {@code CreateGroup}/{@code UpdateGroup} 校验，
 * 未标 groups 的约束属 Default 组、不会被校验；故六个必填字段均显式归入两个组
 * （否则等于没有校验）。
 */
@Data
public class AddressReq {

    /** 地址 ID —— 仅作透传保留（修改时以 path 为准），**不再要求 body 携带**。 */
    private Long id;

    // 所属用户ID（通常从登录上下文获取，不依赖前端传递，但保留字段方便测试）
    private Long userId;

    @NotBlank(groups = {CreateGroup.class, UpdateGroup.class}, message = "收货人不能为空")
    private String receiver;

    @NotBlank(groups = {CreateGroup.class, UpdateGroup.class}, message = "联系电话不能为空")
    private String phone;

    @NotBlank(groups = {CreateGroup.class, UpdateGroup.class}, message = "省份不能为空")
    private String province;

    @NotBlank(groups = {CreateGroup.class, UpdateGroup.class}, message = "城市不能为空")
    private String city;

    @NotBlank(groups = {CreateGroup.class, UpdateGroup.class}, message = "区/县不能为空")
    private String district;

    @NotBlank(groups = {CreateGroup.class, UpdateGroup.class}, message = "街道/镇不能为空")
    private String street;

    @NotBlank(groups = {CreateGroup.class, UpdateGroup.class}, message = "详细地址不能为空")
    private String detail;

    private Boolean defaulted;   // 是否默认地址：1-是，0-否

    private String label;        // 地址标签（如“家”、“公司”）

    private BigDecimal longitude; // 经度（可选）

    private BigDecimal latitude;  // 纬度（可选）
}

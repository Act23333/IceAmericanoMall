package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.domain.model.address.Address;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收货地址响应 VO（JSON 报文与重构前逐字段一致，{@code updateTime} 除外 —— 见下）。
 *
 * <p><b>字段收缩（V5.4）</b>：删除 {@code updateTime}。裁决依据：前端全仓对该字段
 * <b>只声明不消费</b> —— {@code Implementation/front-end/packages/api/types/index.ts} 的
 * {@code AddressResp} 接口声明了 {@code updateTime}，但 {@code apps/web}、{@code apps/miniprogram}
 * 与 {@code packages/**} 中<b>无任何读取点</b>（无 {@code .updateTime} 访问、无解构取值）。
 * 按安全模型 12.3「最小暴露面」收窄出参，删除不产生任何已证实的消费方影响。
 * 领域模型 {@code Address} 仍保留 {@code updateTime}（内部审计口径不变），仅出参不再投影。
 */
@Data
public class AddressResp {

    /** 地址唯一标识 */
    private Long id;

    /** 所属用户ID（技术主键口径） */
    private Long userId;

    private String receiver;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String street;

    private String detail;

    /** 是否默认地址：1-是，0-否 */
    private Boolean defaulted;

    /** 地址标签（如"家"、"公司"） */
    private String label;

    /** 经度（可选） */
    private BigDecimal longitude;

    /** 纬度（可选） */
    private BigDecimal latitude;

    private LocalDateTime createTime;

    /** 领域模型 → 响应 VO。 */
    public static AddressResp from(Address address) {
        AddressResp resp = new AddressResp();
        resp.setId(address.getId());
        resp.setUserId(address.getUserId());
        resp.setReceiver(address.getReceiver());
        resp.setPhone(address.getPhone());
        resp.setProvince(address.getProvince());
        resp.setCity(address.getCity());
        resp.setDistrict(address.getDistrict());
        resp.setStreet(address.getStreet());
        resp.setDetail(address.getDetail());
        resp.setDefaulted(address.getDefaulted());
        resp.setLabel(address.getLabel());
        resp.setLongitude(address.getLongitude());
        resp.setLatitude(address.getLatitude());
        resp.setCreateTime(address.getCreateTime());
        return resp;
    }
}

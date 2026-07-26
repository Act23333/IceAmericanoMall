package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AddressResp {
    private Long id;               // 地址唯一标识（雪花ID）
    /**
     * 多用户管理场景
     * 如果系统支持管理员查看所有用户的地址（如后台管理），或一个账号可关联多个子账户（如家庭组），那么前端需要知道每条地址属于哪个用户，这时 userId 是必要的。
     *
     * 前端本地缓存与关联
     * 有些前端框架可能需要将地址数据与用户信息做本地关联（例如用户切换时快速过滤地址列表），此时 userId 可以作为过滤标识。
     *
     * 调试与日志
     * 在开发环境下，前端开发人员通过响应直接看到 userId 便于调试归属关系
     */
    private Long userId;           // 所属用户业务ID
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String street;
    private String detail;
    private Boolean defaulted;
    private String label;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
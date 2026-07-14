package org.icedAmericanoMall.domain.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import org.icedAmericanoMall.constants.UserStatusEnum;

import java.time.LocalDateTime;

/**
 * @ClassName: User
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/25 17:16
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.domain.entity
 */
@Data
@TableName("user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;                     // 技术主键

    private String userId;               // 业务唯一标识

    private String username;             // 用户名（可选）

    private String phone;                // 手机号（登录账号）

    private String password;             // 加密密码

    private String avatar;               // 头像URL

    private String wxOpenid;             // 微信 openid（第三方登录，可空）

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime registerTime;  // 注册时间（业务时间）

    private LocalDateTime lastLoginTime; // 最近登录时间（首次/回归奖励判断，V2.5）

    private UserStatusEnum status;              // 状态：1-正常，0-禁用

    private Integer balance;             // 余额（单位：分）

    /** 0=ROLE_USER, 1=ROLE_SELLER, 2=ROLE_ADMIN */
    private Integer roleType;            // 角色类型

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;    // 记录创建时间（自动填充）

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;    // 最后更新时间（自动填充）
}

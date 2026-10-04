package org.icedamericanomall.auth.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建角色请求（{@code POST /api/admin/roles}，AD31）。
 *
 * <p>报文契约与迁出前一致：{@code { "name", "code", "description" }}（JSON 键名不变，
 * 替换原先的 {@code Map<String,Object>} 裸入参）。
 */
@Data
public class RoleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称（{@code sys_role.name}，NOT NULL） */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过64")
    private String name;

    /** 角色编码（{@code sys_role.code}，NOT NULL + UNIQUE） */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过64")
    private String code;

    /** 角色描述（可空） */
    @Size(max = 256, message = "角色描述长度不能超过256")
    private String description;
}

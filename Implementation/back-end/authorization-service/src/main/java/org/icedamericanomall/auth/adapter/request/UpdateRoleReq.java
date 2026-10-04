package org.icedamericanomall.auth.adapter.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新角色请求（{@code PUT /api/admin/roles/{id}}，AD32）。
 *
 * <p>报文契约与迁出前一致：{@code { "name"?, "description"? }} —— 两字段均为<b>可选</b>
 * （部分更新语义，见 05-API-Specification AD32）。角色编码不可更新，故本对象不含 {@code code}
 * （客户端若仍携带 {@code code}，按未知字段忽略，与迁出前 {@code Map} 入参行为一致）。
 */
@Data
public class UpdateRoleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称（可选，{@code null} 表示不更新） */
    @Size(max = 64, message = "角色名称长度不能超过64")
    private String name;

    /** 角色描述（可选，允许置空串以清空描述） */
    @Size(max = 256, message = "角色描述长度不能超过256")
    private String description;
}

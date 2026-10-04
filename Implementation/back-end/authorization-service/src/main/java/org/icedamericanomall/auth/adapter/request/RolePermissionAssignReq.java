package org.icedamericanomall.auth.adapter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 角色权限分配请求（{@code POST /api/admin/roles/{id}/permissions}，AD35）。
 *
 * <p>报文契约与迁出前一致：{@code { "permissionIds": [1,2,3] }}（覆盖式分配）。
 * 校验口径与迁出前一致：仅拒绝 {@code null}（缺字段），空数组合法（表示清空该角色权限）。
 */
@Data
public class RolePermissionAssignReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 权限 ID 列表（覆盖式；空数组表示清空） */
    @NotNull(message = "permissionIds 不能为空")
    private List<Long> permissionIds;
}

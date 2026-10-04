package org.icedamericanomall.auth.app.rbac;

import org.icedamericanomall.auth.domain.model.rbac.Permission;
import org.icedamericanomall.auth.domain.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.Mockito.when;

/**
 * 权限查询单元测试 —— 平铺列表转树（parent_id 组装 / 孤儿挂根）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionQueryManager 权限树单元测试")
class PermissionQueryManagerTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private PermissionQueryManager queryManager;

    private Permission perm(long id, Long parentId, String name) {
        Permission p = new Permission();
        p.setId(id);
        p.setName(name);
        p.setParentId(parentId);
        p.setCode("perm:" + id);
        p.setType(1);
        p.setSortOrder((int) id);
        p.setStatus(1);
        return p;
    }

    @Test
    @DisplayName("权限树 — 子权限挂到父节点，顶层仅剩无父节点")
    void shouldBuildTree_whenParentExists() {
        when(roleRepository.findAllPermissions()).thenReturn(List.of(
                perm(1, null, "订单管理"),
                perm(2, 1L, "订单详情"),
                perm(3, null, "商品管理")));

        List<Permission> tree = queryManager.listPermissionTree();

        assertEquals(2, tree.size(), "顶层只剩两个根节点");
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("订单详情", tree.get(0).getChildren().get(0).getName());
    }

    @Test
    @DisplayName("权限树 — parent 指向不存在的节点 → 孤儿挂根（不丢权限）")
    void shouldAttachOrphanToRoot_whenParentMissing() {
        when(roleRepository.findAllPermissions()).thenReturn(List.of(
                perm(1, 999L, "孤儿权限")));

        List<Permission> tree = queryManager.listPermissionTree();

        assertEquals(1, tree.size());
        assertEquals("孤儿权限", tree.get(0).getName());
    }
}

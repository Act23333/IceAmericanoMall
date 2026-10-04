package org.icedamericanomall.logistics.infrastructure.persistence;

import org.icedamericanomall.logistics.domain.enums.LogisticsStatusEnum;
import org.icedamericanomall.logistics.domain.model.logistics.Package;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PackageRepositoryImpl 单元测试 —— PO ↔ 聚合映射与查询委托（全部走标准 BaseMapper 方法）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PackageRepositoryImpl 测试")
class PackageRepositoryImplTest {

    @Mock
    private OrderLogisticsMapper mockMapper;

    private PackageRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new PackageRepositoryImpl(mockMapper);
    }

    private OrderLogisticsPO po(int packageNo, Integer status) {
        OrderLogisticsPO po = new OrderLogisticsPO();
        po.setId((long) packageNo);
        po.setOrderId(1L);
        po.setPackageNo(packageNo);
        po.setLogisticsNumber("SF123456");
        po.setLogisticsCompany("顺丰快递");
        po.setStatus(status);
        return po;
    }

    @Test
    @DisplayName("findByOrderId — 返回该订单全部包裹（映射为聚合，按包裹号升序）")
    void shouldReturnAllPackages_whenOrderHasMultiplePackages() {
        when(mockMapper.selectList(any())).thenReturn(List.of(po(1, 2), po(2, 1)));

        List<Package> result = repository.findByOrderId(1L);

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getPackageNo());
        assertEquals(2, result.get(1).getPackageNo());
        assertEquals("SF123456", result.get(0).getLogisticsNumber());
    }

    @Test
    @DisplayName("findByOrderId — 无包裹时返回空列表（不抛异常）")
    void shouldReturnEmptyList_whenNoPackage() {
        when(mockMapper.selectList(any())).thenReturn(List.of());

        assertTrue(repository.findByOrderId(1L).isEmpty());
    }

    @Test
    @DisplayName("findByOrderIdAndPackageNo — 命中时返回聚合")
    void shouldReturnPackage_whenPackageExists() {
        when(mockMapper.selectOne(any())).thenReturn(po(2, LogisticsStatusEnum.SHIPPED.getCode()));

        Optional<Package> result = repository.findByOrderIdAndPackageNo(1L, 2);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().getPackageNo());
        assertEquals("SF123456", result.get().getLogisticsNumber());
    }

    @Test
    @DisplayName("findByOrderIdAndPackageNo — 不存在时返回 empty")
    void shouldReturnEmpty_whenPackageNotFound() {
        when(mockMapper.selectOne(any())).thenReturn(null);

        assertTrue(repository.findByOrderIdAndPackageNo(999L, 1).isEmpty());
    }

    @Test
    @DisplayName("existsByOrderIdAndPackageNo — selectCount 结果判定")
    void shouldCheckExistenceByCount() {
        when(mockMapper.selectCount(any())).thenReturn(1L);
        assertTrue(repository.existsByOrderIdAndPackageNo(1L, 1));

        when(mockMapper.selectCount(any())).thenReturn(0L);
        assertFalse(repository.existsByOrderIdAndPackageNo(1L, 1));
    }

    @Test
    @DisplayName("save — 聚合字段完整映射为 PO 后 insert")
    void shouldInsertPo_whenSave() {
        Package pkg = Package.create(1L, 2, "SF123456", "顺丰快递", "张三", "13800000000",
                "浙江", "杭州", "西湖区", "文一西路", "1号楼");

        repository.save(pkg);

        ArgumentCaptor<OrderLogisticsPO> captor = ArgumentCaptor.forClass(OrderLogisticsPO.class);
        verify(mockMapper).insert(captor.capture());
        OrderLogisticsPO po = captor.getValue();
        assertEquals(1L, po.getOrderId());
        assertEquals(2, po.getPackageNo());
        assertEquals(LogisticsStatusEnum.PENDING.getCode(), po.getStatus());
        assertEquals("张三", po.getContact());
        assertEquals("1号楼", po.getDetail());
    }

    @Test
    @DisplayName("update — 状态迁移结果经 updateById 落库")
    void shouldUpdatePo_whenUpdate() {
        Package pkg = Package.create(1L, 1, "SF123456", "顺丰快递", null, null,
                null, null, null, null, null);
        pkg.transitTo(LogisticsStatusEnum.SHIPPED);

        repository.update(pkg);

        ArgumentCaptor<OrderLogisticsPO> captor = ArgumentCaptor.forClass(OrderLogisticsPO.class);
        verify(mockMapper).updateById(captor.capture());
        assertEquals(LogisticsStatusEnum.SHIPPED.getCode(), captor.getValue().getStatus());
    }
}

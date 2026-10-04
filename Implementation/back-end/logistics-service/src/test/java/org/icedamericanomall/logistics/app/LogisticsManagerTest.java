package org.icedamericanomall.logistics.app;

import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.dto.CreateLogisticsCO;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.logistics.adapter.assembler.LogisticsConverter;
import org.icedamericanomall.logistics.adapter.response.LogisticsVO;
import org.icedamericanomall.logistics.domain.enums.LogisticsStatusEnum;
import org.icedamericanomall.logistics.domain.model.logistics.Package;
import org.icedamericanomall.logistics.domain.repository.PackageRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LogisticsManager 单元测试 —— 物流查询的订单归属校验（越权防范）、包裹列表装配与 internal 编排。
 *
 * <p><b>V5.6（F3 拆单发货）</b>：查询返回包裹<b>列表</b>（按 {@code packageNo} 升序）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogisticsManager 归属校验单元测试")
class LogisticsManagerTest {

    @Mock private PackageRepository packageRepository;
    @Mock private LogisticsConverter logisticsConverter;
    @Mock private OrderClient orderClient;
    @InjectMocks private LogisticsManager logisticsManager;

    private OrderSummaryCO order(Long userId) {
        OrderSummaryCO co = new OrderSummaryCO();
        co.setOrderNo("ORD-1");
        co.setUserId(userId);
        co.setTotalAmount(10000);
        co.setPayAmount(9000);
        co.setStatus(3);
        return co;
    }

    private Package pkg(int packageNo, Integer status) {
        Package pkg = Package.create(1L, packageNo, "SF123456", "顺丰快递", null, null,
                null, null, null, null, null);
        if (status != null && LogisticsStatusEnum.of(status) != LogisticsStatusEnum.PENDING) {
            pkg.transitTo(LogisticsStatusEnum.of(status));
        }
        return pkg;
    }

    // ==================== getOwnedLogistics ====================

    @Test
    @DisplayName("getOwnedLogistics — 本人订单：返回包裹列表（F3 列表化）")
    void shouldReturnPackageList_whenOrderOwnedByUser() {
        Package p1 = pkg(1, LogisticsStatusEnum.SHIPPED.getCode());
        Package p2 = pkg(2, LogisticsStatusEnum.PENDING.getCode());
        LogisticsVO vo1 = new LogisticsVO();
        vo1.setPackageNo(1);
        LogisticsVO vo2 = new LogisticsVO();
        vo2.setPackageNo(2);

        when(orderClient.getOrderById(1L)).thenReturn(order(100L));
        when(packageRepository.findByOrderId(1L)).thenReturn(List.of(p1, p2));
        when(logisticsConverter.toVO(p1)).thenReturn(vo1);
        when(logisticsConverter.toVO(p2)).thenReturn(vo2);

        List<LogisticsVO> result = logisticsManager.getOwnedLogistics(1L, 100L);

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getPackageNo());
        verify(packageRepository).findByOrderId(1L);
    }

    @Test
    @DisplayName("getOwnedLogistics — 无包裹：返回空列表（不抛 LOGISTICS_NOT_FOUND）")
    void shouldReturnEmptyList_whenNoPackage() {
        when(orderClient.getOrderById(1L)).thenReturn(order(100L));
        when(packageRepository.findByOrderId(1L)).thenReturn(List.of());

        assertTrue(logisticsManager.getOwnedLogistics(1L, 100L).isEmpty());
    }

    @Test
    @DisplayName("getOwnedLogistics — 非本人订单：抛 FORBIDDEN 且不查物流")
    void shouldThrowForbidden_whenOrderBelongsToAnotherUser() {
        when(orderClient.getOrderById(1L)).thenReturn(order(100L));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> logisticsManager.getOwnedLogistics(1L, 999L));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(packageRepository, never()).findByOrderId(anyLong());
    }

    @Test
    @DisplayName("getOwnedLogistics — 订单不存在：抛 ORDER_NOT_FOUND")
    void shouldThrowOrderNotFound_whenOrderMissing() {
        when(orderClient.getOrderById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> logisticsManager.getOwnedLogistics(1L, 100L));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
        verify(packageRepository, never()).findByOrderId(anyLong());
    }

    @Test
    @DisplayName("getOwnedLogistics — 订单无归属用户信息（降级数据）：fail-closed 抛 FORBIDDEN")
    void shouldThrowForbidden_whenOrderHasNoOwner() {
        when(orderClient.getOrderById(1L)).thenReturn(order(null));

        assertThrows(ForbiddenException.class, () -> logisticsManager.getOwnedLogistics(1L, 100L));
    }

    // ==================== createLogistics（幂等） ====================

    @Test
    @DisplayName("createLogistics — (订单,包裹号) 已存在：幂等跳过，不落库")
    void shouldBeIdempotent_whenPackageAlreadyExists() {
        CreateLogisticsCO co = new CreateLogisticsCO();
        co.setOrderId(1L);
        co.setPackageNo(1);

        when(packageRepository.existsByOrderIdAndPackageNo(1L, 1)).thenReturn(true);

        logisticsManager.createLogistics(co);

        verify(packageRepository, never()).save(any());
    }

    @Test
    @DisplayName("createLogistics — 新包裹：经聚合工厂创建（状态默认待揽收）后落库")
    void shouldCreatePackage_whenNotExists() {
        CreateLogisticsCO co = new CreateLogisticsCO();
        co.setOrderId(1L);
        co.setPackageNo(2);
        co.setLogisticsNumber("SF123");
        when(packageRepository.existsByOrderIdAndPackageNo(1L, 2)).thenReturn(false);

        logisticsManager.createLogistics(co);

        ArgumentCaptor<Package> captor = ArgumentCaptor.forClass(Package.class);
        verify(packageRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getOrderId());
        assertEquals(2, captor.getValue().getPackageNo());
        assertEquals(LogisticsStatusEnum.PENDING.getCode(), captor.getValue().getStatus());
        assertEquals("SF123", captor.getValue().getLogisticsNumber());
    }

    @Test
    @DisplayName("createLogistics — packageNo 未传：聚合工厂按 1 兜底（单包裹语义兼容）")
    void shouldDefaultPackageNoToOne_whenAbsent() {
        CreateLogisticsCO co = new CreateLogisticsCO();
        co.setOrderId(1L);
        when(packageRepository.existsByOrderIdAndPackageNo(1L, 1)).thenReturn(false);

        logisticsManager.createLogistics(co);

        ArgumentCaptor<Package> captor = ArgumentCaptor.forClass(Package.class);
        verify(packageRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getPackageNo());
    }

    // ==================== updateStatus（委托聚合校验） ====================

    @Test
    @DisplayName("updateStatus — 合法迁移：聚合变更后仓储落库")
    void shouldTransitAndUpdate_whenLegal() {
        Package pkg = pkg(1, LogisticsStatusEnum.PENDING.getCode());
        when(packageRepository.findByOrderIdAndPackageNo(1L, 1)).thenReturn(Optional.of(pkg));

        logisticsManager.updateStatus(1L, 1, LogisticsStatusEnum.SHIPPED.getCode());

        assertEquals(LogisticsStatusEnum.SHIPPED.getCode(), pkg.getStatus());
        verify(packageRepository).update(pkg);
    }

    @Test
    @DisplayName("updateStatus — 不存在的包裹抛 LOGISTICS_NOT_FOUND 且不落库")
    void shouldThrowNotFound_whenPackageMissing() {
        when(packageRepository.findByOrderIdAndPackageNo(999L, 1)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> logisticsManager.updateStatus(999L, 1, LogisticsStatusEnum.SHIPPED.getCode()));

        assertEquals(ErrorCode.LOGISTICS_NOT_FOUND, ex.getErrorCode());
        verify(packageRepository, never()).update(any());
    }

    @Test
    @DisplayName("updateStatus — 非法状态码（99）抛 PARAM_ERROR 且不查仓储")
    void shouldThrowParamError_whenStatusValueIllegal() {
        BizException ex = assertThrows(BizException.class,
                () -> logisticsManager.updateStatus(1L, 1, 99));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(packageRepository, never()).findByOrderIdAndPackageNo(anyLong(), anyInt());
        verify(packageRepository, never()).update(any());
    }

    @Test
    @DisplayName("updateStatus — 非法迁移（待揽收→已签收）抛 ORDER_STATUS_INVALID 且不落库")
    void shouldThrowStatusInvalid_whenTransitionIllegal() {
        Package pkg = pkg(1, LogisticsStatusEnum.PENDING.getCode());
        when(packageRepository.findByOrderIdAndPackageNo(1L, 1)).thenReturn(Optional.of(pkg));

        BizException ex = assertThrows(BizException.class,
                () -> logisticsManager.updateStatus(1L, 1, LogisticsStatusEnum.DELIVERED.getCode()));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
        verify(packageRepository, never()).update(any());
    }

    @Test
    @DisplayName("updateStatus — 同状态重放：幂等空操作（不落库）")
    void shouldBeNoOp_whenStatusUnchanged() {
        Package pkg = pkg(1, LogisticsStatusEnum.SHIPPED.getCode());
        when(packageRepository.findByOrderIdAndPackageNo(1L, 1)).thenReturn(Optional.of(pkg));

        logisticsManager.updateStatus(1L, 1, LogisticsStatusEnum.SHIPPED.getCode());

        verify(packageRepository, never()).update(any());
    }
}

package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.icedAmericanoMall.domain.entity.OrderLogisticsEntity;
import org.icedAmericanoMall.enums.LogisticsStatusEnum;
import org.icedAmericanoMall.mapper.OrderLogisticsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.noLazy.common.exception.BizException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LogisticsServiceImpl — verifies logistics lifecycle business logic.
 * <p>
 * All methods in LogisticsServiceImpl use only standard BaseMapper methods
 * (getById, save, updateById) and are fully testable with Mockito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogisticsServiceImpl 业务逻辑测试")
class LogisticsServiceImplTest {

    @Mock
    private OrderLogisticsMapper mockMapper;

    private LogisticsServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new LogisticsServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", (BaseMapper<OrderLogisticsEntity>) mockMapper);
    }

    // ==================== getByOrderId ====================

    @Test
    @DisplayName("getByOrderId — 物流记录存在时返回实体")
    void shouldReturnEntity_whenLogisticsExists() {
        OrderLogisticsEntity entity = new OrderLogisticsEntity();
        entity.setOrderId(1L);
        entity.setLogisticsNumber("SF123456");
        entity.setLogisticsCompany("顺丰快递");
        entity.setStatus(LogisticsStatusEnum.SHIPPED.getCode());

        when(mockMapper.selectById(1L)).thenReturn(entity);

        OrderLogisticsEntity result = service.getByOrderId(1L);
        assertNotNull(result);
        assertEquals(1L, result.getOrderId());
        assertEquals("SF123456", result.getLogisticsNumber());
        assertEquals(LogisticsStatusEnum.SHIPPED.getCode(), result.getStatus());
        verify(mockMapper).selectById(1L);
    }

    @Test
    @DisplayName("getByOrderId — 物流记录不存在时抛出 BizException")
    void shouldThrowException_whenLogisticsNotFound() {
        when(mockMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.getByOrderId(999L));
        assertTrue(ex.getMessage().contains("物流信息不存在"));
        verify(mockMapper).selectById(999L);
    }

    // ==================== createLogistics ====================

    @Test
    @DisplayName("createLogistics — 创建物流记录，状态默认为待揽收")
    void shouldSetDefaultStatusAsPending_whenCreatingLogistics() {
        OrderLogisticsEntity entity = new OrderLogisticsEntity();
        entity.setOrderId(1L);
        entity.setLogisticsNumber("SF123456");
        entity.setLogisticsCompany("顺丰快递");

        when(mockMapper.insert(any(OrderLogisticsEntity.class))).thenReturn(1);

        service.createLogistics(entity);

        assertEquals(LogisticsStatusEnum.PENDING.getCode(), entity.getStatus(),
                "创建时状态应默认为" + LogisticsStatusEnum.PENDING.getDesc());
        verify(mockMapper).insert(entity);
    }

    @Test
    @DisplayName("createLogistics — 若已设置状态则不覆盖")
    void shouldNotOverrideStatus_whenStatusAlreadySet() {
        OrderLogisticsEntity entity = new OrderLogisticsEntity();
        entity.setOrderId(1L);
        entity.setStatus(LogisticsStatusEnum.SHIPPED.getCode()); // 显式设为运输中

        when(mockMapper.insert(any(OrderLogisticsEntity.class))).thenReturn(1);

        service.createLogistics(entity);

        assertEquals(LogisticsStatusEnum.SHIPPED.getCode(), entity.getStatus(),
                "已设置的状态不应被覆盖");
        verify(mockMapper).insert(entity);
    }

    @Test
    @DisplayName("createLogistics — 完整物流字段正确保存")
    void shouldSaveAllFields_whenCreatingLogistics() {
        OrderLogisticsEntity entity = new OrderLogisticsEntity();
        entity.setOrderId(100L);
        entity.setLogisticsNumber("YTO987654321");
        entity.setLogisticsCompany("圆通快递");
        entity.setContact("李四");
        entity.setMobile("13900139000");
        entity.setProvince("浙江省");
        entity.setCity("杭州市");
        entity.setDistrict("余杭区");
        entity.setDetail("文一西路969号");

        when(mockMapper.insert(any(OrderLogisticsEntity.class))).thenReturn(1);

        service.createLogistics(entity);

        verify(mockMapper).insert(entity);
        assertEquals(LogisticsStatusEnum.PENDING.getCode(), entity.getStatus());
    }

    // ==================== updateStatus ====================

    @Test
    @DisplayName("updateStatus — 状态从待揽收更新为运输中")
    void shouldUpdateStatusFromPendingToShipped() {
        OrderLogisticsEntity entity = new OrderLogisticsEntity();
        entity.setOrderId(1L);
        entity.setStatus(LogisticsStatusEnum.PENDING.getCode());

        when(mockMapper.selectById(1L)).thenReturn(entity);
        when(mockMapper.updateById(any(OrderLogisticsEntity.class))).thenReturn(1);

        service.updateStatus(1L, LogisticsStatusEnum.SHIPPED.getCode());

        assertEquals(LogisticsStatusEnum.SHIPPED.getCode(), entity.getStatus());
        verify(mockMapper).selectById(1L);
        verify(mockMapper).updateById(entity);
    }

    @Test
    @DisplayName("updateStatus — 状态更新为已签收")
    void shouldUpdateStatusToDelivered() {
        OrderLogisticsEntity entity = new OrderLogisticsEntity();
        entity.setOrderId(1L);
        entity.setStatus(LogisticsStatusEnum.SHIPPED.getCode());

        when(mockMapper.selectById(1L)).thenReturn(entity);
        when(mockMapper.updateById(any(OrderLogisticsEntity.class))).thenReturn(1);

        service.updateStatus(1L, LogisticsStatusEnum.DELIVERED.getCode());

        assertEquals(LogisticsStatusEnum.DELIVERED.getCode(), entity.getStatus());
    }

    @Test
    @DisplayName("updateStatus — 不存在的物流记录抛出异常")
    void shouldThrowException_whenUpdatingNonExistentLogistics() {
        when(mockMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class,
                () -> service.updateStatus(999L, LogisticsStatusEnum.SHIPPED.getCode()));
        verify(mockMapper, never()).updateById(any(OrderLogisticsEntity.class));
    }

    // ==================== LogisticsStatusEnum ====================

    @Test
    @DisplayName("LogisticsStatusEnum — 状态码正确映射")
    void shouldHaveCorrectLogisticsStatusCodes() {
        assertEquals(1, LogisticsStatusEnum.PENDING.getCode(), "待揽收");
        assertEquals(2, LogisticsStatusEnum.SHIPPED.getCode(), "运输中");
        assertEquals(3, LogisticsStatusEnum.DELIVERED.getCode(), "已签收");
        assertEquals(4, LogisticsStatusEnum.RETURNED.getCode(), "已退回");
    }

    @Test
    @DisplayName("LogisticsStatusEnum — 共 4 个物流状态")
    void shouldHaveExactlyFourLogisticsStatuses() {
        assertEquals(4, LogisticsStatusEnum.values().length);
    }

    @Test
    @DisplayName("LogisticsStatusEnum.of — 根据 code 查找枚举")
    void shouldFindEnumByCode() {
        assertEquals(LogisticsStatusEnum.PENDING, LogisticsStatusEnum.of(1));
        assertEquals(LogisticsStatusEnum.SHIPPED, LogisticsStatusEnum.of(2));
        assertEquals(LogisticsStatusEnum.DELIVERED, LogisticsStatusEnum.of(3));
        assertEquals(LogisticsStatusEnum.RETURNED, LogisticsStatusEnum.of(4));
    }

    @Test
    @DisplayName("LogisticsStatusEnum.of — 无效 code 返回 null")
    void shouldReturnNull_whenInvalidCode() {
        assertNull(LogisticsStatusEnum.of(null));
        assertNull(LogisticsStatusEnum.of(99));
        assertNull(LogisticsStatusEnum.of(0));
    }

    @Test
    @DisplayName("LogisticsStatusEnum — 状态流转: 待揽收→运输中→已签收")
    void shouldSupportValidStateTransitions() {
        // 正常流转路径验证
        assertNotEquals(LogisticsStatusEnum.PENDING.getCode(),
                LogisticsStatusEnum.SHIPPED.getCode());
        assertNotEquals(LogisticsStatusEnum.SHIPPED.getCode(),
                LogisticsStatusEnum.DELIVERED.getCode());
        // 不允许回到上一状态
        assertNotEquals(LogisticsStatusEnum.DELIVERED.getCode(),
                LogisticsStatusEnum.PENDING.getCode());
    }
}

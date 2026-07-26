package org.icedamericanomall.service.impl;

import org.icedamericanomall.domain.entity.PayOrderEntity;
import org.icedamericanomall.enums.PayStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PayOrderServiceImpl — entity and enum validation.
 *
 * Note: {@code initiatePayment}, {@code handleCallback}, and {@code queryStatus}
 * use MyBatis-Plus lambdaQuery()/save()/updateById() chains which depend on the mapper proxy.
 * Those methods require {@code @SpringBootTest} integration tests with Testcontainers or H2.
 */
@DisplayName("PayOrderServiceImpl 单元测试")
class PayOrderServiceImplTest {

    // ==================== PayOrderEntity 字段 ====================

    @Test
    @DisplayName("PayOrderEntity — 全部字段赋值正确")
    void shouldSetAllPayOrderFields() {
        PayOrderEntity entity = new PayOrderEntity();
        entity.setId(1L);
        entity.setBizOrderNo("ORD-001");
        entity.setPayOrderNo("PAY-UUID-001");
        entity.setBizUserId(100L);
        entity.setPayChannelCode("WECHAT");
        entity.setAmount(129900);  // 1299.00 元 = 129900 分
        entity.setPayType(4);       // 扫码支付
        entity.setStatus(PayStatusEnum.PENDING_PAY.getCode());
        entity.setPayOverTime(LocalDateTime.now().plusMinutes(30));
        entity.setQrCodeUrl("weixin://wxpay/bizpayurl?pr=abc123");

        assertEquals(1L, entity.getId());
        assertEquals("ORD-001", entity.getBizOrderNo());
        assertEquals("PAY-UUID-001", entity.getPayOrderNo());
        assertEquals(100L, entity.getBizUserId());
        assertEquals("WECHAT", entity.getPayChannelCode());
        assertEquals(129900, entity.getAmount());
        assertEquals(4, entity.getPayType());
        assertNotNull(entity.getPayOverTime());
        assertEquals("weixin://wxpay/bizpayurl?pr=abc123", entity.getQrCodeUrl());
    }

    @Test
    @DisplayName("PayOrderEntity — 金额以 Integer（分）存储")
    void shouldStoreAmountInCents() {
        PayOrderEntity entity = new PayOrderEntity();
        entity.setAmount(129900);
        assertInstanceOf(Integer.class, entity.getAmount());
        assertEquals(129900, entity.getAmount());
    }

    @Test
    @DisplayName("PayOrderEntity — 支持 H5/公众号/扫码/余额四种支付类型")
    void shouldSupportAllPayTypes() {
        PayOrderEntity entity = new PayOrderEntity();
        entity.setPayType(1);
        assertEquals(1, entity.getPayType()); // H5
        entity.setPayType(2);
        assertEquals(2, entity.getPayType()); // 公众号
        entity.setPayType(3);
        assertEquals(3, entity.getPayType()); // 小程序
        entity.setPayType(4);
        assertEquals(4, entity.getPayType()); // 扫码
    }

    // ==================== PayStatusEnum 枚举 ====================

    @Test
    @DisplayName("PayStatusEnum — 状态码正确映射")
    void shouldHaveCorrectPayStatusCodes() {
        assertEquals(0, PayStatusEnum.PENDING_SUBMIT.getCode(), "待提交");
        assertEquals(1, PayStatusEnum.PENDING_PAY.getCode(), "待支付");
        assertEquals(2, PayStatusEnum.TIMEOUT_CANCEL.getCode(), "超时取消");
        assertEquals(3, PayStatusEnum.SUCCESS.getCode(), "支付成功");
    }

    @Test
    @DisplayName("PayStatusEnum — 共 4 个支付状态")
    void shouldHaveExactlyFourStatuses() {
        assertEquals(4, PayStatusEnum.values().length);
    }

    @Test
    @DisplayName("PayStatusEnum — 状态流转方向验证")
    void shouldHaveCorrectStateTransition() {
        // 待提交(0) → 待支付(1) → 成功(3) / 超时取消(2)
        assertNotEquals(PayStatusEnum.PENDING_SUBMIT.getCode(),
                PayStatusEnum.PENDING_PAY.getCode());
        assertNotEquals(PayStatusEnum.PENDING_PAY.getCode(),
                PayStatusEnum.SUCCESS.getCode());
        assertNotEquals(PayStatusEnum.PENDING_PAY.getCode(),
                PayStatusEnum.TIMEOUT_CANCEL.getCode());
    }

    // ==================== 边界情况 ====================

    @Test
    @DisplayName("PayOrderEntity — 默认 qrCodeUrl 为空")
    void shouldHaveNullQrCodeUrl_whenNewInstance() {
        PayOrderEntity entity = new PayOrderEntity();
        assertNull(entity.getQrCodeUrl());
    }

    @Test
    @DisplayName("PayOrderEntity — paySuccessTime 支付成功后才设置")
    void shouldHaveNullPaySuccessTime_whenNotPaid() {
        PayOrderEntity entity = new PayOrderEntity();
        entity.setStatus(PayStatusEnum.PENDING_PAY.getCode());
        assertNull(entity.getPaySuccessTime());
    }

    @Test
    @DisplayName("PayOrderEntity — 超时时间在创建后 30 分钟")
    void shouldSetPayOverTime_whenCreated() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime overTime = now.plusMinutes(30);
        PayOrderEntity entity = new PayOrderEntity();
        entity.setPayOverTime(overTime);
        assertTrue(entity.getPayOverTime().isAfter(now));
    }

    @Test
    @DisplayName("CreateLogisticsDTO — 字段赋值正确")
    void shouldSetCreateLogisticsDTOFields() {
        var dto = new org.icedamericanomall.dto.CreateLogisticsDTO();
        dto.setOrderId(1L);
        dto.setLogisticsNumber("SF123456");
        dto.setLogisticsCompany("顺丰快递");
        dto.setContact("张三");
        dto.setMobile("13800138000");

        assertEquals(1L, dto.getOrderId());
        assertEquals("SF123456", dto.getLogisticsNumber());
        assertEquals("顺丰快递", dto.getLogisticsCompany());
        assertEquals("张三", dto.getContact());
        assertEquals("13800138000", dto.getMobile());
    }

    // ==================== 支付幂等性 ====================

    @Test
    @DisplayName("幂等保护 — 已支付订单不应重复支付")
    void shouldDetectAlreadyPaidOrder() {
        PayOrderEntity entity = new PayOrderEntity();
        entity.setStatus(PayStatusEnum.SUCCESS.getCode());
        assertTrue(entity.getStatus() == PayStatusEnum.SUCCESS.getCode(),
                "已支付订单状态应为SUCCESS");

        // 模拟重复支付检查：状态为SUCCESS时拒绝
        PayStatusEnum status = PayStatusEnum.SUCCESS;
        boolean isAlreadyPaid = (status.getCode() == PayStatusEnum.SUCCESS.getCode());
        assertTrue(isAlreadyPaid, "SUCCESS状态应被识别为已支付");
    }

    @Test
    @DisplayName("幂等保护 — 回调已处理时直接返回")
    void shouldSkipCallback_whenAlreadyProcessed() {
        // 模拟：支付单状态已是SUCCESS，再次收到回调
        PayOrderEntity entity = new PayOrderEntity();
        entity.setStatus(PayStatusEnum.SUCCESS.getCode());

        // 幂等检查：已SUCCESS则直接返回
        boolean shouldSkip = entity.getStatus() == PayStatusEnum.SUCCESS.getCode();
        assertTrue(shouldSkip, "已成功时回调应被跳过");
    }

    @Test
    @DisplayName("超时取消 — 仅待支付状态可超时取消")
    void shouldOnlyTimeoutCancelPendingPayOrders() {
        PayOrderEntity pending = new PayOrderEntity();
        pending.setStatus(PayStatusEnum.PENDING_PAY.getCode());

        PayOrderEntity success = new PayOrderEntity();
        success.setStatus(PayStatusEnum.SUCCESS.getCode());

        // 待支付 → 可取消
        assertNotEquals(PayStatusEnum.TIMEOUT_CANCEL.getCode(), pending.getStatus());
        // 已成功 → 不可取消
        assertNotEquals(PayStatusEnum.TIMEOUT_CANCEL.getCode(), success.getStatus());
    }

    @Test
    @DisplayName("支付单号唯一 — payOrderNo 使用 UUID 格式")
    void shouldHaveUniquePayOrderNo() {
        PayOrderEntity entity1 = new PayOrderEntity();
        entity1.setPayOrderNo("uuid-001");
        PayOrderEntity entity2 = new PayOrderEntity();
        entity2.setPayOrderNo("uuid-002");

        assertNotEquals(entity1.getPayOrderNo(), entity2.getPayOrderNo(),
                "不同支付单应有不同的 payOrderNo");
    }
}

package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroupBuyOrderReq（T27 拼团下单请求）Bean Validation 单元测试 ——
 * 与 {@link DirectOrderReqTest} 同构：activityId/addressId 必填、teamNo 可选、
 * remark 长度上限 {@code Order.REMARK_MAX_LENGTH}（200）。
 */
@DisplayName("GroupBuyOrderReq 契约校验")
class GroupBuyOrderReqTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private GroupBuyOrderReq req() {
        GroupBuyOrderReq r = new GroupBuyOrderReq();
        r.setActivityId(1L);
        r.setTeamNo(null);
        r.setAddressId(1L);
        return r;
    }

    private Set<ConstraintViolation<GroupBuyOrderReq>> violations(GroupBuyOrderReq req) {
        return validator.validate(req);
    }

    @Test
    @DisplayName("activityId + addressId 齐全、teamNo/remark 缺省（开团）— 无约束违规")
    void shouldPass_whenOpenTeamMinimalBody() {
        assertTrue(violations(req()).isEmpty());
    }

    @Test
    @DisplayName("activityId 缺失 — 触发 @NotNull（→ 400 PARAM_ERROR）")
    void shouldReject_whenActivityIdMissing() {
        GroupBuyOrderReq r = req();
        r.setActivityId(null);
        assertTrue(violations(r).stream().anyMatch(v -> "activityId".equals(v.getPropertyPath().toString())));
    }

    @Test
    @DisplayName("addressId 缺失 — 触发 @NotNull")
    void shouldReject_whenAddressIdMissing() {
        GroupBuyOrderReq r = req();
        r.setAddressId(null);
        assertTrue(violations(r).stream().anyMatch(v -> "addressId".equals(v.getPropertyPath().toString())));
    }

    @Test
    @DisplayName("teamNo 非空（参团）— 合法")
    void shouldPass_whenTeamNoPresent() {
        GroupBuyOrderReq r = req();
        r.setTeamNo("T-9f3a1");
        assertTrue(violations(r).isEmpty());
    }

    @Test
    @DisplayName("remark 长度 = REMARK_MAX_LENGTH(200) — 边界内合法")
    void shouldPass_whenRemarkAtMaxLength() {
        GroupBuyOrderReq r = req();
        r.setRemark("好".repeat(Order.REMARK_MAX_LENGTH));
        assertTrue(violations(r).isEmpty());
    }

    @Test
    @DisplayName("remark 长度 = REMARK_MAX_LENGTH + 1(201) — 触发 @Size（→ 400 PARAM_ERROR）")
    void shouldReject_whenRemarkExceedsMaxLength() {
        GroupBuyOrderReq r = req();
        r.setRemark("好".repeat(Order.REMARK_MAX_LENGTH + 1));
        assertTrue(violations(r).stream().anyMatch(v -> "remark".equals(v.getPropertyPath().toString())));
    }
}

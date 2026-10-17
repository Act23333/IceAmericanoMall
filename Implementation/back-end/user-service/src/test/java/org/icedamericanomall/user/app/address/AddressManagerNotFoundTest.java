package org.icedamericanomall.user.app.address;

import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.adapter.request.AddressReq;
import org.icedamericanomall.user.domain.model.address.Address;
import org.icedamericanomall.user.domain.repository.AddressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 地址域的<b>不存在承载</b>（2026-10-18 批次 ①「not-found 归位」）。
 *
 * <p><b>两处被纠正的偏离</b>：
 * <ol>
 *   <li>{@code getById} 曾抛 {@code USER_NOT_FOUND}(1102, "地址不存在") —— 用一个「用户」码
 *       承载「地址」资源，调用方无法按码分辨实体。现改 {@code NO_RESOURCE_FOUND}(1103)，同属
 *       not-found 家族且同为 404。<b>不新增</b> {@code ADDRESS_NOT_FOUND}：新增码会破坏
 *       前后端 99:99 逐条对齐门禁；</li>
 *   <li>{@code updateAddress / setDefault / deleteAddress} 的「0 行」曾抛
 *       {@code DBException(INTERNAL_ERROR)}（<b>500</b>）—— 把一次并发删除（资源已不存在）
 *       报成服务端故障。现改为 404 + 1103。</li>
 * </ol>
 *
 * <p><b>为什么不走幂等 200</b>：写路径的「目标已消失」必须可见（回 200 等于告诉客户端修改已生效），
 * 只有纯读场景才适合 fail-open —— 该取舍写死在 {@code AddressManager} 的类注释里。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddressManager — 地址不存在一律 not-found（1103 / 404），不再借 1102 或 500")
class AddressManagerNotFoundTest {

    private static final Long USER_ID = 1L;
    private static final Long ADDRESS_ID = 9L;

    @Mock
    private AddressRepository addressRepository;

    private AddressManager addressManager;

    private AddressManager manager() {
        if (addressManager == null) {
            addressManager = new AddressManager(addressRepository);
        }
        return addressManager;
    }

    /** 已存在且归属当前用户的地址（并发删除场景的「删除前」快照）。 */
    private static Address owned() {
        Address address = Address.builder().build();
        address.setId(ADDRESS_ID);
        address.setUserId(USER_ID);
        return address;
    }

    private static AddressReq req() {
        AddressReq req = new AddressReq();
        req.setReceiver("张三");
        req.setPhone("13800000000");
        req.setDetail("某路 1 号");
        return req;
    }

    private static void assertNotFound(BizException ex, String where) {
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode(),
                where + "：地址不存在必须 1103（不得借 USER_NOT_FOUND 1102）");
        assertEquals(404, ex.getErrorCode().getHttpStatus().value(), where + "：必须真 404");
        assertEquals(ErrorCategory.CLIENT, ErrorCategory.of(ex.getErrorCode()),
                where + "：404 → CLIENT（Feign 侧透传，不改写 SYSTEM_BUSY）");
        assertNotEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode(),
                where + "：并发删除不是服务器错误，不得落 500");
    }

    @Test
    @DisplayName("getById（内部 Feign 快照）— 查不到 → 1103 / 404，不再是 USER_NOT_FOUND(1102)")
    void shouldThrowNoResourceFound_whenAddressMissing() {
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> manager().getById(ADDRESS_ID));

        assertNotFound(ex, "getById");
        assertEquals("地址不存在", ex.getDisplayMessage(), "文案保持不变：前端提示逐字可比");
    }

    @Test
    @DisplayName("updateAddress — 并发删除导致 0 行 → 1103 / 404（不再是 DBException 500）")
    void shouldThrow404_whenUpdateAffectsNoRow() {
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(owned()));
        when(addressRepository.updateByUserIdAndId(any(Address.class))).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> manager().updateAddress(USER_ID, ADDRESS_ID, req()));

        assertNotFound(ex, "updateAddress");
    }

    @Test
    @DisplayName("setDefault — 并发删除导致 0 行 → 1103 / 404（不再是 DBException 500）")
    void shouldThrow404_whenSetDefaultAffectsNoRow() {
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(owned()));
        when(addressRepository.markDefault(anyLong(), anyLong())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> manager().setDefault(USER_ID, ADDRESS_ID));

        assertNotFound(ex, "setDefault");
    }

    @Test
    @DisplayName("deleteAddress — 并发删除导致 0 行 → 1103 / 404（不再是 DBException 500）")
    void shouldThrow404_whenDeleteAffectsNoRow() {
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(owned()));
        when(addressRepository.deleteByUserIdAndId(anyLong(), anyLong())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> manager().deleteAddress(USER_ID, ADDRESS_ID));

        assertNotFound(ex, "deleteAddress");
    }
}

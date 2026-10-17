package org.icedamericanomall.user.adapter.web;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.adapter.web.internal.InternalAddressController;
import org.icedamericanomall.user.app.address.AddressManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 地址端点的<b>不存在承载</b>（2026-10-18 批次 ①）。
 *
 * <p>覆盖两类端点、两种承载（ADR-004）：
 * <ul>
 *   <li>对外 {@code /api/user/address/**} → <b>HTTP 404 + {@code Result}(1103)</b>，
 *       信封键集合仍是 {@code code/msg/data}（本批<b>未</b>改动对外报文形状）；</li>
 *   <li>内部 {@code /internal/address/{id}} → <b>HTTP 404 + 裸 {@code ErrorInfo}</b>
 *       （{@code code/msg/category/retryable/traceId}，{@code category=CLIENT}）。
 *       这一条是 Feign 侧「4xx 原样透传」的证据：调用方看到的是「地址不存在」而不是
 *       「用户服务不可用」。</li>
 * </ul>
 *
 * <p><b>改前必失败</b>：{@code PUT /update/{id}} 的并发删除分支改造前是
 * {@code DBException(INTERNAL_ERROR)} → <b>500</b>；{@code /internal/address/{id}}
 * 改造前借 {@code USER_NOT_FOUND(1102)}（404 但码错，前端/调用方无法按码分辨实体）。
 * 故本类断言「404 + 1103」两个维度，改造前必然红。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("地址端点 — 不存在一律 1103；对外 404+Result，内部 404+ErrorInfo(CLIENT)")
class AddressControllerNotFoundTest {

    private static final String VALID_BODY = """
            {"receiver":"张三","phone":"13800000000","province":"上海","city":"上海",
             "district":"浦东","street":"张江路","detail":"1 号"}
            """;

    @Mock
    private AddressManager addressManager;

    private MockMvc publicMvc;
    private MockMvc internalMvc;

    @BeforeEach
    void setUp() {
        publicMvc = MockMvcBuilders.standaloneSetup(new AddressController(addressManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        internalMvc = MockMvcBuilders.standaloneSetup(new InternalAddressController(addressManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static BizException addressGone() {
        return new BizException(ErrorCode.NO_RESOURCE_FOUND, "地址不存在");
    }

    @Test
    @DisplayName("修改地址（并发删除 0 行）→ 404 + 1103，不再是 500")
    void shouldReturn404_whenUpdateAddressVanished() throws Exception {
        doThrow(addressGone()).when(addressManager).updateAddress(any(), anyLong(), any());

        publicMvc.perform(MockMvcRequestBuilders.put("/api/user/address/update/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1103))
                .andExpect(jsonPath("$.msg").value("地址不存在"));
    }

    @Test
    @DisplayName("设默认地址（并发删除 0 行）→ 404 + 1103，不再是 500")
    void shouldReturn404_whenSetDefaultVanished() throws Exception {
        doThrow(addressGone()).when(addressManager).setDefault(any(), anyLong());

        publicMvc.perform(MockMvcRequestBuilders.put("/api/user/address/default/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1103));
    }

    @Test
    @DisplayName("删除地址（并发删除 0 行）→ 404 + 1103，不再是 500")
    void shouldReturn404_whenDeleteAddressVanished() throws Exception {
        doThrow(addressGone()).when(addressManager).deleteAddress(any(), anyLong());

        publicMvc.perform(MockMvcRequestBuilders.delete("/api/user/address/delete/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1103));
    }

    @Test
    @DisplayName("内部 Feign 快照 GET /internal/address/{id} → 404 + 裸 ErrorInfo，category=CLIENT（不透传成 SYSTEM_BUSY）")
    void shouldReturnTyped404_whenInternalAddressMissing() throws Exception {
        doThrow(addressGone()).when(addressManager).getById(9L);

        internalMvc.perform(MockMvcRequestBuilders.get("/internal/address/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1103))
                .andExpect(jsonPath("$.msg").value("地址不存在"))
                .andExpect(jsonPath("$.category").value(ErrorCategory.CLIENT.name()))
                .andExpect(jsonPath("$.retryable").value(false));
    }
}

package org.icedamericanomall.marketing.adapter.internal;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.marketing.app.promotion.PromotionQueryManager;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalPromotionController 适配层接线测试（V5.8 I44）。
 *
 * <p>控制器只做参数校验与委托：{@code POST /internal/promotion/query} 委托
 * {@code PromotionQueryManager.queryActive}；{@code /internal/**} 不包 {@code Result}
 * （业务异常以真实 HTTP 状态透出，ADR-004）；sellerId 缺失 → 400。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalPromotionController — I44 委托 / 参数校验")
class InternalPromotionControllerTest {

    @Mock
    private PromotionQueryManager promotionQueryManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalPromotionController(promotionQueryManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("query — 合法 body → 委托 promotionQueryManager.queryActive 且透传 sellerId")
    void shouldDelegateQuery() throws Exception {
        when(promotionQueryManager.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/promotion/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerId\":500}"))
                .andExpect(status().isOk());

        verify(promotionQueryManager).queryActive(argThat(query -> query != null
                && Long.valueOf(500L).equals(query.getSellerId())));
    }

    @Test
    @DisplayName("query — sellerId 缺失 → 400（@Valid），不触达应用层")
    void shouldReturn400_whenSellerIdMissing() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/promotion/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(promotionQueryManager);
    }

    @Test
    @DisplayName("query — 有生效活动 → 原样透出 CO 列表（不包 Result）")
    void shouldReturnRawCoList_whenActivePromotionsExist() throws Exception {
        PromotionCO co = new PromotionCO(1L, "双11店铺满减", 500L, 2, 10000, 8500, 5000);
        when(promotionQueryManager.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of(co));

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/promotion/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerId\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value(2))
                .andExpect(jsonPath("$[0].threshold").value(10000));
    }
}

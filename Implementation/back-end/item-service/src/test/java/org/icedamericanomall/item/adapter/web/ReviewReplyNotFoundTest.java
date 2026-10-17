package org.icedamericanomall.item.adapter.web;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.app.ReviewManager;
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
 * 商家回复评价（{@code POST /api/item/review/{id}/reply}）的存在性承载 —— 批次 ①。
 *
 * <p><b>改前必失败</b>：改造前该分支抛 {@code BUSINESS_EXECUTION_EXCEPTION}(3000)，
 * 端点返回 <b>HTTP 200 + body code 3000「评价不存在」</b>；现返回 <b>404 + 1103</b>。
 *
 * <p>同一端点内还有另一条<b>真·业务规则拒绝</b>（「已回复过该评价」），本类第二条用例守住它
 * <b>仍是 200</b> —— 批 ① 只处理 not-found，不得顺手把规则拒绝也改成 4xx。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewController — 评价不存在 404/1103；已回复仍 200/3000")
class ReviewReplyNotFoundTest {

    @Mock
    private ReviewManager reviewManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("评价不存在 → 404 + 1103（不再是 200 + 3000）")
    void shouldReturn404_whenReviewMissing() throws Exception {
        doThrow(new BizException(ErrorCode.NO_RESOURCE_FOUND, "评价不存在"))
                .when(reviewManager).replyToReview(any(), anyLong(), any());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/item/review/9/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"感谢支持\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1103))
                .andExpect(jsonPath("$.msg").value("评价不存在"));
    }

    @Test
    @DisplayName("已回复过 → 仍 200 + 3000（业务规则拒绝，本批刻意不动）")
    void shouldKeep200_whenAlreadyReplied() throws Exception {
        doThrow(new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已回复过该评价"))
                .when(reviewManager).replyToReview(any(), anyLong(), any());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/item/review/9/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"感谢支持\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3000));
    }
}

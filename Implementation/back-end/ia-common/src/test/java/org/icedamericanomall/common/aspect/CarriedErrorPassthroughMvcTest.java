package org.icedamericanomall.common.aspect;

import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.exception.CarriedErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>跨服务 4xx 透传的「框架层」回归测试</b> —— 让 <b>Spring 自己去选分支</b>，而不是直接调 handler 方法。
 *
 * <h2>为什么还需要这一层（它补的是前两层都覆盖不到的洞）</h2>
 * <table border="1">
 *   <caption>三层证据的分工</caption>
 *   <tr><th>层</th><th>证明了什么</th><th>证明不了什么</th></tr>
 *   <tr><td>{@code GlobalExceptionHandlerTest}（直接调 handler 方法）</td>
 *       <td>「给定这个异常，响应长什么样」</td>
 *       <td>Spring <b>会不会</b>把这个异常交给它 —— 直调绕过了类型匹配</td></tr>
 *   <tr><td>{@code UpstreamExceptionPassthroughWiringTest}（ia-api，继承断言）</td>
 *       <td>真实类确实是载体形状</td>
 *       <td>载体分支是否被注册、匹配顺序是否正确</td></tr>
 *   <tr><td><b>本类</b>（MockMvc + 真实 {@code ExceptionHandlerExceptionResolver}）</td>
 *       <td>整条链：请求 → 抛异常 → Spring 解析 → 选中载体分支 → HTTP 报文</td>
 *       <td>真实网络/网关（运行态，另见交接单「运行态未验」）</td></tr>
 * </table>
 *
 * <p><b>它咬得住什么、咬不住什么（实测边界，别想当然）</b>：
 * <ul>
 *   <li><b>咬得住</b>：载体分支与兜底回溯<b>同时</b>失效时，本类全红（摘注解 + 停掉
 *       {@code cause} 链回溯 → 五条透传用例全部变成 500 + {@code code=1000}）。</li>
 *   <li><b>咬不住（刻意）</b>：只摘 {@code @ExceptionHandler(CarriedErrorException.class)}
 *       时本类<b>仍然全绿</b> —— 因为 {@code handleException} 里的 {@code cause} 链回溯
 *       会接住同一条异常。这两条路径是<b>互为冗余的双保险</b>，不是门禁漏洞：
 *       单一变异杀不掉它，正是双保险的定义。谁把这两条同时当成重复代码删掉，本类才会红。</li>
 * </ul>
 * <p>因此「注解还在不在」由 {@code GlobalExceptionHandlerTest#shouldDeclareExceptionHandlerForCarriedError}
 * 单独守（那是<b>声明层</b>的守卫，不是行为层的）—— 两者职责不同，别合并。
 *
 * <p>用的是 {@code standaloneSetup}：只装一个探针 Controller + 真实 handler，
 * <b>不需要 Nacos / Redis / MySQL</b>，因此在基础设施不可达时也能跑。
 */
@DisplayName("跨服务 4xx 透传 — 框架层（Spring 真的会选中载体分支）")
class CarriedErrorPassthroughMvcTest {

    private static final int DOWNSTREAM_CODE = 1103;
    private static final int DOWNSTREAM_STATUS = 404;
    private static final String DOWNSTREAM_MSG = "商品不存在或已删除";

    /** 探针 Controller：三个端点分别抛「直接载体」「装饰器包装的载体」「无关异常」。 */
    @RestController
    static class ProbeController {

        @GetMapping("/api/probe/not-found")
        Object publicNotFound() {
            throw clientError();
        }

        @GetMapping("/api/probe/unavailable")
        Object publicUnavailable() {
            throw new ProbeCarrier(ErrorInfo.of(1001, "系统繁忙", ErrorCategory.TRANSIENT, null), 503);
        }

        @GetMapping("/api/probe/wrapped")
        Object wrappedByDecorator() {
            // 重试/熔断/隔舱等装饰器，以及容器自身的包装，都会让第一层类型不再是载体
            throw new RuntimeException("decorator", clientError());
        }

        @GetMapping("/api/probe/plain")
        Object plainFailure() {
            return throwPlain();
        }

        private static Object throwPlain() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/internal/probe/not-found")
        Object internalNotFound() {
            throw clientError();
        }
    }

    /** 测试用载体（本模块不能依赖 ia-api 的真实子类；ia-api 侧由继承断言守）。 */
    private static final class ProbeCarrier extends CarriedErrorException {
        private static final long serialVersionUID = 1L;

        ProbeCarrier(ErrorInfo errorInfo, int upstreamStatus) {
            super(errorInfo, upstreamStatus);
        }
    }

    private static ProbeCarrier clientError() {
        return new ProbeCarrier(
                ErrorInfo.of(DOWNSTREAM_CODE, DOWNSTREAM_MSG, ErrorCategory.CLIENT, "trace-down"), DOWNSTREAM_STATUS);
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("内部 /internal/** → 真实 404 + 下游 ErrorInfo 原样（无 Result 信封）")
    void shouldPassThrough_whenInternalClientError() throws Exception {
        mockMvc().perform(get("/internal/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(DOWNSTREAM_CODE))
                .andExpect(jsonPath("$.msg").value(DOWNSTREAM_MSG))
                .andExpect(jsonPath("$.category").value("CLIENT"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.traceId").value("trace-down"))
                // 内部出口是 ErrorInfo，不是 Result ⇒ 不得出现 data 键
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("对外 /api/** → 真实 404 + Result 信封（code 是下游码，且不夹带 category）")
    void shouldPassThrough_whenPublicClientError() throws Exception {
        mockMvc().perform(get("/api/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(DOWNSTREAM_CODE))
                .andExpect(jsonPath("$.msg").value(DOWNSTREAM_MSG))
                // 信封键集合零变化：category / retryable / traceId 一个都不许进来
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.retryable").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    @DisplayName("上游不可用 → 503（不是 500）")
    void shouldMapTo503_whenCarriedTransient() throws Exception {
        mockMvc().perform(get("/api/probe/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("★ 装饰器把载体包一层 → 仍按 404 透传（兜底分支的 cause 链回溯被框架层验证）")
    void shouldStillPassThrough_whenCarrierWrapped() throws Exception {
        mockMvc().perform(get("/api/probe/wrapped"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(DOWNSTREAM_CODE));
    }

    @Test
    @DisplayName("反向对照 — 无关异常仍 500 + code=1000（兜底没被载体逻辑吃掉）")
    void shouldStillReturn500_whenPlainException() throws Exception {
        mockMvc().perform(get("/api/probe/plain"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1000));
    }
}

package org.icedamericanomall.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把带 {@link QueryModel} 的形参用 <b>Jackson</b> 从查询参数绑定为 DTO —— ADR-056「第 ③ 类载体」的正解。
 *
 * <p><b>为什么不是 servlet 层包装</b>：包装方案（为蛇形参数追加驼峰别名）会让两种写法同时可用、
 * 且在共享内核里改动全部服务的参数语义，属隐式魔法与技术债（已否决并移除）。
 * 本方案是 Spring MVC 的<b>官方扩展点</b>，显式、可发现、且<b>命名策略只有 Jackson 一份</b>。
 *
 * <p><b>绑定语义</b>：
 * <ul>
 *   <li>单值参数 → 直接作为标量；重复参数 → {@code List}（Jackson 可转为 {@code List<Long>} 等）；</li>
 *   <li>未知参数名忽略（{@code FAIL_ON_UNKNOWN_PROPERTIES} 在 Spring Boot 下默认关闭），
 *       因此 <b>只有 snake_case 能绑定</b>，不产生"两种写法都行"的宽松语义；</li>
 *   <li>字段缺省值沿用 DTO 自身初始化（如 {@code private Integer page = 1;}）。</li>
 * </ul>
 *
 * <p>与 {@code @RequestBody} 的对照：请求体由 Jackson 按同一策略反序列化，故
 * <b>报文体与查询参数现在是同一套命名规则</b>，无需为查询参数引入第二套机制。
 */
public class JacksonQueryModelArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectMapper objectMapper;

    public JacksonQueryModelArgumentResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(QueryModel.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Map<String, String[]> raw = webRequest.getParameterMap();
        Map<String, Object> flat = new LinkedHashMap<>(raw.size() * 2);
        for (Map.Entry<String, String[]> entry : raw.entrySet()) {
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                continue;
            }
            flat.put(entry.getKey(), values.length == 1 ? values[0] : Arrays.asList(values));
        }
        return objectMapper.convertValue(flat, parameter.getParameterType());
    }
}

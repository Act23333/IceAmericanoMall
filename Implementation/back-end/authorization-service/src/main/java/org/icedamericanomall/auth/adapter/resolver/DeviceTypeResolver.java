package org.icedamericanomall.auth.adapter.resolver;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 设备类型解析器（适配层）。
 *
 * <p><b>核心原则</b>：服务端识别，不信任前端传值。
 * <ul>
 *   <li>Web/H5：从 User-Agent 解析</li>
 *   <li>App：从 X-Platform 请求头读取（客户端只能声明"我是 App"，不能声明"我是 PC"）</li>
 *   <li>小程序：从 X-Platform 或 User-Agent（含 miniProgram 标识）识别</li>
 * </ul>
 *
 * <p><b>为什么不能由前端传 deviceType？</b>
 * 因为 deviceType 是"同端互斥登录"的安全边界，前端可篡改绕过。
 */
@Slf4j
@Component
public class DeviceTypeResolver {

    /** App 端声明平台用的请求头 */
    private static final String HEADER_PLATFORM = "X-Platform";

    private static final String HEADER_USER_AGENT = "User-Agent";

    /** 微信小程序标识 */
    private static final String WECHAT_MINI_FLAG = "miniProgram";

    /** 微信内置浏览器标识 */
    private static final String WECHAT_FLAG = "MicroMessenger";

    /** 移动端标识 */
    private static final String[] MOBILE_FLAGS = {
            "Android", "iPhone", "iPod", "Windows Phone", "BlackBerry"
    };

    /** 平板标识 */
    private static final String[] TABLET_FLAGS = {
            "iPad", "Android Tablet", "Tablet"
    };

    /**
     * 解析设备类型。
     *
     * @param request HTTP 请求
     * @return 设备类型枚举（无法识别时默认 PC Web）
     */
    public PlatformEnum resolve(HttpServletRequest request) {
        // 1. 优先从 X-Platform 请求头识别（App / 小程序）
        String platformHeader = request.getHeader(HEADER_PLATFORM);
        if (StringUtils.hasText(platformHeader)) {
            PlatformEnum platform = parsePlatformHeader(platformHeader);
            if (platform != null) {
                return platform;
            }
        }

        // 2. 从 User-Agent 识别（Web / H5 / 微信）
        String userAgent = request.getHeader(HEADER_USER_AGENT);
        if (!StringUtils.hasText(userAgent)) {
            // 没有 UA，默认按 PC Web 处理
            return PlatformEnum.WEB;
        }
        return parseFromUserAgent(userAgent);
    }

    /**
     * 解析 X-Platform 请求头。
     * <p>App / 小程序会主动声明自己的平台。
     */
    private PlatformEnum parsePlatformHeader(String platform) {
        String lower = platform.toLowerCase();
        return switch (lower) {
            case "ios" -> PlatformEnum.APP_IOS;
            case "android" -> PlatformEnum.APP_ANDROID;
            case "mini", "wechat_mini", "miniprogram" -> PlatformEnum.WECHAT_MINI;
            default -> {
                log.warn("未识别的 X-Platform: {}", platform);
                yield null;
            }
        };
    }

    /**
     * 从 User-Agent 解析平台。
     */
    private PlatformEnum parseFromUserAgent(String userAgent) {
        // 1. 微信小程序（UA 里带 miniProgram）
        if (userAgent.contains(WECHAT_MINI_FLAG)) {
            return PlatformEnum.WECHAT_MINI;
        }

        // 2. 微信内置浏览器
        if (userAgent.contains(WECHAT_FLAG)) {
            return PlatformEnum.WECHAT_WEB;
        }

        // 3. 平板
        for (String flag : TABLET_FLAGS) {
            if (userAgent.contains(flag)) {
                return PlatformEnum.TABLET;
            }
        }

        // 4. 手机浏览器
        for (String flag : MOBILE_FLAGS) {
            if (userAgent.contains(flag)) {
                return PlatformEnum.H5;
            }
        }

        // 5. 默认 PC Web
        return PlatformEnum.WEB;
    }
}

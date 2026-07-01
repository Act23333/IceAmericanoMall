package org.icedAmericanoMall.integration.captcha;

/**
 * 人机验证客户端接口。
 */
public interface CaptchaClient<T> {
    boolean verify(T request);
}

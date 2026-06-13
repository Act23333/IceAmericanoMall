package org.noLazy.common.client.captcha;

public interface CaptchaClient<T> {
    boolean verify(T request);
}
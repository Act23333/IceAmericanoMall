package org.icedamericanomall.notification.domain.model.channel;

/**
 * 短信内容（领域值对象，不可变 + 创建即校验）。
 *
 * <p>通道适配器负责把本对象翻译成 ia-integration 的 {@code SmsClient.send(phone, content)} 调用；
 * 领域层不感知厂商、模板与 SDK。
 *
 * @param phone     接收手机号
 * @param content   短信正文（调用方已完成模板渲染）
 * @param requestId 幂等 / 链路追踪标识（透传，可为空）
 * @param scene     业务场景（LOGIN / REGISTER / ORDER_SHIPPED ...，可用于日志归因）
 */
public record SmsMessage(String phone, String content, String requestId, String scene) {

    /** 手机号长度上限（含国际区号余量），仅做防御性校验，格式合法性由上游负责。 */
    private static final int MAX_PHONE_LENGTH = 32;

    /** 紧凑构造器：创建即校验。 */
    public SmsMessage {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("短信接收号码不能为空");
        }
        if (phone.length() > MAX_PHONE_LENGTH) {
            throw new IllegalArgumentException("短信接收号码长度非法");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("短信内容不能为空");
        }
    }
}

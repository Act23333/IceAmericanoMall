package org.icedamericanomall.integration.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 阿里云短信客户端 — {@code aliyun.sms.enabled=true} 时激活（与 {@link MockSmsClient} 互斥）。
 *
 * <pre>
 * application.yml:
 *   aliyun.sms:
 *     enabled: true
 *     access-key-id: your-key
 *     access-key-secret: your-secret
 *     sign-name: 冰美商城
 *     template-code: SMS_123456789
 * </pre>
 *
 * <p>失败语义（V5.1 修正）：初始化失败 / 发送失败<b>抛业务异常</b>
 * （{@link ErrorCode#SMS_SEND_FAILED}）而不是仅记日志后返回 ——
 * 否则上游 notification-service 无法识别失败，会把「没发出去」当成「已发送」。
 *
 * <p>模板参数（V5.2 改造）：不再用字符串拼装 {@code {"code":"1234"}}，
 * 改为把模板变量表交给 Jackson 序列化，消除手拼 JSON 的转义 / 注入风险。
 * 序列化使用本类<b>私有的</b> {@link ObjectMapper}，而非容器里的共享 Bean：
 * 共享 Bean 会带上 {@code spring.jackson.property-naming-strategy}（本项目
 * notification-service 配置为 {@code SNAKE_CASE}），从而把 {@code orderNo} 改写成
 * {@code order_no}，与短信控制台模板里的 {@code ${orderNo}} 对不上。
 * 模板变量名是<b>与短信服务商的契约</b>，必须原样透传。
 */
@Slf4j
@Component
@ConditionalOnClass(Client.class)
@ConditionalOnProperty(name = "aliyun.sms.enabled", havingValue = "true")
public class AliyunSmsClient implements SmsClient {

    /** 阿里云短信服务端点 */
    private static final String SMS_ENDPOINT = "dysmsapi.aliyuncs.com";

    /**
     * 模板变量专用序列化器 —— 独立于容器共享 Bean，保证变量名不被全局命名策略改写。
     * 无状态且线程安全，可作为类级常量复用。
     */
    private static final ObjectMapper TEMPLATE_PARAM_MAPPER = new ObjectMapper();

    private final AliyunSmsProperties properties;
    private Client client;

    public AliyunSmsClient(AliyunSmsProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            Config config = new Config()
                    .setAccessKeyId(properties.getAccessKeyId())
                    .setAccessKeySecret(properties.getAccessKeySecret());
            config.endpoint = SMS_ENDPOINT;
            this.client = new Client(config);
            log.info("阿里云短信客户端初始化成功 templateCode={}", properties.getTemplateCode());
        } catch (Exception e) {
            log.error("阿里云短信客户端初始化失败", e);
        }
    }

    /** 简写形式：{@code code} 作为模板变量 {@code code} 的值，模板取配置中的默认模板。 */
    @Override
    public void send(String phone, String code) {
        send(phone, null, code == null ? Map.of() : Map.of(VAR_CODE, code));
    }

    /**
     * 通用形式：显式模板编码 + 任意模板变量表。
     *
     * @param templateCode   模板编码；为 {@code null} / 空白时回落到
     *                       {@code aliyun.sms.template-code} 配置
     * @param templateParams 模板变量表，key 必须与控制台模板中的 {@code ${key}} 一致
     */
    @Override
    public void send(String phone, String templateCode, Map<String, String> templateParams) {
        if (client == null) {
            log.error("阿里云短信客户端未初始化 phone={}", phone);
            throw new BizException(ErrorCode.SMS_SEND_FAILED, "短信通道未就绪");
        }
        String effectiveTemplate = resolveTemplateCode(templateCode);
        if (effectiveTemplate == null) {
            log.error("短信模板编码缺失 phone={}", phone);
            throw new BizException(ErrorCode.SMS_TEMPLATE_ERROR, "短信模板未配置");
        }
        try {
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(properties.getSignName())
                    .setTemplateCode(effectiveTemplate)
                    .setTemplateParam(toTemplateParamJson(templateParams));
            client.sendSms(request);
            log.info("短信发送成功 phone={}, templateCode={}", phone, effectiveTemplate);
        } catch (Exception e) {
            log.error("阿里云短信发送失败 phone={}, templateCode={}", phone, effectiveTemplate, e);
            throw new BizException(ErrorCode.SMS_SEND_FAILED, "短信发送失败", e);
        }
    }

    /** 入参模板优先，缺省回落配置模板；两者都缺则返回 null 由调用处报错。 */
    private String resolveTemplateCode(String templateCode) {
        if (templateCode != null && !templateCode.isBlank()) {
            return templateCode;
        }
        String configured = properties.getTemplateCode();
        return (configured == null || configured.isBlank()) ? null : configured;
    }

    /**
     * 模板变量表 → 阿里云要求的 JSON 字符串。
     *
     * <p>序列化失败属于本地代码 / 变量表缺陷，直接抛业务异常而不是把空对象发出去，
     * 否则用户会收到一条「变量全是空白」的短信。
     */
    private static String toTemplateParamJson(Map<String, String> templateParams) {
        try {
            return TEMPLATE_PARAM_MAPPER.writeValueAsString(
                    templateParams == null ? Map.of() : templateParams);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SMS_TEMPLATE_ERROR, "短信模板参数序列化失败", e);
        }
    }
}

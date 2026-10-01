package org.icedamericanomall.integration.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mock 短信客户端 —— 本地开发 / 测试默认激活，只把短信内容打到日志，不产生真实外呼。
 *
 * <p>激活条件：{@code aliyun.sms.enabled} 缺省或为 false。
 * {@code true} 时由 {@link AliyunSmsClient} 接管（两者互斥，不会同时存在）。
 *
 * <p>历史问题修复（V5.1）：本类原先标注 {@code @Primary} 且无 {@code @ConditionalOnProperty}，
 * 导致 {@code aliyun.sms.enabled=true} 时真实客户端仍被 Mock 遮蔽（真实短信永远发不出去）。
 * 现改为与 {@code AliyunSmsClient} 互斥的条件装配，与 {@code MockEmailClient / RealEmailClient}
 * 的做法保持一致。
 *
 * <p>条件装配对齐标准（V5.2）：Mock 侧一律
 * {@code @ConditionalOnProperty(name = "<namespace>.enabled", havingValue = "false", matchIfMissing = true)}，
 * Real 侧一律 {@code havingValue = "true"} + {@code @ConditionalOnClass(<SDK>) }。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aliyun.sms.enabled", havingValue = "false", matchIfMissing = true)
public class MockSmsClient implements SmsClient {

    /** 简写形式：委托通用形式，语义完全一致。 */
    @Override
    public void send(String phone, String code) {
        send(phone, null, code == null ? Map.of() : Map.of(VAR_CODE, code));
    }

    /** 通用形式：把模板编码与全部模板变量打到日志，便于本地联调核对模板变量名。 */
    @Override
    public void send(String phone, String templateCode, Map<String, String> templateParams) {
        log.info("===== MOCK SMS =====");
        log.info("To: {}", phone);
        log.info("TemplateCode: {}", templateCode);
        log.info("Params: {}", templateParams);
        log.info("===== END MOCK SMS =====");
    }
}

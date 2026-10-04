package org.icedamericanomall.client;

import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.dto.FreightTemplateQueryCO;
import org.icedamericanomall.fallback.FreightTemplateClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 运费模板查询 Feign 客户端（F2 运费模型：trade-service → item-service，契约 §4.14.17 I43）。
 *
 * <p>调用方：trade-service 下单编排（Normal/Direct 策略）——按订单内商品绑定的模板 ID 批量取
 * 启用模板，计算并快照 {@code orders.post_fee}。
 *
 * <p><b>降级语义：fail-closed（抛 {@code BizException}）</b>，属 ADR-011 三分法的
 * <b>② 判别型查询</b>：
 * <ul>
 *   <li>本查询的返回值会被调用方解释为业务事实「商家有/没有启用模板」——空集合意味着
 *       {@code postFee = 0}（免邮）；</li>
 *   <li>若在 item-service 抖动时静默降级为空集合，等于「系统故障伪装成免邮」，平台替买家
 *       承担了运费损失且无任何可观测信号（错误信息污染 + 资损）。</li>
 * </ul>
 * 故此处<b>必须</b>抛异常，让下单失败（调用方可重试），绝不允许默认免邮静默降级。
 *
 * <p>⚠️ 注意与契约 §4.14.17 正文的偏差：契约草拟的降级口径是「取不到模板按商家无启用模板处理
 * → postFee=0，不阻断下单」，与本类实现的 fail-closed 相反；实现方按 ADR-011 判别型查询口径收敛为
 * fail-closed（契约待 product-manager 同步）。
 */
@FeignClient(name = "item-service", path = "/internal/item/freight-template",
        contextId = "freightTemplate", fallbackFactory = FreightTemplateClientFallback.class)
public interface FreightTemplateClient {

    /**
     * 批量查询商家启用运费模板（含商家默认模板）。
     *
     * @param query 查询条件（sellerId 必填；templateIds 可空 = 仅按 sellerId 查）
     * @return 启用模板列表（含 {@code isDefault} 标记）；无启用模板时为空集合
     */
    @PostMapping("/query")
    List<FreightTemplateCO> query(@RequestBody FreightTemplateQueryCO query);
}

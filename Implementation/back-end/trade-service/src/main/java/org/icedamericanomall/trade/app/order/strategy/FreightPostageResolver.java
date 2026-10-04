package org.icedamericanomall.trade.app.order.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.FreightTemplateClient;
import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.dto.FreightTemplateQueryCO;
import org.icedamericanomall.trade.domain.service.PostageCalculator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 下单运费解析器（F2 运费模型，V5.6）—— 把「订单项 → 生效运费模板」解析为
 * {@link PostageCalculator.Rule} 列表并交给纯函数计价。
 *
 * <p><b>为何放在 strategy 包</b>：本类含 Feign 调用（{@code FreightTemplateClient}），
 * 按硬约束「Feign 只允许出现在编排层 / 策略层」，下单策略（Normal/Direct）在
 * {@code validate}（事务外）阶段完成后调用本类，{@code @Transactional} 的落库仍在 service 层。
 * 本类自身<b>不加</b> {@code @Transactional}。
 *
 * <p><b>生效模板解析规则</b>（04 §十二 12.2 计价规则第 5 条）：
 * 订单项绑定的模板在返回的启用模板集合中 → 用绑定模板；否则（未绑定 / 绑定已停用 / 模板已删除）
 * → 用商家默认模板（{@code isDefault=true}，即该商家最早创建的启用模板）；两者皆无 → 该订单项
 * 不产生规则（兜底不阻断下单）。
 *
 * <p><b>降级语义</b>：{@code FreightTemplateClient} 为 fail-closed（ADR-011 判别型查询）——
 * 查不到模板时抛异常使下单失败，绝不静默按免邮处理（避免「故障伪装成免邮」的资损）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreightPostageResolver {

    private final FreightTemplateClient freightTemplateClient;

    /**
     * 计算订单运费（分）。
     *
     * @param sellerId    商家 ID（= 商家 user_id）；为空（如无归属场景）返回 0
     * @param snapshots   订单项快照（含各商品绑定的 freightTemplateId，可为 null）
     * @param totalAmount 订单商品总额（分，不含运费）
     * @return 运费（分，≥0）
     */
    public int resolve(Long sellerId, List<OrderCreateContext.CartItemSnapshot> snapshots, int totalAmount) {
        if (sellerId == null || snapshots == null || snapshots.isEmpty()) {
            return 0;
        }
        List<Long> templateIds = snapshots.stream()
                .map(OrderCreateContext.CartItemSnapshot::getFreightTemplateId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        FreightTemplateQueryCO query = new FreightTemplateQueryCO();
        query.setSellerId(sellerId);
        query.setTemplateIds(templateIds);
        List<FreightTemplateCO> templates = freightTemplateClient.query(query);
        if (templates == null || templates.isEmpty()) {
            // 商家无任何启用模板 → Phase 1 兜底：运费 0，不阻断下单
            log.debug("商家无启用运费模板，运费兜底为 0: sellerId={}", sellerId);
            return 0;
        }

        Map<Long, FreightTemplateCO> enabledById = new LinkedHashMap<>();
        FreightTemplateCO defaultTemplate = null;
        for (FreightTemplateCO template : templates) {
            if (template == null || template.getId() == null) {
                continue;
            }
            enabledById.put(template.getId(), template);
            if (Boolean.TRUE.equals(template.getIsDefault())) {
                defaultTemplate = template;
            }
        }

        List<PostageCalculator.Rule> rules = new ArrayList<>(snapshots.size());
        for (OrderCreateContext.CartItemSnapshot snapshot : snapshots) {
            FreightTemplateCO bound = snapshot.getFreightTemplateId() == null
                    ? null : enabledById.get(snapshot.getFreightTemplateId());
            // 未绑定 / 绑定模板已停用或不存在 → 回落商家默认模板
            FreightTemplateCO effective = bound != null ? bound : defaultTemplate;
            if (effective != null) {
                rules.add(new PostageCalculator.Rule(effective.getFee(), effective.getFreeThreshold()));
            }
        }
        int postFee = PostageCalculator.calculate(rules, totalAmount);
        log.debug("运费计价: sellerId={}, items={}, totalAmount={}, postFee={}",
                sellerId, snapshots.size(), totalAmount, postFee);
        return postFee;
    }
}

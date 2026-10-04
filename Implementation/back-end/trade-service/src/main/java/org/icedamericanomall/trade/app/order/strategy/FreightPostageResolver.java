package org.icedamericanomall.trade.app.order.strategy;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.FreightTemplateClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.dto.FreightTemplateQueryCO;
import org.icedamericanomall.trade.domain.service.PostageCalculator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * 下单运费解析器（F2 运费模型，V5.6）—— 把「订单项 → 生效运费模板」解析为
 * {@link PostageCalculator.Rule} 列表并交给纯函数计价。
 *
 * <p><b>为何放在 strategy 包</b>：本类含 Feign 调用（{@code FreightTemplateClient}），
 * 按硬约束「Feign 只允许出现在编排层 / 策略层」，下单策略（Normal/Direct）在
 * {@code validate}（事务外）阶段完成后调用本类，{@code @Transactional} 的落库仍在 service 层。
 * 本类自身<b>不加</b> {@code @Transactional}。
 *
 * <p><b>生效模板解析规则</b>（04 §十二 12.2 计价规则第 5 条）：按 sellerId 取商家<b>全量</b>
 * 启用模板集合（{@code isDefault} 标记默认模板），订单项绑定的模板在集合中 → 用绑定模板；
 * 否则（未绑定 / 绑定已停用 / 模板已删除）→ 用商家默认模板；两者皆无 → 该订单项不产生规则
 * （兜底不阻断下单）。
 *
 * <p><b>短 TTL 本地缓存（ADR-037 后果收敛）</b>：I43 原先每次下单一次跨服务查询，高频下单会成为
 * item-service 的读热点；现以 Caffeine 按 sellerId 缓存「该商家全量启用模板集合」——
 * 缓存 miss 时 loader 以「sellerId + 空 templateIds」发起 I43 查询（契约 §4.14.17：空数组 =
 * 仅按 sellerId 查，返回全量启用集合 + 兜底默认模板）。模板变更分钟级生效即可接受，且订单侧
 * 本就快照 {@code orders.post_fee}，短暂陈旧不产生资金错误，故无需事件式失效。
 *
 * <p><b>降级语义</b>：{@code FreightTemplateClient} 为 fail-closed（ADR-011 判别型查询）——
 * 缓存加载失败<b>不落缓存</b>、异常直接上抛使下单失败，绝不静默按免邮处理（避免
 * 「故障伪装成免邮」的资损）；下一次调用将重新发起查询。
 */
@Slf4j
@Component
public class FreightPostageResolver {

    /**
     * 缓存条目上限（商家数量级）—— 防内存失控；超出按 LRU 淘汰，仅损失一次缓存命中。
     */
    private static final long CACHE_MAXIMUM_SIZE = 10_000L;

    /**
     * 写后过期 60 秒 —— 模板变更分钟级生效即可接受；订单侧本就快照 post_fee，
     * 短暂陈旧不产生资金错误，故不需要更短的 TTL 或主动失效。
     */
    private static final Duration CACHE_EXPIRE_AFTER_WRITE = Duration.ofSeconds(60);

    private final FreightTemplateClient freightTemplateClient;
    private final LoadingCache<Long, List<FreightTemplateCO>> templateCache;

    public FreightPostageResolver(FreightTemplateClient freightTemplateClient) {
        this.freightTemplateClient = freightTemplateClient;
        this.templateCache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(CACHE_EXPIRE_AFTER_WRITE)
                .build(this::loadEnabledTemplates);
    }

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
        List<FreightTemplateCO> templates = loadTemplates(sellerId);
        if (templates.isEmpty()) {
            // 商家无任何启用模板 → Phase 1 兜底：运费 0，不阻断下单
            log.debug("商家无启用运费模板，运费兜底为 0: sellerId={}", sellerId);
            return 0;
        }
        return calculate(sellerId, snapshots, totalAmount, templates);
    }

    /** 缓存加载：I43 按「sellerId + 空 templateIds」取该商家全量启用集合 + 兜底默认模板。 */
    private List<FreightTemplateCO> loadEnabledTemplates(Long sellerId) {
        FreightTemplateQueryCO query = new FreightTemplateQueryCO();
        query.setSellerId(sellerId);
        query.setTemplateIds(List.of());
        List<FreightTemplateCO> templates = freightTemplateClient.query(query);
        return templates == null ? List.of() : templates;
    }

    /** 取商家启用模板集合（短 TTL 缓存）；加载失败不落缓存、原始异常上抛（fail-closed）。 */
    private List<FreightTemplateCO> loadTemplates(Long sellerId) {
        try {
            List<FreightTemplateCO> templates = templateCache.get(sellerId);
            return templates == null ? List.of() : templates;
        } catch (CompletionException e) {
            // fail-closed（ADR-011 判别型查询）：把 loader 的原始异常还原上抛，禁止伪装成免邮
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeEx) {
                throw runtimeEx;
            }
            throw new BizException(ErrorCode.SYSTEM_BUSY, "运费计算服务暂不可用");
        }
    }

    /** 由启用模板集合构建「绑定命中 → 否则默认模板」的计价规则并交给纯函数计价。 */
    private int calculate(Long sellerId, List<OrderCreateContext.CartItemSnapshot> snapshots,
                          int totalAmount, List<FreightTemplateCO> templates) {
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

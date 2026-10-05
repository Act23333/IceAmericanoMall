package org.icedamericanomall.marketing.app.promotion;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.domain.model.promotion.Promotion;
import org.icedamericanomall.marketing.domain.repository.PromotionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生效促销查询用例编排（应用层 · 集群内部，V5.8 I44，F5 促销引擎 ADR-043）。
 *
 * <p>调用方：trade-service 价格计算中心（ia-api {@code PromotionClient}，Feign 不入事务）。
 * 返回该店铺「进行中」活动集合（时间窗推导口径，按 id 升序）；<b>空集合 = 无生效活动
 * （合法业务态）</b>，与查询失败严格区分——降级口径为 fail-closed（{@code PromotionClientFallback}
 * 抛 {@code SYSTEM_BUSY}），本端点自身不吞异常。
 */
@Component
@RequiredArgsConstructor
public class PromotionQueryManager {

    private final PromotionRepository promotionRepository;
    private final MarketingConverter marketingConverter;

    /**
     * 查询商家当前生效（进行中）的促销活动集合（I44）。
     *
     * @param query 查询条件（{@code sellerId} 必填）
     * @return 生效中促销列表（CO 为 ia-api 跨服务契约对象）；无生效活动时为空集合
     * @throws BizException {@code sellerId} 缺失（{@link ErrorCode#PARAM_ERROR}，400；
     *                      {@code /internal/**} 以真实状态码 + 极简 JSON 透出，ADR-004）
     */
    public List<PromotionCO> queryActive(PromotionQueryCO query) {
        if (query == null || query.getSellerId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "sellerId 不能为空");
        }
        List<Promotion> promotions =
                promotionRepository.findActiveBySeller(query.getSellerId(), LocalDateTime.now());
        return marketingConverter.toPromotionCOList(promotions);
    }
}

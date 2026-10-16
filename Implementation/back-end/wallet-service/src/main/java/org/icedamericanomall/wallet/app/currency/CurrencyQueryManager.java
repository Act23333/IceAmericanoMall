package org.icedamericanomall.wallet.app.currency;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CurrencyRateCO;
import org.icedamericanomall.wallet.adapter.response.CurrencyVO;
import org.icedamericanomall.wallet.domain.model.currency.Currency;
import org.icedamericanomall.wallet.domain.repository.CurrencyRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 币种查询用例编排（应用层）—— P36 币种列表 + I61 当前汇率（F16 多币种 Phase 1）。
 *
 * <p><b>短 TTL 本地缓存（ADR-041 先例，{@code SpuPromotionManager.tagsFor} 同款 Caffeine）</b>：
 * P36 是高频公开读端点，本地表直读 + 60s 写后过期缓存；写操作（AD67/AD68）后由
 * {@code CurrencyAdminManager} 主动 {@link #evictEnabledListCache()}（写后删缓存，不更新缓存），
 * 缓存失效至多延迟一个 TTL 可见——展示场景可接受。
 *
 * <p><b>降级语义</b>：
 * <ul>
 *   <li>P36 列表：展示型查询 fail-open——本地表读失败返回空列表（前端降级仅 CNY 展示，
 *       对齐 ADR-011 三分法），绝不因币种查询失败而失败；</li>
 *   <li>I61 当前汇率：判别型查询 fail-closed——未启用/未配置 → {@code NO_RESOURCE_FOUND(1103)}
 *       极简透出（ADR-004），由 trade 侧 fail-open 消化（哨兵 -1 不快照，主流程零影响）。</li>
 * </ul>
 */
@Slf4j
@Component
public class CurrencyQueryManager {

    /** 缓存键：启用币种列表全量（单条目语义）。 */
    private static final String CACHE_KEY_ENABLED = "enabled";

    /** 缓存条目上限：单键缓存，1 即够。 */
    private static final long CACHE_MAXIMUM_SIZE = 1L;

    /** 写后过期 60 秒（PRD §17.6 P36「可选 60s 缓存，I44 先例」口径）。 */
    private static final Duration CACHE_EXPIRE_AFTER_WRITE = Duration.ofSeconds(60);

    private final CurrencyRepository currencyRepository;
    private final LoadingCache<String, List<CurrencyVO>> enabledCache;

    public CurrencyQueryManager(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
        this.enabledCache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(CACHE_EXPIRE_AFTER_WRITE)
                .build(this::loadEnabledList);
    }

    /**
     * P36：启用币种 + 当前汇率列表（60s 本地缓存；展示型查询 fail-open）。
     *
     * @return 仅启用币种；本地表读失败返回空列表（前端回落 CNY 展示）
     */
    public List<CurrencyVO> listEnabled() {
        List<CurrencyVO> cached = enabledCache.get(CACHE_KEY_ENABLED);
        return cached == null ? List.of() : cached;
    }

    /**
     * I61：当前汇率（trade 下单快照消费，判别型查询 fail-closed）。
     *
     * @param code 币种代码（ISO 4217，必填）
     * @return 三字段契约对象 {@code code/rateScaled/exponent}
     * @throws BadRequestException 币种代码为空（{@link ErrorCode#PARAM_ERROR}，400）
     * @throws BizException        币种不存在/未启用/汇率未配置
     *                             （{@link ErrorCode#NO_RESOURCE_FOUND}，404 → 1103 极简透出）
     */
    public CurrencyRateCO currentRate(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "币种代码不能为空");
        }
        Currency currency = currencyRepository.findByCode(code);
        if (currency == null || !currency.isEnabled() || currency.rate() == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "币种未启用或汇率未配置: " + code);
        }
        return new CurrencyRateCO(currency.code(), currency.rate().rateScaled(), currency.exponent());
    }

    /** 写操作后主动删除缓存（缓存规范：写后失效，不更新缓存）。 */
    public void evictEnabledListCache() {
        enabledCache.invalidate(CACHE_KEY_ENABLED);
    }

    /**
     * 缓存加载：本地表联查启用币种并映射 VO。
     * <p>展示场景 fail-open：读失败返回空列表（随 Caffeine 缓存 60s，抖动期间不再重试打压），
     * 绝不向公开端点上抛——前端降级仅 CNY 展示（US-CUR-7）。
     */
    private List<CurrencyVO> loadEnabledList(String key) {
        try {
            List<CurrencyVO> list = currencyRepository.listEnabledWithRate().stream()
                    .map(CurrencyQueryManager::toVO)
                    .toList();
            log.debug("[currency][list][load] 启用币种数={}", list.size());
            return list;
        } catch (Exception e) {
            log.warn("[currency][list][fail-open] 币种列表查询失败，返回空列表（前端回落 CNY）: cause={}",
                    e.getMessage());
            return List.of();
        }
    }

    /** 聚合 → C 端视图（契约 §4.2.11 字段逐字映射；未配置汇率的币种 rate 字段为 null）。 */
    private static CurrencyVO toVO(Currency currency) {
        if (currency.rate() == null) {
            return new CurrencyVO(currency.code(), currency.name(), currency.symbol(),
                    currency.exponent(), null, null);
        }
        return new CurrencyVO(currency.code(), currency.name(), currency.symbol(), currency.exponent(),
                currency.rate().rateScaled(), currency.rate().effectTime());
    }
}

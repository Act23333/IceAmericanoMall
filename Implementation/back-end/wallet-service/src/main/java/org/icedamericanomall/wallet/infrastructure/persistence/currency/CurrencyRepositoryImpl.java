package org.icedamericanomall.wallet.infrastructure.persistence.currency;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.wallet.domain.model.currency.Currency;
import org.icedamericanomall.wallet.domain.model.currency.ExchangeRate;
import org.icedamericanomall.wallet.domain.repository.CurrencyRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 币种/汇率仓储实现（基础设施层）—— 落 {@code currency} / {@code exchange_rate} 两表。
 *
 * <p>PO ↔ 领域 record 的转换在本类内联完成（static toDomain/toPO，领域层不感知持久化对象）。
 * 「币种 + 汇率联查」用两表各一次查询按 {@code code} 内存拼装（种子量级为个位数六币种，
 * 无需 SQL join）；汇率 upsert 按 {@code uk_code} 命中更新、未命中插入。
 */
@Repository
@RequiredArgsConstructor
public class CurrencyRepositoryImpl implements CurrencyRepository {

    private final CurrencyMapper currencyMapper;
    private final ExchangeRateMapper exchangeRateMapper;

    @Override
    public List<Currency> listEnabledWithRate() {
        List<CurrencyPO> currencies = currencyMapper.selectList(new LambdaQueryWrapper<CurrencyPO>()
                .eq(CurrencyPO::getStatus, Currency.STATUS_ENABLED)
                .orderByAsc(CurrencyPO::getCode));
        return attachRates(currencies);
    }

    @Override
    public Currency findByCode(String code) {
        CurrencyPO po = currencyMapper.selectOne(new LambdaQueryWrapper<CurrencyPO>()
                .eq(CurrencyPO::getCode, code));
        if (po == null) {
            return null;
        }
        ExchangeRatePO rate = exchangeRateMapper.selectOne(new LambdaQueryWrapper<ExchangeRatePO>()
                .eq(ExchangeRatePO::getCode, code));
        return toDomain(po, toRateDomain(rate));
    }

    /** 管理端联合分页（AD66）：status 可选过滤，code 升序稳定排序（uk_code 唯一，次序确定）。 */
    @Override
    public IPage<Currency> pageWithRate(Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<CurrencyPO> wrapper = new LambdaQueryWrapper<CurrencyPO>()
                .orderByAsc(CurrencyPO::getCode);
        if (status != null) {
            wrapper.eq(CurrencyPO::getStatus, status);
        }
        IPage<CurrencyPO> page = currencyMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, ExchangeRate> rates = loadRates(page.getRecords().stream()
                .map(CurrencyPO::getCode).toList());
        return page.convert(po -> toDomain(po, rates.get(po.getCode())));
    }

    /** upsert 汇率：显式 SET（含 updaterId/remark 置 null），未命中 {@code uk_code} 再插入兜底。 */
    @Override
    public void updateRate(ExchangeRate rate) {
        int updated = exchangeRateMapper.update(null, new LambdaUpdateWrapper<ExchangeRatePO>()
                .eq(ExchangeRatePO::getCode, rate.code())
                .set(ExchangeRatePO::getRateScaled, rate.rateScaled())
                .set(ExchangeRatePO::getSource, rate.source())
                .set(ExchangeRatePO::getEffectTime, rate.effectTime())
                .set(ExchangeRatePO::getUpdaterId, rate.updaterId())
                .set(ExchangeRatePO::getRemark, rate.remark()));
        if (updated == 0) {
            exchangeRateMapper.insert(toRatePO(rate));
        }
    }

    /** 启停币种：条件更新，按业务键命中才生效（未命中交由应用层抛 1103）。 */
    @Override
    public boolean updateStatus(String code, int status) {
        return currencyMapper.update(null, new LambdaUpdateWrapper<CurrencyPO>()
                .eq(CurrencyPO::getCode, code)
                .set(CurrencyPO::getStatus, status)) > 0;
    }

    // ==================== 联查拼装与 PO <-> 聚合 ====================

    /** 币种列表挂汇率：汇率表一次 in 查询按 code 内存拼装（种子量级个位数，无 join 必要）。 */
    private List<Currency> attachRates(List<CurrencyPO> currencies) {
        if (currencies.isEmpty()) {
            return List.of();
        }
        Map<String, ExchangeRate> rates = loadRates(currencies.stream().map(CurrencyPO::getCode).toList());
        return currencies.stream().map(po -> toDomain(po, rates.get(po.getCode()))).toList();
    }

    /** 按 code 批量取汇率（in 查询，一行一币）。 */
    private Map<String, ExchangeRate> loadRates(List<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return exchangeRateMapper.selectList(new LambdaQueryWrapper<ExchangeRatePO>()
                        .in(ExchangeRatePO::getCode, codes)).stream()
                .map(CurrencyRepositoryImpl::toRateDomain)
                .collect(Collectors.toMap(ExchangeRate::code, Function.identity()));
    }

    static Currency toDomain(CurrencyPO po, ExchangeRate rate) {
        return new Currency(po.getCode(), po.getName(), po.getSymbol(), po.getExponent(),
                po.getStatus(), rate);
    }

    static ExchangeRate toRateDomain(ExchangeRatePO po) {
        if (po == null) {
            return null;
        }
        return new ExchangeRate(po.getCode(), po.getRateScaled(), po.getSource(), po.getEffectTime(),
                po.getUpdaterId(), po.getRemark());
    }

    static ExchangeRatePO toRatePO(ExchangeRate rate) {
        ExchangeRatePO po = new ExchangeRatePO();
        po.setCode(rate.code());
        po.setRateScaled(rate.rateScaled());
        po.setSource(rate.source());
        po.setEffectTime(rate.effectTime());
        po.setUpdaterId(rate.updaterId());
        po.setRemark(rate.remark());
        return po;
    }
}

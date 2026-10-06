package org.icedamericanomall.wallet.app.currency;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.wallet.adapter.request.RateUpdateReq;
import org.icedamericanomall.wallet.adapter.request.StatusUpdateReq;
import org.icedamericanomall.wallet.adapter.response.CurrencyAdminVO;
import org.icedamericanomall.wallet.domain.model.currency.Currency;
import org.icedamericanomall.wallet.domain.model.currency.ExchangeRate;
import org.icedamericanomall.wallet.domain.repository.CurrencyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 币种管理端用例编排（应用层 · 平台后台）—— AD66 联合分页 / AD67 人工修正汇率 / AD68 启停
 * （F16 多币种 Phase 1，契约 §4.13.8g）。
 *
 * <p>两个写用例均为纯本地 DB 操作，事务边界在本层（{@code rollbackFor} 全量回滚）。
 * <b>运营口径</b>：AD67 修正与 AD68 停用即时生效于<b>新</b>订单快照与 I61/P36 读侧——
 * 既有订单 {@code display_*} 快照展示不受影响（快照锁汇，不追源表）；写后主动失效
 * P36 的 60s 本地缓存（写后删缓存，不更新缓存）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyAdminManager {

    private final CurrencyRepository currencyRepository;
    private final CurrencyQueryManager currencyQueryManager;

    /**
     * AD66：币种 + 汇率联合分页（status 过滤可选，code 升序稳定排序）。
     *
     * @param status   状态过滤（null=全部）
     * @param pageNum  页码（1 起，缺省 1 由适配层归一）
     * @param pageSize 页大小（缺省 20 由适配层归一）
     * @return 管理端分页（含 source/effectTime/updaterId 留痕，供 stale 告警与人工兜底）
     */
    public IPage<CurrencyAdminVO> page(Integer status, int pageNum, int pageSize) {
        return currencyRepository.pageWithRate(status, pageNum, pageSize).convert(this::toVO);
    }

    /**
     * AD67：人工修正汇率（source=MANUAL，effectTime=当前时刻，updaterId 取自 UserContext）。
     *
     * <p>定点整数直传（10⁻⁶ 刻度，主单位小数由管理端 UI 换算）；汇率恒正不变式由
     * {@link ExchangeRate#manual} 守卫。
     *
     * @param code 币种代码（业务唯一键，路径寻址）
     * @param req  修正请求（rateScaled 必填且 &gt; 0，校验已在适配层与聚合双重把守）
     * @throws BizException 币种不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）；
     *                      汇率非正数（{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRate(String code, RateUpdateReq req) {
        requireCurrencyExists(code);
        ExchangeRate rate = ExchangeRate.manual(code, req.getRateScaled(),
                UserContext.getUserId(), req.getRemark());
        currencyRepository.updateRate(rate);
        currencyQueryManager.evictEnabledListCache();
        log.info("[currency][rate][ok] code={}, rateScaled={}, source=MANUAL, updaterId={}",
                code, req.getRateScaled(), UserContext.getUserId());
    }

    /**
     * AD68：启用/停用币种（停用仅影响 P36 列表与 I61 换算；既有订单快照展示不受影响）。
     *
     * @param code 币种代码（业务唯一键，路径寻址）
     * @param req  启停请求（status 仅允许 0/1，适配层与聚合常量双重把守）
     * @throws BadRequestException 状态值域非法（{@link ErrorCode#PARAM_ERROR}，400）
     * @throws BizException        币种不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String code, StatusUpdateReq req) {
        Currency.requireValidStatus(req.getStatus());
        if (!currencyRepository.updateStatus(code, req.getStatus())) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "币种不存在: " + code);
        }
        currencyQueryManager.evictEnabledListCache();
        log.info("[currency][status][ok] code={}, status={}", code, req.getStatus());
    }

    // ==================== 内部方法 ====================

    /** 币种存在性先查（AD67 的 upsert 以 exchange_rate 为落点，currency 缺失必须在此拦截为 1103）。 */
    private void requireCurrencyExists(String code) {
        if (currencyRepository.findByCode(code) == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "币种不存在: " + code);
        }
    }

    /** 币种聚合 → 管理端视图（不透出技术主键 id；留痕字段逐字透出）。 */
    private CurrencyAdminVO toVO(Currency currency) {
        if (currency.rate() == null) {
            return new CurrencyAdminVO(currency.code(), currency.name(), currency.symbol(),
                    currency.exponent(), currency.status(), null, null, null, null, null);
        }
        return new CurrencyAdminVO(currency.code(), currency.name(), currency.symbol(),
                currency.exponent(), currency.status(), currency.rate().rateScaled(),
                currency.rate().source(), currency.rate().effectTime(),
                currency.rate().updaterId(), currency.rate().remark());
    }
}

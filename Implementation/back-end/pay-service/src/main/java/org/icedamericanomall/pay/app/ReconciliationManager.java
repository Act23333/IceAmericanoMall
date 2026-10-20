package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.integration.payment.ChannelBillItem;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.pay.adapter.assembler.ReconciliationConverter;
import org.icedamericanomall.pay.adapter.response.ReconciliationBatchVO;
import org.icedamericanomall.pay.adapter.response.ReconciliationDetailVO;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationBatch;
import org.icedamericanomall.pay.domain.enums.ReconciliationChannelEnum;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.pay.domain.repository.ReconciliationRepository;
import org.icedamericanomall.pay.domain.service.ReconciliationComparer;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 渠道对账编排 Manager（F4）—— 下载渠道账单 → 领域比对 → 落批次与差异明细。
 *
 * <p><b>职责边界</b>：本类只做编排（渠道下载、防腐转换、落库委托）；
 * 四态比对是纯业务规则，下沉到 {@link ReconciliationComparer}（领域层，无框架依赖）。
 *
 * <p><b>Phase 1 口径</b>：差异仅落库记录供管理端查看，<b>不自动调账</b>（ADR-036）。
 * 渠道账单下载与本地流水读取都在本层（账单下载是通道适配层的 Feign/HTTP 调用，
 * 不可放进事务），写库（先删后写）下沉到 {@link ReconciliationRepository} 的本地事务方法。
 *
 * <pre>
 * Scenario: 当日账单比对生成四态明细
 *   Then 生成批次（status=完成），明细覆盖 匹配 / 渠道有本地无 / 本地有渠道无 / 金额不一致 四态
 * Scenario: 重复执行同日对账幂等（先删后写）
 *   Then 旧批次与明细被删除并重写，仍只有一组 (billDate, channel) 批次（uk_date_channel 兜底）
 * Scenario: 账单获取失败
 *   Then 落一条 status=失败 的批次（同日重跑时被正常结果先删后写覆盖）
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationManager {

    private final ReconciliationRepository reconciliationRepository;
    private final PayOrderRepository payOrderRepository;
    private final PaymentChannelRouter channelRouter;
    private final ReconciliationConverter reconciliationConverter;

    /**
     * 执行对账（AD46 / XXL-Job）。
     *
     * @param billDate    账单日
     * @param channelCode 对账渠道码（{@code WECHAT} / {@code ALIPAY}；<b>空 = 当日全部渠道</b>）
     * @return 本次生成的批次视图（按渠道顺序；指定单渠道时仅一条）
     * @throws org.icedamericanomall.common.exception.BadRequestException 渠道码非法（{@code PARAM_ERROR}）
     */
    public List<ReconciliationBatchVO> run(LocalDate billDate, String channelCode) {
        ReconciliationChannelEnum single = ReconciliationChannelEnum.of(channelCode);
        List<ReconciliationChannelEnum> channels = single == null
                ? List.of(ReconciliationChannelEnum.values())
                : List.of(single);
        List<ReconciliationBatchVO> batches = new ArrayList<>(channels.size());
        for (ReconciliationChannelEnum channel : channels) {
            batches.add(reconciliationConverter.toVO(reconcile(billDate, channel)));
        }
        log.info("[reconcile][done] billDate={}, channels={}, batches={}", billDate, channels.size(), batches.size());
        return batches;
    }

    /**
     * 单渠道对账：下载 → 比对 → 落库（下载失败落失败批次，不中断其它渠道）。
     *
     * <p><b>为什么成功/失败落库各拆一个私有方法</b>：两条路径<b>互斥</b>（下载失败即 return），
     * 单次执行只会发生<b>一个</b>资金写；但「同一方法内 ≥2 个资金写」的写法会被
     * {@code FundTransactionGuardTest} 判红 —— 那是门禁按方法体计数、看不见分支互斥。
     * 拆开后每个方法各只有 1 个写、各自单语句自提交即原子，与门禁的判定口径一致；
     * <b>不登记豁免</b>（豁免表只该留真正无法用事务保护的形态，否则会变成免责名单）。
     */
    private ReconciliationBatch reconcile(LocalDate billDate, ReconciliationChannelEnum channel) {
        List<ChannelBillItem> channelBill;
        try {
            channelBill = download(channel, billDate);
        } catch (Exception e) {
            log.error("[reconcile][download-failed] 账单获取失败: billDate={}, channel={}", billDate, channel, e);
            return saveFailedBatch(billDate, channel);
        }
        return saveSucceededBatch(billDate, channel, channelBill);
    }

    /** 失败路径落库（单写）。 */
    private ReconciliationBatch saveFailedBatch(LocalDate billDate, ReconciliationChannelEnum channel) {
        return reconciliationRepository.saveFailedBatch(billDate, channel.getCode());
    }

    /** 成功路径：读本地流水 → 四态比对 → 落批次与差异明细（单写）。 */
    private ReconciliationBatch saveSucceededBatch(LocalDate billDate, ReconciliationChannelEnum channel,
                                                   List<ChannelBillItem> channelBill) {
        List<PayOrder> localOrders = payOrderRepository.listPaidByDateAndChannel(billDate, channel.getCode());
        ReconciliationComparer.Diff diff = ReconciliationComparer.compare(localOrders, toBillEntries(channelBill));
        return reconciliationRepository.saveBatchResult(billDate, channel.getCode(),
                diff.channelTotal(), diff.localTotal(), diff.details());
    }

    /** 渠道账单下载（按渠道路由到通道适配层）。 */
    private List<ChannelBillItem> download(ReconciliationChannelEnum channel, LocalDate billDate) {
        String date = billDate.toString();
        return switch (channel) {
            case WECHAT -> channelRouter.downloadWechatBill(date);
            case ALIPAY -> channelRouter.downloadAlipayBill(date);
        };
    }

    /**
     * 防腐转换：渠道 SDK 账单条目 → 领域比对入参（ia-integration 类型不进领域层）。
     *
     * <p>账单的「商户订单号」列 = 本平台对外渠道号（{@code pay_order.channel_trade_no}），
     * 字段名沿用渠道协议叫法 {@code outTradeNo}，语义按渠道号传入比对函数。
     */
    private List<ReconciliationComparer.BillEntry> toBillEntries(List<ChannelBillItem> channelBill) {
        return channelBill.stream()
                .map(item -> new ReconciliationComparer.BillEntry(item.outTradeNo(), item.channelTxnId(), item.amount()))
                .toList();
    }

    /** AD44 对账批次分页（管理端，时间倒序，可按渠道/状态/账单日过滤）。 */
    public IPage<ReconciliationBatchVO> pageBatches(int page, int size, String channel, Integer status,
                                                    LocalDate billDate) {
        return reconciliationRepository.pageBatches(page, size, channel, status, billDate)
                .convert(reconciliationConverter::toVO);
    }

    /** AD45 批次差异明细分页（按批次，可按四态 result 过滤）。 */
    public IPage<ReconciliationDetailVO> pageDetails(Long batchId, int page, int size, Integer result) {
        return reconciliationRepository.pageDetails(batchId, page, size, result)
                .convert(reconciliationConverter::toVO);
    }
}

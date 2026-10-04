package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.pay.domain.enums.ReconciliationStatusEnum;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationBatch;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationDetail;
import org.icedamericanomall.pay.domain.repository.ReconciliationRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 渠道对账仓储实现 —— 批次与明细的原子读写，先删后写保证同日同渠道幂等。
 * 逻辑自 {@code ReconciliationServiceImpl} 原样收编（含「为什么直接注入 Mapper」的测试口径）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReconciliationRepositoryImpl implements ReconciliationRepository {

    private final ReconciliationBatchMapper batchMapper;
    private final ReconciliationDetailMapper detailMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationBatch saveBatchResult(LocalDate billDate, String channel, int channelTotal,
                                               int localTotal, List<ReconciliationDetail> details) {
        deleteExisting(billDate, channel);
        ReconciliationBatch batch = insertBatch(billDate, channel,
                ReconciliationStatusEnum.COMPLETED.getCode(), channelTotal, localTotal);
        for (ReconciliationDetail detail : details) {
            detail.setBatchId(batch.getId());
            detailMapper.insert(toDetailPO(detail));
        }
        log.info("[reconcile][saved] billDate={}, channel={}, batchId={}, channelTotal={}, localTotal={}, details={}",
                billDate, channel, batch.getId(), channelTotal, localTotal, details.size());
        return batch;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationBatch saveFailedBatch(LocalDate billDate, String channel) {
        deleteExisting(billDate, channel);
        ReconciliationBatch batch = insertBatch(billDate, channel,
                ReconciliationStatusEnum.FAILED.getCode(), null, null);
        log.warn("[reconcile][failed] 账单获取失败，已记录失败批次: billDate={}, channel={}, batchId={}",
                billDate, channel, batch.getId());
        return batch;
    }

    @Override
    public IPage<ReconciliationBatch> pageBatches(int page, int size, String channel, Integer status,
                                                  LocalDate billDate) {
        var wrapper = Wrappers.<ReconciliationBatchPO>lambdaQuery()
                .orderByDesc(ReconciliationBatchPO::getBillDate)
                .orderByDesc(ReconciliationBatchPO::getId);
        if (channel != null && !channel.isBlank()) {
            wrapper.eq(ReconciliationBatchPO::getChannel, channel);
        }
        if (status != null) {
            wrapper.eq(ReconciliationBatchPO::getStatus, status);
        }
        if (billDate != null) {
            wrapper.eq(ReconciliationBatchPO::getBillDate, billDate);
        }
        return batchMapper.selectPage(new Page<>(page, size), wrapper)
                .convert(ReconciliationRepositoryImpl::toDomain);
    }

    @Override
    public IPage<ReconciliationDetail> pageDetails(Long batchId, int page, int size, Integer result) {
        var wrapper = Wrappers.<ReconciliationDetailPO>lambdaQuery()
                .eq(ReconciliationDetailPO::getBatchId, batchId)
                .orderByAsc(ReconciliationDetailPO::getId);
        if (result != null) {
            wrapper.eq(ReconciliationDetailPO::getResult, result);
        }
        return detailMapper.selectPage(new Page<>(page, size), wrapper)
                .convert(ReconciliationRepositoryImpl::toDetailDomain);
    }

    /**
     * 删除同日同渠道的旧批次与其全部明细（先删后写的「删」）。
     *
     * <p>按 {@code (bill_date, channel)} 定位（{@code uk_date_channel} 唯一索引覆盖，最多一行）：
     * 先删明细（按 {@code batch_id}）再删批次，避免留下孤儿明细。
     */
    private void deleteExisting(LocalDate billDate, String channel) {
        List<ReconciliationBatchPO> existingList = batchMapper.selectList(
                Wrappers.<ReconciliationBatchPO>lambdaQuery()
                        .eq(ReconciliationBatchPO::getBillDate, billDate)
                        .eq(ReconciliationBatchPO::getChannel, channel));
        if (existingList == null || existingList.isEmpty()) {
            return;
        }
        for (ReconciliationBatchPO existing : existingList) {
            detailMapper.delete(Wrappers.<ReconciliationDetailPO>lambdaQuery()
                    .eq(ReconciliationDetailPO::getBatchId, existing.getId()));
            batchMapper.deleteById(existing.getId());
            log.info("[reconcile][rewrite] 旧批次已删除并重写: billDate={}, channel={}, oldBatchId={}",
                    billDate, channel, existing.getId());
        }
    }

    private ReconciliationBatch insertBatch(LocalDate billDate, String channel, int status,
                                            Integer channelTotal, Integer localTotal) {
        ReconciliationBatchPO batch = new ReconciliationBatchPO();
        batch.setBillDate(billDate);
        batch.setChannel(channel);
        batch.setStatus(status);
        batch.setChannelTotal(channelTotal);
        batch.setLocalTotal(localTotal);
        batchMapper.insert(batch);
        return toDomain(batch);
    }

    private static ReconciliationBatch toDomain(ReconciliationBatchPO po) {
        ReconciliationBatch batch = new ReconciliationBatch();
        batch.setId(po.getId());
        batch.setBillDate(po.getBillDate());
        batch.setChannel(po.getChannel());
        batch.setStatus(po.getStatus());
        batch.setChannelTotal(po.getChannelTotal());
        batch.setLocalTotal(po.getLocalTotal());
        batch.setCreateTime(po.getCreateTime());
        batch.setUpdateTime(po.getUpdateTime());
        return batch;
    }

    private static ReconciliationBatchPO toBatchPO(ReconciliationBatch batch) {
        ReconciliationBatchPO po = new ReconciliationBatchPO();
        po.setId(batch.getId());
        po.setBillDate(batch.getBillDate());
        po.setChannel(batch.getChannel());
        po.setStatus(batch.getStatus());
        po.setChannelTotal(batch.getChannelTotal());
        po.setLocalTotal(batch.getLocalTotal());
        return po;
    }

    private static ReconciliationDetail toDetailDomain(ReconciliationDetailPO po) {
        ReconciliationDetail detail = new ReconciliationDetail();
        detail.setId(po.getId());
        detail.setBatchId(po.getBatchId());
        detail.setPayOrderNo(po.getPayOrderNo());
        detail.setChannelTxnId(po.getChannelTxnId());
        detail.setChannelAmount(po.getChannelAmount());
        detail.setLocalAmount(po.getLocalAmount());
        detail.setResult(po.getResult());
        detail.setCreateTime(po.getCreateTime());
        detail.setUpdateTime(po.getUpdateTime());
        return detail;
    }

    private static ReconciliationDetailPO toDetailPO(ReconciliationDetail detail) {
        ReconciliationDetailPO po = new ReconciliationDetailPO();
        po.setId(detail.getId());
        po.setBatchId(detail.getBatchId());
        po.setPayOrderNo(detail.getPayOrderNo());
        po.setChannelTxnId(detail.getChannelTxnId());
        po.setChannelAmount(detail.getChannelAmount());
        po.setLocalAmount(detail.getLocalAmount());
        po.setResult(detail.getResult());
        return po;
    }
}

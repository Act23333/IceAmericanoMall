package org.icedamericanomall.pay.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationBatch;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationDetail;

import java.time.LocalDate;
import java.util.List;

/**
 * 渠道对账仓储（领域端口）—— 实现方：{@code infrastructure.persistence.ReconciliationRepositoryImpl}。
 *
 * <p>幂等口径：同日同渠道先删后写（uk_date_channel 唯一锚点兜底并发）。
 * 分页查询返回 MyBatis-Plus {@code IPage} 信封：管理端分页是既有 HTTP 契约
 * （records/total/size/current/pages），属有界务实豁免。
 */
public interface ReconciliationRepository {

    /**
     * 落批次与差异明细（先删后写；batchId 回填进每条明细）。
     *
     * @return 已落库的批次（含 id）
     */
    ReconciliationBatch saveBatchResult(LocalDate billDate, String channel, int channelTotal,
                                        int localTotal, List<ReconciliationDetail> details);

    /** 账单获取失败时落「失败」批次（同日重跑被正常结果覆盖）。 */
    ReconciliationBatch saveFailedBatch(LocalDate billDate, String channel);

    /** 管理端批次分页（channel/status/billDate 可选过滤，时间倒序）。 */
    IPage<ReconciliationBatch> pageBatches(int page, int size, String channel, Integer status, LocalDate billDate);

    /** 管理端明细分页（按批次，result 可选过滤，id 升序）。 */
    IPage<ReconciliationDetail> pageDetails(Long batchId, int page, int size, Integer result);
}

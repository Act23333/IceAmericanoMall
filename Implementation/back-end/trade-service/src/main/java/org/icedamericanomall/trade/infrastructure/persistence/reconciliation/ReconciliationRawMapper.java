package org.icedamericanomall.trade.infrastructure.persistence.reconciliation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 渠道原始报文 Mapper（分片存储）。
 *
 * <p>重拉与逐笔同一事务先删后插；{@code UNIQUE(batch_no, seq)} 保证不出现重叠分片。
 */
@Mapper
public interface ReconciliationRawMapper extends BaseMapper<ReconciliationRawPO> {

    /**
     * 条件删分片（与逐笔同一条判据，保证两边要么都删、要么都不动）。
     *
     * @param batchNo     批次号
     * @param pendingCode PENDING 的码值
     * @param fetchedCode FETCHED 的码值
     * @return 删除行数；0 = 不可重拉
     */
    @Delete("DELETE FROM reconciliation_raw WHERE batch_no = #{batchNo} AND EXISTS ("
            + "SELECT 1 FROM reconciliation_batch b WHERE b.batch_no = #{batchNo} "
            + "AND b.superseded_by IS NULL AND b.status IN (#{pendingCode}, #{fetchedCode}))")
    int deleteByBatchIfRefetchable(@Param("batchNo") String batchNo,
                                   @Param("pendingCode") int pendingCode,
                                   @Param("fetchedCode") int fetchedCode);

    @Select("SELECT COUNT(*) FROM reconciliation_raw WHERE batch_no = #{batchNo}")
    int countByBatch(@Param("batchNo") String batchNo);

    /** 读分片（按 seq 升序；重组原文用）。 */
    @Select("SELECT * FROM reconciliation_raw WHERE batch_no = #{batchNo} ORDER BY seq ASC")
    List<ReconciliationRawPO> selectByBatch(@Param("batchNo") String batchNo);
}

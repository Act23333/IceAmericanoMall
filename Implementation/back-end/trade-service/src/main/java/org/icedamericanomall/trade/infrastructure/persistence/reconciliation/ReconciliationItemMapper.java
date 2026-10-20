package org.icedamericanomall.trade.infrastructure.persistence.reconciliation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 对账逐笔 Mapper。
 *
 * <p>重拉时**同事务先删后插**（§1.54③）；删除按批次，不按 item 主键。
 */
@Mapper
public interface ReconciliationItemMapper extends BaseMapper<ReconciliationItemPO> {

    /**
     * 条件删：只有批次仍处于可重拉状态时才删（重拉前清空，不出现半旧半新）。
     *
     * <p><b>为什么不先查后删</b>：两步之间存在窗口，别人可能在这期间把批次推进到闭环，
     * 而我们照样把它的逐笔删了。把状态判断写进同一条 DELETE 的 {@code EXISTS} 里，窗口消失。
     *
     * <p>状态值用命名参数传入（{@code ReconciliationStatus.getCode()}），
     * 硬编码会被 {@code ReconciliationMapperNoHardcodedStatusGuardTest} 拦下。
     *
     * @param batchNo     批次号
     * @param pendingCode PENDING 的码值
     * @param fetchedCode FETCHED 的码值
     * @return 删除行数；0 = 批次不存在或已不可重拉（调用方据此区分异常）
     */
    @Delete("DELETE FROM reconciliation_item WHERE batch_no = #{batchNo} AND EXISTS ("
            + "SELECT 1 FROM reconciliation_batch b WHERE b.batch_no = #{batchNo} "
            + "AND b.superseded_by IS NULL AND b.status IN (#{pendingCode}, #{fetchedCode}))")
    int deleteByBatchIfRefetchable(@Param("batchNo") String batchNo,
                                   @Param("pendingCode") int pendingCode,
                                   @Param("fetchedCode") int fetchedCode);

    /** 读某批次的逐笔（报表与测试用）。 */
    @Select("SELECT * FROM reconciliation_item WHERE batch_no = #{batchNo} ORDER BY id ASC")
    List<ReconciliationItemPO> selectByBatch(@Param("batchNo") String batchNo);

    /** 统计某批次的逐笔数（重拉幂等断言用）。 */
    @Select("SELECT COUNT(*) FROM reconciliation_item WHERE batch_no = #{batchNo}")
    int countByBatch(@Param("batchNo") String batchNo);
}

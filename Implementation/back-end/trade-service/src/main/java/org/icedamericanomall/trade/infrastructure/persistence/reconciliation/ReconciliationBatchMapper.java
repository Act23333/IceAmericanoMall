package org.icedamericanomall.trade.infrastructure.persistence.reconciliation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 对账批次 Mapper。
 *
 * <p>只做映射与条件更新；"状态是否允许推进"的白名单校验在仓储层（§1.56⑤）。
 */
@Mapper
public interface ReconciliationBatchMapper extends BaseMapper<ReconciliationBatchPO> {

    /**
     * 取当日有效批次。
     *
     * <p>条件用 {@code active_key = '0'}（**与唯一键同列**，能走索引），
     * 它与 {@code superseded_by IS NULL} 等价：生成列对有效批次恒为 {@code '0'}，
     * 对已取代批次恒为 {@code superseded_by#batch_no}（必含 {@code #}，不可能等于 {@code '0'}）。
     *
     * <p>唯一性由 {@code UNIQUE(provider, biz_date, active_key)} 保证，所以这里最多一行 ——
     * 这不是"约定"，是数据库约束。
     *
     * @param provider 渠道标识
     * @param bizDate  账期
     * @return 有效批次，可能为 null
     */
    @Select("SELECT * FROM reconciliation_batch WHERE provider = #{provider} "
            + "AND biz_date = #{bizDate} AND active_key = '0'")
    ReconciliationBatchPO selectActive(@Param("provider") String provider, @Param("bizDate") LocalDate bizDate);

    /**
     * 读当前状态（{@code Optional}：查不到 = 批次不存在，与 {@code findActiveBatch} 一致）。
     *
     * <p>用途：条件删/条件更新 0 行后区分"批次不存在"与"状态已变"（§1.63②）。
     * 注意返回值只是**某一时刻的快照**，异常信息里要标注"可能为旧值"。
     *
     * @param batchNo 批次号
     * @return 状态码；批次不存在时为空
     */
    @Select("SELECT status FROM reconciliation_batch WHERE batch_no = #{batchNo}")
    java.util.Optional<Integer> selectStatus(@Param("batchNo") String batchNo);

    /**
     * 条件迁移状态：只有当前状态等于 {@code expectedFrom} 才更新。
     *
     * @param batchNo     批次号
     * @param expectedFrom 期望的当前状态
     * @param to           目标状态
     * @return 影响行数；0 = 状态已变（调用方抛并发异常，见 §1.57③）
     */
    @Update("UPDATE reconciliation_batch SET status = #{to} WHERE batch_no = #{batchNo} AND status = #{expectedFrom}")
    int claimStatus(@Param("batchNo") String batchNo, @Param("expectedFrom") int expectedFrom,
                    @Param("to") int to);
}

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

    /** 删除某批次的全部逐笔（重拉前清空，保证不出现半旧半新）。 */
    @Delete("DELETE FROM reconciliation_item WHERE batch_no = #{batchNo}")
    int deleteByBatch(@Param("batchNo") String batchNo);

    /** 读某批次的逐笔（报表与测试用）。 */
    @Select("SELECT * FROM reconciliation_item WHERE batch_no = #{batchNo} ORDER BY id ASC")
    List<ReconciliationItemPO> selectByBatch(@Param("batchNo") String batchNo);

    /** 统计某批次的逐笔数（重拉幂等断言用）。 */
    @Select("SELECT COUNT(*) FROM reconciliation_item WHERE batch_no = #{batchNo}")
    int countByBatch(@Param("batchNo") String batchNo);
}

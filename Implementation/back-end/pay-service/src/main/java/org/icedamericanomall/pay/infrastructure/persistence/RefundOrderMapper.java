package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款单 Mapper —— 入参/出参均为 PO（本服务实体即 PO）。
 *
 * <p>除 BaseMapper 标准方法外仅一条显式 SQL：悬挂退款补偿的筛选口径（状态 + 阈值 + 批量上限）
 * 是任务级契约，收口在 Mapper 层显式可审计（与 {@code AfterSaleMapper.updateStatusConditionally}
 * 等条件更新同理——不变式需要契约化时用显式 SQL 而非 Wrapper 拼接）。
 */
public interface RefundOrderMapper extends BaseMapper<RefundOrderPO> {

    /**
     * 悬挂补偿查询：「处理中」且创建时间早于阈值的退款单，按创建时间升序截取 limit 条。
     *
     * <p>LIMIT 由应用层常量（有界批量）传入，不拼接外部输入，无注入面。
     *
     * @param status    处理中状态码
     * @param threshold 悬挂判定阈值
     * @param limit     单轮最大条数
     * @return 悬挂退款单 PO 列表
     */
    @Select("SELECT * FROM refund_order WHERE status = #{status} AND create_time < #{threshold} "
            + "ORDER BY create_time ASC LIMIT #{limit}")
    List<RefundOrderPO> selectStaleProcessing(@Param("status") int status,
                                              @Param("threshold") LocalDateTime threshold,
                                              @Param("limit") int limit);
}

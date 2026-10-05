package org.icedamericanomall.trade.infrastructure.persistence.invoice;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 常用发票抬头 Mapper —— 默认抬头唯一语义（{@code uk_user_default} 多 NULL）在此承载。
 */
@Mapper
public interface InvoiceTitleMapper extends BaseMapper<InvoiceTitlePO> {

    /**
     * 清除用户默认抬头标记（is_default→NULL）—— 换设默认的前置步，
     * 保证「一用户至多一条默认抬头」不变量。
     *
     * @param userId 用户 user_id
     * @return 受影响行数（0/1）
     */
    @Update("UPDATE invoice_title SET is_default = NULL, update_time = NOW() "
            + "WHERE user_id = #{userId} AND is_default = 1")
    int clearDefaultByUserId(@Param("userId") Long userId);

    /**
     * 全量更新抬头（显式 SQL，INV8 语义）—— 税号三字段与 is_default 直接覆盖赋值，
     * <b>允许置 NULL</b>（企业转个人清税号 / 取消默认标记，MP updateById 忽略空值不适用）。
     * 显式带 {@code deleted = 0}（@TableLogic 只作用于 MP 注入 SQL，自定义 SQL 需自带）。
     *
     * @param po 抬头 PO（id + userId 组成 WHERE 双条件，防越权）
     * @return 受影响行数（0=不存在 / 非本人 / 已软删）
     */
    @Update("UPDATE invoice_title SET type = #{po.type}, title = #{po.title}, "
            + "tax_no_cipher = #{po.taxNoCipher}, tax_no_hash = #{po.taxNoHash}, tax_no_mask = #{po.taxNoMask}, "
            + "is_default = #{po.isDefault}, update_time = NOW() "
            + "WHERE id = #{po.id} AND user_id = #{po.userId} AND deleted = 0")
    int updateTitleById(@Param("po") InvoiceTitlePO po);
}

package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商家资金账户 Mapper —— 两个写都是<b>单语句原子操作</b>（不读-改-写）。
 *
 * <p><b>刻意都不带额度条件</b>：额度（「可提现额 ≥ amount」）属于<b>申请侧</b>
 * （{@code WithdrawalManager#applyWithdrawal}，第二步做成条件更新预占 {@code frozen_amount}），
 * 不属于审核侧 —— 审核只是在断言「钱已经按申请时的额度打出去了」。
 * 把额度条件塞进打款记账会制造一种假的拒绝：申请时通过了、打款时因为期间余额变化而被拒，
 * 而钱其实已经出去了（状态与账两头都不对）。
 *
 * <p>为什么不用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 兜底建行：本方法族要在 H2（MySQL 兼容模式）
 * 上跑自检用例，方言特性会把「账对不对」的验证绑到「数据库支不支持那句语法」上。
 * 建行由实现类先 UPDATE、未命中再 INSERT 完成，并发首建由唯一键 {@code uk_seller_id} 拦住（见实现类注释）。
 *
 * <p>刻意不显式写 {@code update_time}：MySQL 侧由列定义 {@code ON UPDATE CURRENT_TIMESTAMP} 维护，
 * 少一处「应用时钟 vs 数据库时钟」的口径分叉。
 */
@Mapper
public interface SellerFundAccountMapper extends BaseMapper<SellerFundAccountPO> {

    /** 累计已结算 += amount（行不存在时 0 行）。 */
    @Update("UPDATE seller_fund_account SET settled_total = settled_total + #{amount} "
            + "WHERE seller_id = #{sellerId}")
    int increaseSettledTotal(@Param("sellerId") Long sellerId, @Param("amount") int amount);

    /**
     * 累计已打款 += amount（行不存在时 0 行）。
     *
     * <p><b>无条件</b>：审核侧不做额度判断（见类注释）。唯一调用方是「审核为已打款」，
     * 而状态迁移本身已是条件更新（待审核 → 终态，0 行即幂等），重复审核到不了这里。
     */
    @Update("UPDATE seller_fund_account SET paid_total = paid_total + #{amount} "
            + "WHERE seller_id = #{sellerId}")
    int increasePaidTotal(@Param("sellerId") Long sellerId, @Param("amount") int amount);
}

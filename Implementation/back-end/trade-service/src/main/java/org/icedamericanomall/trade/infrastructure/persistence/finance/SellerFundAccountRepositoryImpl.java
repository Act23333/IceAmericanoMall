package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.domain.model.finance.SellerFundAccount;
import org.icedamericanomall.trade.domain.repository.SellerFundAccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商家资金账户仓储实现 —— PO 与聚合互转在本类完成。
 *
 * <p><b>「首建行」的并发口径（写清，别当偶然）</b>：两个写入方法都先做<b>无条件</b>的增量更新；
 * 只有当它影响 0 行（该商家还没有账户行）时才插入新行。两个执行者同时首建时，后插入的那个会撞
 * {@code uk_seller_id}；MyBatis-Spring 把它翻译成 {@link DuplicateKeyException}，本类随后改写为增量，
 * 语义等价于 upsert，且不依赖任何方言语法（{@code INSERT ... ON DUPLICATE KEY UPDATE} 在 H2 兼容模式下
 * 不是可依赖的契约，而这段代码必须被 H2 自检用例原样跑通）。
 *
 * <p><b>为什么本类自己带 {@code @Transactional}</b>：「先更新、0 行再插入」在同一个方法里是多条写语句，
 * 按编码规则多语句本地写的事务归属落在仓储实现，而不是靠调用方记得开事务。
 * 传播行为是 REQUIRED：调用方（结算/审核）已有事务时直接加入，不会开第二个事务。
 *
 * <p><b>两个方法都不带额度条件</b>：额度校验属申请侧（第二步的条件预占），审核侧只做记账
 * （理由见 {@link org.icedamericanomall.trade.domain.repository.SellerFundAccountRepository}）。
 */
@Repository
@RequiredArgsConstructor
public class SellerFundAccountRepositoryImpl implements SellerFundAccountRepository {

    private final SellerFundAccountMapper sellerFundAccountMapper;

    @Override
    public SellerFundAccount findBySellerId(Long sellerId) {
        return toDomain(sellerFundAccountMapper.selectOne(new LambdaQueryWrapper<SellerFundAccountPO>()
                .eq(SellerFundAccountPO::getSellerId, sellerId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSettled(Long sellerId, int amount) {
        if (sellerFundAccountMapper.increaseSettledTotal(sellerId, amount) > 0) {
            return;
        }
        try {
            sellerFundAccountMapper.insert(newAccount(sellerId, amount, 0));
        } catch (DuplicateKeyException concurrentFirstWrite) {
            // 并发首建：行已被另一个事务建好，退回增量累加（否则这一笔结算会整条回滚）
            sellerFundAccountMapper.increaseSettledTotal(sellerId, amount);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPaid(Long sellerId, int amount) {
        if (sellerFundAccountMapper.increasePaidTotal(sellerId, amount) > 0) {
            return;
        }
        try {
            // 兜底建行：账户行缺失说明这笔提现早于资金账户的建立（第一步的残留形态）。
            // 宁可建行记账（可用额可能显示为负 = fail-closed，申请侧会拒绝后续提现），也不吞掉一笔真实打款。
            sellerFundAccountMapper.insert(newAccount(sellerId, 0, amount));
        } catch (DuplicateKeyException concurrentFirstWrite) {
            sellerFundAccountMapper.increasePaidTotal(sellerId, amount);
        }
    }

    @Override
    public List<SellerFundAccount> findAll() {
        return sellerFundAccountMapper.selectList(null).stream()
                .map(SellerFundAccountRepositoryImpl::toDomain).toList();
    }

    // ==================== PO <-> 聚合 ====================

    private static SellerFundAccountPO newAccount(Long sellerId, int settledTotal, int paidTotal) {
        SellerFundAccountPO po = new SellerFundAccountPO();
        po.setSellerId(sellerId);
        po.setSettledTotal(settledTotal);
        po.setFrozenAmount(0);
        po.setPaidTotal(paidTotal);
        return po;
    }

    static SellerFundAccount toDomain(SellerFundAccountPO po) {
        if (po == null) {
            return null;
        }
        SellerFundAccount account = new SellerFundAccount();
        account.setId(po.getId());
        account.setSellerId(po.getSellerId());
        account.setSettledTotal(po.getSettledTotal());
        account.setFrozenAmount(po.getFrozenAmount());
        account.setPaidTotal(po.getPaidTotal());
        account.setCreateTime(po.getCreateTime());
        account.setUpdateTime(po.getUpdateTime());
        return account;
    }
}

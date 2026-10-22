package org.icedamericanomall.trade.infrastructure.persistence.finance;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.domain.port.PayoutNoFactory;
import org.icedamericanomall.trade.domain.repository.WithdrawalPayoutAttemptRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 出款尝试账仓储实现。
 *
 * <p>四条实现约束见端口类注释：锁提现单行、事务内无自调用、撞键即抛不重试、上限收敛在此。
 */
@Repository
@RequiredArgsConstructor
public class WithdrawalPayoutAttemptRepositoryImpl implements WithdrawalPayoutAttemptRepository {

    /** 允许的连续「明确失败」次数；达到后停止自动重发，交人工。 */
    public static final int MAX_FAILED_ATTEMPTS = 3;

    private final WithdrawalPayoutAttemptMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Attempt claimAndMark(Long withdrawalId, String channel, PayoutNoFactory factory, LocalDateTime now) {
        if (withdrawalId == null) {
            throw new IllegalArgumentException("withdrawalId 不能为空");
        }
        // ① 先锁提现单行：锁只在事务内有效（本方法 @Transactional，且不得被同类方法自调用）
        WithdrawalPO locked = mapper.lockWithdrawal(withdrawalId);
        if (locked == null) {
            throw new IllegalArgumentException("提现单不存在: " + withdrawalId);
        }
        // ② 锁内查非终态：存在即拒绝（并发下这是唯一可靠的判据）
        if (mapper.selectNonTerminal(withdrawalId, AttemptState.REQUESTED.ordinal() + 1,
                AttemptState.UNKNOWN.ordinal() + 1) != null) {
            throw new IllegalStateException("提现单已有未收敛的出款尝试，不能重复占号: " + withdrawalId);
        }
        // ③ 上限只数「明确失败」；CANCELLED 是主动终止，不计入
        int failed = mapper.countByState(withdrawalId, AttemptState.FAILED.ordinal() + 1);
        if (failed >= MAX_FAILED_ATTEMPTS) {
            throw new IllegalStateException("出款失败重试已达上限(" + MAX_FAILED_ATTEMPTS + ")，需人工介入: "
                    + withdrawalId);
        }
        // ④ 占号：序号只增不减，生成即占号
        int nextAttemptNo = mapper.selectMaxAttemptNo(withdrawalId) + 1;
        WithdrawalPayoutAttemptPO po = new WithdrawalPayoutAttemptPO();
        po.setWithdrawalId(withdrawalId);
        po.setAttemptNo(nextAttemptNo);
        // 单号在锁内生成：序号只有这里知道，规则由 domain 的工厂提供（纯函数）
        // withdrawal_no 是 BINARY(16)；转成与 IdGenerator.nextId() 同形态的十六进制字符串后交给工厂
        String payoutNo = factory.create(hex(locked.getWithdrawalNo()), nextAttemptNo);
        po.setPayoutNo(payoutNo);
        po.setChannel(channel);
        po.setState(AttemptState.REQUESTED.ordinal() + 1);
        po.setRequestedAt(now);
        // 撞 UNIQUE(withdrawal_id, attempt_no) 会在这里直接抛出：锁已保证串行，撞键说明有代码绕过锁，不重试
        mapper.insert(po);
        // 先插 attempt 再写指针：反过来一旦事务没生效，就会留下指向不存在尝试的悬空指针
        if (mapper.markAttemptAndPointer(withdrawalId, now, payoutNo) == 0) {
            // 0 行 = 提现单已不是 PAYING：必须抛，让上面的 attempt 插入一起回滚。
            // 不抛的话会留下「有尝试、没指针、没打标」的静默不一致，对账时无法解释。
            Integer actual = mapper.selectWithdrawalStatus(withdrawalId);
            throw new IllegalStateException("提现单状态已非 PAYING，指针未写入: " + withdrawalId
                    + "，当前状态=" + actual + "（PAYING=4）");
        }
        return toAttempt(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateState(String payoutNo, AttemptState from, AttemptState to) {
        // 白名单先于条件更新：条件更新挡"状态已变"，白名单挡"迁移本身不合法"（如 SUCCESS→REQUESTED 倒退）
        if (!WithdrawalPayoutAttemptRepository.canTransit(from, to)) {
            throw new IllegalArgumentException("非法的尝试状态迁移: " + from + " → " + to);
        }
        boolean terminal = to == AttemptState.SUCCESS || to == AttemptState.FAILED
                || to == AttemptState.CANCELLED;
        return mapper.updateStateConditional(payoutNo, from.ordinal() + 1, to.ordinal() + 1,
                terminal ? LocalDateTime.now() : null);
    }

    @Override
    public Optional<Attempt> findActiveAttempt(Long withdrawalId) {
        return Optional.ofNullable(mapper.selectNonTerminal(withdrawalId,
                        AttemptState.REQUESTED.ordinal() + 1, AttemptState.UNKNOWN.ordinal() + 1))
                .map(this::toAttempt);
    }

    /**
     * BINARY(16) → 十六进制字符串（32 字符），与 {@code IdGenerator.nextId()} 同形态。
     *
     * <p>收敛在仓储内部：这是"工厂收到的单号是什么形态"的唯一约定点，
     * 散落在调用处就会出现"工厂收到 16 字节、哈希出另一个号"—— 单号照样生成，但回溯对不上。
     */
    private static String hex(byte[] raw) {
        return java.util.HexFormat.of().formatHex(raw);
    }

    private Attempt toAttempt(WithdrawalPayoutAttemptPO po) {
        return new Attempt(po.getId(), po.getWithdrawalId(), po.getAttemptNo(), po.getPayoutNo(),
                po.getChannel(), AttemptState.values()[po.getState() - 1], po.getChannelRef(),
                po.getRequestedAt(), po.getFinishedAt());
    }
}

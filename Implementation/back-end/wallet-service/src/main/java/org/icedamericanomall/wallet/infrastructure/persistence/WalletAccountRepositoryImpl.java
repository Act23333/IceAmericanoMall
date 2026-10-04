package org.icedamericanomall.wallet.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.wallet.domain.repository.WalletAccountRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 钱包账户仓储实现（基础设施层）—— 落 {@code wallet_account} 表。
 */
@Component
@RequiredArgsConstructor
public class WalletAccountRepositoryImpl implements WalletAccountRepository {

    private final WalletAccountMapper mapper;

    @Override
    public Optional<Integer> findBalanceByUserId(Long userId) {
        return Optional.ofNullable(mapper.selectBalanceByUserId(userId));
    }

    @Override
    public int deduct(Long userId, int amount) {
        return mapper.deduct(userId, amount);
    }

    @Override
    public int credit(Long userId, int amount) {
        return mapper.credit(userId, amount);
    }
}

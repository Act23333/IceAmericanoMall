package org.icedamericanomall.user.infrastructure.persistence.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.enums.MemberLevelEnum;
import org.icedamericanomall.user.domain.model.member.MemberLevelRule;
import org.icedamericanomall.user.domain.repository.MemberLevelRuleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 会员等级规则仓储实现（基础设施层；Phase 1 只读 —— ADR-044）。
 *
 * <p>PO ↔ 领域值对象转换为内联静态 {@link #toDomain}（不建独立 Converter 类）；
 * level code 无法归一化的脏行映射为 null 并被过滤，避免单行数据问题放大为整表查询失败。
 */
@Repository
@RequiredArgsConstructor
public class MemberLevelRuleRepositoryImpl implements MemberLevelRuleRepository {

    private final MemberLevelRuleMapper memberLevelRuleMapper;

    @Override
    public List<MemberLevelRule> findAll() {
        return memberLevelRuleMapper.selectList(new LambdaQueryWrapper<MemberLevelRulePO>()
                        .orderByAsc(MemberLevelRulePO::getSort))
                .stream()
                .map(MemberLevelRuleRepositoryImpl::toDomain)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Optional<MemberLevelRule> findByLevel(MemberLevelEnum level) {
        if (level == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toDomain(memberLevelRuleMapper.selectOne(
                new LambdaQueryWrapper<MemberLevelRulePO>()
                        .eq(MemberLevelRulePO::getLevel, level.getCode())
                        .last("LIMIT 1"))));
    }

    /**
     * PO → 领域值对象。
     *
     * @param dataObject 持久化对象（可为 null）
     * @return 领域值对象；level code 无法归一化时返回 null（由调用方过滤）
     */
    static MemberLevelRule toDomain(MemberLevelRulePO dataObject) {
        if (dataObject == null) {
            return null;
        }
        MemberLevelEnum level = MemberLevelEnum.fromCode(dataObject.getLevel());
        if (level == null) {
            return null;
        }
        return new MemberLevelRule(level, dataObject.getThreshold(),
                dataObject.getDiscountRate(), dataObject.getPointsRate(), dataObject.getSort());
    }
}

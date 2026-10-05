package org.icedamericanomall.user.domain.repository;

import org.icedamericanomall.user.domain.enums.MemberLevelEnum;
import org.icedamericanomall.user.domain.model.member.MemberLevelRule;

import java.util.List;
import java.util.Optional;

/**
 * 会员等级规则仓储（领域层定义，基础设施层实现；F6 会员等级，ADR-044）。
 *
 * <p>Phase 1 只读 —— 规则是 seed 预置的产品决策（管理端编辑留 roadmap，改表 seed 即生效），
 * 故只提供查询端口，不提供写端口。
 */
public interface MemberLevelRuleRepository {

    /**
     * 全量规则（{@code sort} 升序，与门槛序等价；空表返回空列表）。
     *
     * <p>供等级重算纯函数消费（函数内部再做防御性排序，不依赖本返回顺序）。
     */
    List<MemberLevelRule> findAll();

    /**
     * 按等级查规则（{@code uk_level}）。
     *
     * @param level 等级
     * @return 命中的规则；规则表缺行返回 empty（调用方按枚举默认权益兜底并告警，ADR-044 fail-open）
     */
    Optional<MemberLevelRule> findByLevel(MemberLevelEnum level);
}

package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.enums.HomeConfigStatusEnum;
import org.icedamericanomall.item.domain.model.home.HomeConfig;
import org.icedamericanomall.item.domain.repository.HomeConfigRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 首页装修配置仓储实现 —— MyBatis-Plus 访问 {@code home_config} 表。
 */
@Repository
@RequiredArgsConstructor
public class HomeConfigRepositoryImpl implements HomeConfigRepository {

    private final HomeConfigMapper homeConfigMapper;

    @Override
    public List<HomeConfig> findEnabled() {
        return homeConfigMapper.selectList(new LambdaQueryWrapper<HomeConfigPO>()
                        .eq(HomeConfigPO::getStatus, HomeConfigStatusEnum.ENABLED.getCode())
                        .orderByAsc(HomeConfigPO::getSortOrder))
                .stream()
                .map(HomeConfigRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<HomeConfig> findAll() {
        return homeConfigMapper.selectList(new LambdaQueryWrapper<HomeConfigPO>()
                        .orderByAsc(HomeConfigPO::getSortOrder))
                .stream()
                .map(HomeConfigRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public Optional<HomeConfig> findById(Long id) {
        HomeConfigPO po = homeConfigMapper.selectById(id);
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    @Override
    public HomeConfig save(HomeConfig config) {
        HomeConfigPO po = toPO(config);
        homeConfigMapper.insert(po);
        return toDomain(po);
    }

    @Override
    public void update(HomeConfig config) {
        homeConfigMapper.updateById(toPO(config));
    }

    @Override
    public void deleteById(Long id) {
        homeConfigMapper.deleteById(id);
    }

    private static HomeConfig toDomain(HomeConfigPO po) {
        return HomeConfig.builder()
                .id(po.getId())
                .slot(po.getSlot())
                .type(po.getType())
                .title(po.getTitle())
                .productIds(po.getProductIds())
                .image(po.getImage())
                .linkUrl(po.getLinkUrl())
                .sortOrder(po.getSortOrder())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private static HomeConfigPO toPO(HomeConfig config) {
        HomeConfigPO po = new HomeConfigPO();
        po.setId(config.getId());
        po.setSlot(config.getSlot());
        po.setType(config.getType());
        po.setTitle(config.getTitle());
        po.setProductIds(config.getProductIds());
        po.setImage(config.getImage());
        po.setLinkUrl(config.getLinkUrl());
        po.setSortOrder(config.getSortOrder());
        po.setStatus(config.getStatus());
        return po;
    }
}

package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.PageResult;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.model.spu.SpuSearchCard;
import org.icedamericanomall.item.domain.model.spu.SpuSearchCriteria;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 商品仓储实现 —— MyBatis-Plus 访问 {@code spu} 表，PO ↔ 聚合转换内聚于此。
 * {@code sort=price} 的跨页排序走 {@link SpuMapper#selectPageOrderByMinPrice} 自定义 SQL。
 */
@Repository
@RequiredArgsConstructor
public class SpuRepositoryImpl implements SpuRepository {

    private final SpuMapper spuMapper;

    @Override
    public IPage<Spu> pageOnSale(Long sellerId, Long categoryId, String keyword, String sort,
                                     int page, int size) {
        LambdaQueryWrapper<SpuPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpuPO::getStatus, SpuStatusEnum.ON_SHELF.getCode());
        if (sellerId != null) {
            // S5：店铺页商品列表（按商家过滤）
            wrapper.eq(SpuPO::getSellerId, sellerId);
        }
        if (categoryId != null) {
            wrapper.eq(SpuPO::getCategoryId, categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SpuPO::getName, keyword);
        }
        if ("sales".equals(sort)) {
            wrapper.orderByDesc(SpuPO::getSoldCount);
        } else {
            // Default: ad spus first, then by create_time
            wrapper.orderByDesc(SpuPO::getIsAd)
                   .orderByDesc(SpuPO::getCreateTime);
        }
        return spuMapper.selectPage(new Page<>(page, size), wrapper)
                .convert(SpuRepositoryImpl::toDomain);
    }

    /** 全状态商品分页（AD64 重建真相源扫描）：id 升序保证跨页稳定遍历。 */
    @Override
    public IPage<Spu> pageAll(int pageNum, int pageSize) {
        return spuMapper.selectPage(new Page<>(pageNum, pageSize),
                        new LambdaQueryWrapper<SpuPO>().orderByAsc(SpuPO::getId))
                .convert(SpuRepositoryImpl::toDomain);
    }

    @Override
    public IPage<Spu> pageOrderByMinPrice(int page, int size, Long sellerId, Long categoryId,
                                              String keyword, String orderDirection) {
        return spuMapper.selectPageOrderByMinPrice(new Page<>(page, size),
                        SpuStatusEnum.ON_SHELF.getCode(),
                        org.icedamericanomall.item.domain.enums.SkuStatusEnum.ON_SALE.getCode(),
                        sellerId, categoryId, keyword, orderDirection)
                .convert(SpuRepositoryImpl::toDomain);
    }

    @Override
    public boolean existsById(Long spuId) {
        return spuMapper.selectById(spuId) != null;
    }

    @Override
    public void incrementCommentCount(Long spuId) {
        spuMapper.update(null, new LambdaUpdateWrapper<SpuPO>()
                .eq(SpuPO::getId, spuId)
                .setSql("comment_count = comment_count + 1"));
    }

    @Override
    public Spu findById(Long id) {
        SpuPO po = spuMapper.selectById(id);
        return po == null ? null : toDomain(po);
    }

    @Override
    public List<Spu> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return spuMapper.selectList(new LambdaQueryWrapper<SpuPO>()
                        .in(SpuPO::getId, ids))
                .stream().map(SpuRepositoryImpl::toDomain).toList();
    }

    @Override
    public Spu save(Spu spu) {
        SpuPO po = toPO(spu);
        spuMapper.insert(po);
        spu.setId(po.getId());
        return spu;
    }

    @Override
    public void updateStatus(Long spuId, Integer status) {
        spuMapper.update(null, new LambdaUpdateWrapper<SpuPO>()
                .eq(SpuPO::getId, spuId)
                .set(SpuPO::getStatus, status));
    }

    @Override
    public void updateEditable(Long spuId, String name, String mainImage, String description,
                               String brand, Long categoryId, Long freightTemplateId) {
        spuMapper.update(null, new LambdaUpdateWrapper<SpuPO>()
                .eq(SpuPO::getId, spuId)
                .set(name != null, SpuPO::getName, name)
                .set(mainImage != null, SpuPO::getMainImage, mainImage)
                .set(description != null, SpuPO::getDescription, description)
                .set(brand != null, SpuPO::getBrand, brand)
                .set(categoryId != null, SpuPO::getCategoryId, categoryId)
                .set(freightTemplateId != null, SpuPO::getFreightTemplateId, freightTemplateId));
    }

    @Override
    public Map<Long, Long> sellerIdMapBySpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return Map.of();
        }
        return spuMapper.selectList(new LambdaQueryWrapper<SpuPO>()
                        .in(SpuPO::getId, spuIds))
                .stream().collect(Collectors.toMap(
                        SpuPO::getId, SpuPO::getSellerId, (a, b) -> a));
    }

    @Override
    public Map<Long, Long> categoryIdMapBySpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return Map.of();
        }
        return spuMapper.selectList(new LambdaQueryWrapper<SpuPO>()
                        .in(SpuPO::getId, spuIds))
                .stream().collect(Collectors.toMap(
                        SpuPO::getId, SpuPO::getCategoryId, (a, b) -> a));
    }

    /**
     * 未绑定模板（freight_template_id 为 NULL）的商品<b>不进入</b>映射——
     * 调用方取值得到 {@code null}，语义即「未绑定 → 回落商家默认模板」。
     */
    @Override
    public Map<Long, Long> freightTemplateIdMapBySpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return Map.of();
        }
        return spuMapper.selectList(new LambdaQueryWrapper<SpuPO>()
                        .in(SpuPO::getId, spuIds))
                .stream()
                .filter(po -> po.getFreightTemplateId() != null)
                .collect(Collectors.toMap(
                        SpuPO::getId, SpuPO::getFreightTemplateId, (a, b) -> a));
    }

    // ==================== 检索出口（ADR-052：search-service 数据面） ====================

    @Override
    public PageResult<SpuSearchCard> searchPage(SpuSearchCriteria criteria) {
        IPage<SpuPO> result = spuMapper.selectSearchPage(
                new Page<>(criteria.page(), criteria.size()),
                criteria.keyword(), criteria.categoryId(), criteria.brand(),
                criteria.minPrice(), criteria.maxPrice(), criteria.sort());
        return new PageResult<>(result.getRecords().stream().map(SpuRepositoryImpl::toCard).toList(),
                result.getTotal(), criteria.page(), criteria.size());
    }

    @Override
    public PageResult<SpuSearchCard> exportPage(int page, int size) {
        IPage<SpuPO> result = spuMapper.selectExportPage(new Page<>(page, size));
        return new PageResult<>(result.getRecords().stream().map(SpuRepositoryImpl::toCard).toList(),
                result.getTotal(), page, size);
    }

    @Override
    public List<String> suggestNames(String prefix, int limit) {
        return spuMapper.selectSuggestNames(prefix, limit).stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public SpuSearchCard findSearchCardById(Long id) {
        SpuPO dataObject = spuMapper.selectSearchCardById(id);
        return dataObject == null ? null : toCard(dataObject);
    }

    @Override
    public SpuSearchCard findSearchCardByBusinessId(String spuBusinessId) {
        // 32-hex → BINARY(16)：非法/缺省入参经 toBytesOrNull 归一为 null → 直接「未命中」，
        // 不放大成 500（与 SkuSpuDisplayQueryImpl 同一宽松解析口径，ADR-061）
        byte[] businessId = IdGenerator.toBytesOrNull(spuBusinessId);
        if (businessId == null) {
            return null;
        }
        SpuPO dataObject = spuMapper.selectSearchCardByBusinessId(businessId);
        return dataObject == null ? null : toCard(dataObject);
    }

    @Override
    public Map<String, Long> idMapByBusinessIds(List<String> spuBusinessIds) {
        // 32-hex → BINARY(16)：非法/缺省入参一律剔除（不放大成 500，与 SkuSpuDisplayQueryImpl 同口径）
        List<byte[]> ids = normalizeBusinessIds(spuBusinessIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> result = new HashMap<>(ids.size());
        for (SpuBusinessKeyRow row : spuMapper.selectIdByBusinessIds(ids)) {
            if (row.getId() == null || row.getSpuId() == null) {
                continue;
            }
            result.putIfAbsent(IdGenerator.toHexString(row.getSpuId()), row.getId());
        }
        return result;
    }

    @Override
    public Map<Long, String> businessIdMapByTechnicalIds(List<Long> spuIds) {
        // 技术键宽松归一：null/重复入参剔除（主键 IN，无命中项即不入映射）
        if (spuIds == null || spuIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = spuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>(ids.size());
        for (SpuBusinessKeyRow row : spuMapper.selectBusinessIdsByIds(ids)) {
            if (row.getId() == null || row.getSpuId() == null) {
                continue;
            }
            result.putIfAbsent(row.getId(), IdGenerator.toHexString(row.getSpuId()));
        }
        return result;
    }

    /** 业务键宽松归一：非法/重复入参剔除（唯一约束下不会出现同键两行）。 */
    private static List<byte[]> normalizeBusinessIds(List<String> spuBusinessIds) {
        if (spuBusinessIds == null || spuBusinessIds.isEmpty()) {
            return List.of();
        }
        List<byte[]> ids = new java.util.ArrayList<>(spuBusinessIds.size());
        for (String id : spuBusinessIds) {
            byte[] parsed = IdGenerator.toBytesOrNull(id);
            if (parsed != null && ids.stream().noneMatch(parsed::equals)) {
                ids.add(parsed);
            }
        }
        return ids;
    }

    /** PO → 检索卡片（业务 ID 出持久化边界转 hex，ADR-050；publishTime 转 epoch millis）。 */
    static SpuSearchCard toCard(SpuPO dataObject) {
        return new SpuSearchCard(
                dataObject.getId(),
                IdGenerator.toHexString(dataObject.getSpuId()),
                dataObject.getCategoryId(),
                dataObject.getName(),
                dataObject.getDescription(),
                dataObject.getBrand(),
                dataObject.getMainImage(),
                dataObject.getMinPrice() == null ? 0 : dataObject.getMinPrice(),
                dataObject.getSoldCount(),
                dataObject.getPublishTime() == null ? null
                        : dataObject.getPublishTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    // ==================== PO <-> 聚合 ====================

    static Spu toDomain(SpuPO po) {
        return Spu.builder()
                .id(po.getId()).spuId(IdGenerator.toHexString(po.getSpuId())).sellerId(po.getSellerId())
                .categoryId(po.getCategoryId()).freightTemplateId(po.getFreightTemplateId())
                .name(po.getName()).mainImage(po.getMainImage()).images(po.getImages())
                .videoUrl(po.getVideoUrl()).attributes(po.getAttributes())
                .serviceTags(po.getServiceTags()).salesTags(po.getSalesTags())
                .viewCount(po.getViewCount()).description(po.getDescription())
                .brand(po.getBrand()).soldCount(po.getSoldCount()).commentCount(po.getCommentCount())
                .isAd(po.getIsAd()).status(po.getStatus())
                .publishTime(po.getPublishTime()).createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    static SpuPO toPO(Spu spu) {
        SpuPO po = new SpuPO();
        po.setId(spu.getId());
        po.setSpuId(IdGenerator.toBytes(spu.getSpuId()));
        po.setSellerId(spu.getSellerId());
        po.setCategoryId(spu.getCategoryId());
        po.setFreightTemplateId(spu.getFreightTemplateId());
        po.setName(spu.getName());
        po.setMainImage(spu.getMainImage());
        po.setImages(spu.getImages());
        po.setVideoUrl(spu.getVideoUrl());
        po.setAttributes(spu.getAttributes());
        po.setServiceTags(spu.getServiceTags());
        po.setSalesTags(spu.getSalesTags());
        po.setViewCount(spu.getViewCount());
        po.setDescription(spu.getDescription());
        po.setBrand(spu.getBrand());
        po.setSoldCount(spu.getSoldCount());
        po.setCommentCount(spu.getCommentCount());
        po.setIsAd(spu.getIsAd());
        po.setStatus(spu.getStatus());
        po.setPublishTime(spu.getPublishTime());
        return po;
    }
}

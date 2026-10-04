package org.icedamericanomall.item.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.dto.FreightTemplateQueryCO;
import org.icedamericanomall.item.adapter.assembler.FreightTemplateConverter;
import org.icedamericanomall.item.adapter.request.FreightTemplateSaveReq;
import org.icedamericanomall.item.adapter.response.FreightTemplateVO;
import org.icedamericanomall.item.domain.model.freight.FreightTemplate;
import org.icedamericanomall.item.domain.repository.FreightTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运费模板编排（应用层，F2 运费模型）—— 商家端 CRUD + 内部计价查询（I43）装配入口。
 *
 * <p><b>I43 装配编排</b>：一次查询内完成「启用模板（按 sellerId / templateIds）+ 兜底补入
 * 商家默认模板（最早创建的启用模板）+ {@code isDefault} 标记」三件事，调用方
 * （trade-service 下单计价）无需二次往返。
 *
 * <p><b>归属口径</b>：编辑/删除/绑定校验「不存在或非本商家」一律 3041，不区分两者，避免探测。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreightTemplateManager {

    private final FreightTemplateRepository freightTemplateRepository;
    private final FreightTemplateConverter freightTemplateConverter;

    /** 批量查询商家启用运费模板（含默认模板标记）—— I43。 */
    public List<FreightTemplateCO> queryEnabled(FreightTemplateQueryCO query) {
        if (query == null || query.getSellerId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "sellerId 不能为空");
        }
        Long sellerId = query.getSellerId();
        List<Long> templateIds = query.getTemplateIds();

        List<FreightTemplate> templates = (templateIds == null || templateIds.isEmpty())
                ? freightTemplateRepository.findEnabledBySeller(sellerId)
                : freightTemplateRepository.findEnabledByIds(sellerId, templateIds);

        FreightTemplate defaultTemplate = freightTemplateRepository.findDefaultEnabled(sellerId);

        Map<Long, FreightTemplate> merged = new LinkedHashMap<>();
        for (FreightTemplate template : templates) {
            merged.put(template.getId(), template);
        }
        if (defaultTemplate != null) {
            merged.putIfAbsent(defaultTemplate.getId(), defaultTemplate);
        }

        List<FreightTemplateCO> result = new ArrayList<>(merged.size());
        for (FreightTemplate template : merged.values()) {
            FreightTemplateCO co = freightTemplateConverter.toCO(template);
            co.setStatus(template.getStatus());
            co.setIsDefault(defaultTemplate != null
                    && defaultTemplate.getId().equals(template.getId()));
            result.add(co);
        }
        log.debug("运费模板查询: sellerId={}, requested={}, returned={}, defaultTemplateId={}",
                sellerId, templateIds == null ? 0 : templateIds.size(), result.size(),
                defaultTemplate == null ? null : defaultTemplate.getId());
        return result;
    }

    /** 商家新建模板（名称唯一）。 */
    @Transactional(rollbackFor = Exception.class)
    public FreightTemplateVO create(Long sellerId, FreightTemplateSaveReq req) {
        requireNoDuplicateName(sellerId, req.getTemplateName(), null);
        FreightTemplate saved = freightTemplateRepository.save(FreightTemplate.create(
                sellerId, req.getTemplateName(),
                req.getChargeMode(), req.getFee(), req.getFreeThreshold(), req.getStatus()));
        return freightTemplateConverter.toVO(saved);
    }

    /** 商家编辑模板（部分字段可选；改名查重）。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Long sellerId, FreightTemplateSaveReq req) {
        FreightTemplate template = requireOwned(id, sellerId);
        if (req.getTemplateName() != null && template.isRenamingTo(req.getTemplateName())) {
            requireNoDuplicateName(sellerId, req.getTemplateName(), id);
        }
        template.applyPatch(
                req.getTemplateName(),
                req.getChargeMode(),
                req.getFee(),
                req.getFreeThreshold(),
                req.getStatus());
        freightTemplateRepository.update(template);
    }

    /** 商家删除模板（绑定商品由 FK ON DELETE SET NULL 回落默认模板）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long sellerId) {
        requireOwned(id, sellerId);
        freightTemplateRepository.deleteById(id);
    }

    /** 商家模板列表。 */
    public List<FreightTemplateVO> listBySeller(Long sellerId) {
        return freightTemplateConverter.toVOs(
                freightTemplateRepository.findBySeller(sellerId));
    }

    /** 商品绑定校验：不存在或非本商家一律 3041。 */
    public void requireBindable(Long sellerId, Long templateId) {
        requireOwned(templateId, sellerId);
    }

    private FreightTemplate requireOwned(Long id, Long sellerId) {
        FreightTemplate template = freightTemplateRepository.findById(id);
        if (template == null || !template.isOwnedBy(sellerId)) {
            throw new BizException(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, "运费模板不存在");
        }
        return template;
    }

    private void requireNoDuplicateName(Long sellerId, String templateName, Long excludeId) {
        if (freightTemplateRepository.existsDuplicateName(sellerId, templateName, excludeId)) {
            throw new BizException(ErrorCode.FREIGHT_TEMPLATE_NAME_EXISTS, "运费模板名称已存在");
        }
    }
}

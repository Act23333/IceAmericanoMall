package org.icedamericanomall.item.app;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.dto.FreightTemplateQueryCO;
import org.icedamericanomall.item.adapter.assembler.FreightTemplateConverter;
import org.icedamericanomall.item.adapter.request.FreightTemplateSaveReq;
import org.icedamericanomall.item.adapter.response.FreightTemplateVO;
import org.icedamericanomall.item.domain.enums.FreightChargeModeEnum;
import org.icedamericanomall.item.domain.enums.FreightTemplateStatusEnum;
import org.icedamericanomall.item.domain.model.freight.FreightTemplate;
import org.icedamericanomall.item.domain.repository.FreightTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * FreightTemplateManager 编排单元测试（F2 运费模型）—— I43 计价查询装配 + 商家端 CRUD/归属校验。
 * （原 FreightTemplateManagerTest + FreightTemplateServiceImplTest 语义合并迁移。）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FreightTemplateManager 编排单元测试")
class FreightTemplateManagerTest {

    @Mock
    private FreightTemplateRepository freightTemplateRepository;

    @Mock
    private FreightTemplateConverter freightTemplateConverter;

    private FreightTemplateManager freightTemplateManager;

    @BeforeEach
    void setUp() {
        freightTemplateManager = new FreightTemplateManager(
                freightTemplateRepository, freightTemplateConverter);
    }

    private FreightTemplateQueryCO query(Long sellerId, List<Long> templateIds) {
        FreightTemplateQueryCO query = new FreightTemplateQueryCO();
        query.setSellerId(sellerId);
        query.setTemplateIds(templateIds);
        return query;
    }

    private FreightTemplate template(Long id, Long sellerId, String name, Integer fee) {
        return FreightTemplate.builder()
                .id(id).templateNo("NO-" + id).sellerId(sellerId).templateName(name)
                .chargeMode(FreightChargeModeEnum.FIXED.getCode())
                .fee(fee).freeThreshold(0)
                .status(FreightTemplateStatusEnum.ENABLED.getCode())
                .build();
    }

    private FreightTemplateCO co(Long id) {
        FreightTemplateCO co = new FreightTemplateCO();
        co.setId(id);
        return co;
    }

    private FreightTemplateSaveReq saveReq(String name, Integer fee) {
        FreightTemplateSaveReq req = new FreightTemplateSaveReq();
        req.setTemplateName(name);
        req.setFee(fee);
        return req;
    }

    // ==================== queryEnabled（I43 计价查询） ====================

    @Test
    @DisplayName("queryEnabled — sellerId 缺失 → PARAM_ERROR")
    void shouldThrowParamError_whenSellerIdMissing() {
        BizException ex = assertThrows(BizException.class,
                () -> freightTemplateManager.queryEnabled(query(null, null)));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("queryEnabled — templateIds 空 → 按 sellerId 取全部启用模板")
    void shouldQueryBySeller_whenTemplateIdsEmpty() {
        FreightTemplate t1 = template(1L, 5L, "A", 500);
        FreightTemplate t2 = template(2L, 5L, "B", 800);
        when(freightTemplateRepository.findEnabledBySeller(5L)).thenReturn(List.of(t1, t2));
        when(freightTemplateRepository.findDefaultEnabled(5L)).thenReturn(t1);
        when(freightTemplateConverter.toCO(t1)).thenReturn(co(1L));
        when(freightTemplateConverter.toCO(t2)).thenReturn(co(2L));

        List<FreightTemplateCO> result = freightTemplateManager.queryEnabled(query(5L, List.of()));

        assertEquals(2, result.size());
        assertEquals(Boolean.TRUE, result.get(0).getIsDefault(), "最早创建的启用模板即默认模板");
        assertEquals(Boolean.FALSE, result.get(1).getIsDefault());
        verify(freightTemplateRepository).findEnabledBySeller(5L);
        verify(freightTemplateRepository, never()).findEnabledByIds(anyLong(), any());
    }

    @Test
    @DisplayName("queryEnabled — templateIds 非空 → 只取命中模板，isDefault 标记正确")
    void shouldQueryByIds_whenTemplateIdsPresent() {
        FreightTemplate hit = template(2L, 5L, "B", 800);
        when(freightTemplateRepository.findEnabledByIds(5L, List.of(2L))).thenReturn(List.of(hit));
        when(freightTemplateRepository.findDefaultEnabled(5L)).thenReturn(hit);
        when(freightTemplateConverter.toCO(hit)).thenReturn(co(2L));

        List<FreightTemplateCO> result = freightTemplateManager.queryEnabled(query(5L, List.of(2L)));

        assertEquals(1, result.size());
        assertEquals(Boolean.TRUE, result.get(0).getIsDefault());
        verify(freightTemplateRepository).findEnabledByIds(5L, List.of(2L));
        verify(freightTemplateRepository, never()).findEnabledBySeller(anyLong());
    }

    @Test
    @DisplayName("queryEnabled — 兜底补入默认模板：调用方未传默认模板 id 也必须可见")
    void shouldFallbackDefaultTemplate_whenNotRequested() {
        FreightTemplate requested = template(2L, 5L, "B", 800);
        FreightTemplate defaultTpl = template(1L, 5L, "A", 500);
        when(freightTemplateRepository.findEnabledBySeller(5L)).thenReturn(List.of(requested));
        when(freightTemplateRepository.findDefaultEnabled(5L)).thenReturn(defaultTpl);
        when(freightTemplateConverter.toCO(requested)).thenReturn(co(2L));
        when(freightTemplateConverter.toCO(defaultTpl)).thenReturn(co(1L));

        List<FreightTemplateCO> result = freightTemplateManager.queryEnabled(query(5L, null));

        assertEquals(2, result.size(), "命中模板 + 兜底默认模板");
        assertEquals(2L, result.get(0).getId());
        assertEquals(1L, result.get(1).getId(), "默认模板补在尾部");
        assertEquals(Boolean.TRUE, result.get(1).getIsDefault());
        assertEquals(Boolean.FALSE, result.get(0).getIsDefault());
    }

    @Test
    @DisplayName("queryEnabled — 商家无启用模板 → 空列表（调用方兜底 postFee=0）")
    void shouldReturnEmpty_whenNoEnabledTemplate() {
        when(freightTemplateRepository.findEnabledBySeller(5L)).thenReturn(List.of());
        when(freightTemplateRepository.findDefaultEnabled(5L)).thenReturn(null);

        assertTrue(freightTemplateManager.queryEnabled(query(5L, List.of())).isEmpty());
    }

    // ==================== create ====================

    @Test
    @DisplayName("create — 缺省值由领域归一（chargeMode=1 / freeThreshold=0 / status=1）并生成业务编号")
    void shouldNormalizeDefaults_whenCreating() {
        when(freightTemplateRepository.existsDuplicateName(5L, "全场满99包邮", null)).thenReturn(false);
        when(freightTemplateRepository.save(any(FreightTemplate.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(freightTemplateConverter.toVO(any())).thenReturn(new FreightTemplateVO());

        freightTemplateManager.create(5L, saveReq("全场满99包邮", 800));

        ArgumentCaptor<FreightTemplate> captor = ArgumentCaptor.forClass(FreightTemplate.class);
        verify(freightTemplateRepository).save(captor.capture());
        FreightTemplate saved = captor.getValue();
        assertEquals(FreightChargeModeEnum.FIXED.getCode(), saved.getChargeMode());
        assertEquals(0, saved.getFreeThreshold());
        assertEquals(FreightTemplateStatusEnum.ENABLED.getCode(), saved.getStatus());
        assertNotNull(saved.getTemplateNo());
    }

    @Test
    @DisplayName("create — 同商家模板名重复 → FREIGHT_TEMPLATE_NAME_EXISTS(3040)，不落库")
    void shouldReject_whenDuplicatedName() {
        when(freightTemplateRepository.existsDuplicateName(5L, "同名模板", null)).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> freightTemplateManager.create(5L, saveReq("同名模板", 800)));
        assertEquals(ErrorCode.FREIGHT_TEMPLATE_NAME_EXISTS, ex.getErrorCode());
        verify(freightTemplateRepository, never()).save(any());
    }

    // ==================== update ====================

    @Test
    @DisplayName("update — 局部更新：仅改动提供的字段，未提供的保持原值")
    void shouldPatchOnlyProvidedFields() {
        FreightTemplate existing = template(1L, 5L, "旧名", 800);
        when(freightTemplateRepository.findById(1L)).thenReturn(existing);
        when(freightTemplateRepository.existsDuplicateName(5L, "新名", 1L)).thenReturn(false);

        freightTemplateManager.update(1L, 5L, saveReq("新名", null));

        assertEquals("新名", existing.getTemplateName());
        assertEquals(800, existing.getFee(), "fee 未提供则保持原值");
        assertEquals(FreightTemplateStatusEnum.ENABLED.getCode(), existing.getStatus());
        verify(freightTemplateRepository).update(existing);
    }

    @Test
    @DisplayName("update — 模板不存在 / 非本商家 → FREIGHT_TEMPLATE_NOT_FOUND(3041)")
    void shouldThrowNotFound_whenUpdating() {
        when(freightTemplateRepository.findById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> freightTemplateManager.update(1L, 5L, saveReq("新名", 800)));
        assertEquals(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verify(freightTemplateRepository, never()).update(any());
    }

    @Test
    @DisplayName("update — 改名为同商家已存在名称 → 3040")
    void shouldReject_whenRenamingToExistingName() {
        when(freightTemplateRepository.findById(1L)).thenReturn(template(1L, 5L, "旧名", 800));
        when(freightTemplateRepository.existsDuplicateName(5L, "占用名", 1L)).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> freightTemplateManager.update(1L, 5L, saveReq("占用名", 800)));
        assertEquals(ErrorCode.FREIGHT_TEMPLATE_NAME_EXISTS, ex.getErrorCode());
        verify(freightTemplateRepository, never()).update(any());
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete — 删除本商家模板（物理删除，绑定商品由 FK 回落默认模板）")
    void shouldDeleteOwnTemplate() {
        when(freightTemplateRepository.findById(1L)).thenReturn(template(1L, 5L, "A", 500));

        freightTemplateManager.delete(1L, 5L);

        verify(freightTemplateRepository).deleteById(1L);
    }

    @Test
    @DisplayName("delete — 他人模板 → 3041，不删除")
    void shouldReject_whenDeletingOthers() {
        when(freightTemplateRepository.findById(1L)).thenReturn(template(1L, 999L, "A", 500));

        BizException ex = assertThrows(BizException.class,
                () -> freightTemplateManager.delete(1L, 5L));
        assertEquals(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verify(freightTemplateRepository, never()).deleteById(any());
    }

    // ==================== requireBindable ====================

    @Test
    @DisplayName("requireBindable — 不存在或归属他人 → 统一 3041（不区分，避免探测）")
    void shouldReject_whenTemplateNotBindable() {
        when(freightTemplateRepository.findById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> freightTemplateManager.requireBindable(5L, 99L));
        assertEquals(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("requireBindable — 本商家模板通过校验")
    void shouldPass_whenTemplateBindable() {
        when(freightTemplateRepository.findById(1L)).thenReturn(template(1L, 5L, "A", 500));

        assertDoesNotThrow(() -> freightTemplateManager.requireBindable(5L, 1L));
    }
}

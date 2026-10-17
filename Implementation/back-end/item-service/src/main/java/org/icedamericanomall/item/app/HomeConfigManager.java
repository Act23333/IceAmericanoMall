package org.icedamericanomall.item.app;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.assembler.HomeConfigConverter;
import org.icedamericanomall.item.adapter.request.HomeConfigReq;
import org.icedamericanomall.item.adapter.response.HomeConfigVO;
import org.icedamericanomall.item.domain.model.home.HomeConfig;
import org.icedamericanomall.item.domain.model.home.SpuIdCsv;
import org.icedamericanomall.item.domain.repository.HomeConfigRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 首页装修配置编排（应用层）—— 前台启用项读取与管理端 CRUD 事务边界。
 *
 * <p><b>{@code spu_ids} 的读写口径（ADR-063 §决策 7 表 #3，落码批次 B7b）</b>：
 * <ul>
 *   <li><b>写侧 fail-closed</b>：进库前统一过 {@link SpuIdCsv#normalizeForWrite} ——
 *       逐项业务键形态校验（非法即 400）+ 条数上限（列宽倒推 ≤15）+ 去重；</li>
 *   <li><b>读侧 fail-open</b>：出库后统一过 {@link SpuIdCsv#filterForRead} —— 个别项非法
 *       只丢弃该项、保留其余，<b>绝不使整条装修配置失效</b>（首屏坏一条不得拖垮首页）。</li>
 * </ul>
 * 两侧都收敛在本类，避免「某个 CRUD 分支忘了校验」。
 */
@Component
@RequiredArgsConstructor
public class HomeConfigManager {

    private final HomeConfigRepository homeConfigRepository;
    private final HomeConfigConverter homeConfigConverter;

    /** 前台：启用中的配置（{@code spu_ids} 读侧 fail-open 过滤）。 */
    public List<HomeConfigVO> listEnabled() {
        return toVOs(homeConfigRepository.findEnabled());
    }

    /** 管理端：全部配置（同上过滤口径；管理端要能看到「实际生效」的商品列表）。 */
    public List<HomeConfigVO> listAll() {
        return toVOs(homeConfigRepository.findAll());
    }

    @Transactional(rollbackFor = Exception.class)
    public HomeConfigVO createConfig(HomeConfigReq req) {
        HomeConfig saved = homeConfigRepository.save(HomeConfig.newConfig(
                req.getSlot(), req.getType(), req.getTitle(), SpuIdCsv.normalizeForWrite(req.getSpuIds()),
                req.getImage(), req.getLinkUrl(), req.getSortOrder(), req.getStatus()));
        return homeConfigConverter.toVO(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public HomeConfigVO updateConfig(Long id, HomeConfigReq req) {
        if (homeConfigRepository.findById(id).isEmpty()) {
            throw new BizException(ErrorCode.HOME_CONFIG_NOT_FOUND);
        }
        HomeConfig revised = HomeConfig.forRevise(id, req.getSlot(), req.getType(), req.getTitle(),
                SpuIdCsv.normalizeForWrite(req.getSpuIds()), req.getImage(), req.getLinkUrl(),
                req.getSortOrder(), req.getStatus());
        homeConfigRepository.update(revised);
        return homeConfigConverter.toVO(revised);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        homeConfigRepository.deleteById(id);
    }

    /**
     * 聚合 → VO，并做 {@code spu_ids} 读侧过滤。
     *
     * <p>为何在转换之后覆写而不是改聚合：聚合是持久化真相的载体，<b>不得</b>因展示口径被就地改写
     * （否则后续 {@code update} 会把过滤结果回写进库，静默删掉管理端配置）。这里只改出参 VO。
     */
    private List<HomeConfigVO> toVOs(List<HomeConfig> configs) {
        List<HomeConfigVO> vos = homeConfigConverter.toVOs(configs);
        for (int i = 0; i < vos.size() && i < configs.size(); i++) {
            vos.get(i).setSpuIds(SpuIdCsv.filterForRead(configs.get(i).getSpuIds()));
        }
        return vos;
    }
}

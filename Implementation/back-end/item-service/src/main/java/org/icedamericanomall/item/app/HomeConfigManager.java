package org.icedamericanomall.item.app;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.assembler.HomeConfigConverter;
import org.icedamericanomall.item.adapter.request.HomeConfigReq;
import org.icedamericanomall.item.adapter.response.HomeConfigVO;
import org.icedamericanomall.item.domain.model.home.HomeConfig;
import org.icedamericanomall.item.domain.repository.HomeConfigRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 首页装修配置编排（应用层）—— 前台启用项读取与管理端 CRUD 事务边界。
 */
@Component
@RequiredArgsConstructor
public class HomeConfigManager {

    private final HomeConfigRepository homeConfigRepository;
    private final HomeConfigConverter homeConfigConverter;

    /** 前台：启用中的配置。 */
    public List<HomeConfigVO> listEnabled() {
        return homeConfigConverter.toVOs(homeConfigRepository.findEnabled());
    }

    /** 管理端：全部配置。 */
    public List<HomeConfigVO> listAll() {
        return homeConfigConverter.toVOs(homeConfigRepository.findAll());
    }

    @Transactional(rollbackFor = Exception.class)
    public HomeConfigVO createConfig(HomeConfigReq req) {
        HomeConfig saved = homeConfigRepository.save(HomeConfig.newConfig(
                req.getSlot(), req.getType(), req.getTitle(), req.getProductIds(),
                req.getImage(), req.getLinkUrl(), req.getSortOrder(), req.getStatus()));
        return homeConfigConverter.toVO(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public HomeConfigVO updateConfig(Long id, HomeConfigReq req) {
        if (homeConfigRepository.findById(id).isEmpty()) {
            throw new BizException(ErrorCode.HOME_CONFIG_NOT_FOUND);
        }
        HomeConfig revised = HomeConfig.forRevise(id, req.getSlot(), req.getType(), req.getTitle(),
                req.getProductIds(), req.getImage(), req.getLinkUrl(), req.getSortOrder(), req.getStatus());
        homeConfigRepository.update(revised);
        return homeConfigConverter.toVO(revised);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        homeConfigRepository.deleteById(id);
    }
}

package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.convert.SkuConverter;
import org.icedAmericanoMall.domain.entity.SkuEntity;
import org.icedAmericanoMall.dto.SkuDTO;
import org.icedAmericanoMall.service.SkuService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/item/sku")
@RequiredArgsConstructor
public class InternalSkuController {

    private final SkuService skuService;
    private final SkuConverter skuConverter;

    @GetMapping("/list/batch")
    public List<SkuDTO> getSkuListByIds(@RequestParam("ids") List<Long> skuIds) {
        List<SkuEntity> entities = skuService.getSkuListByIds(skuIds);
        return skuConverter.entitiesToDTOs(entities);
    }
}

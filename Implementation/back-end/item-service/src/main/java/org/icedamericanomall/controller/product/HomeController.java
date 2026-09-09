package org.icedamericanomall.controller.product;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.HomeConfigEntity;
import org.icedamericanomall.service.HomeConfigService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeConfigService homeConfigService;

    @GetMapping("/config")
    public Result<List<HomeConfigEntity>> config() {
        return Result.ok(homeConfigService.listEnabled());
    }
}

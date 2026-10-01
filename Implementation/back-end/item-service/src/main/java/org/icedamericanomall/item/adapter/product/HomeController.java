package org.icedamericanomall.item.adapter.product;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.entity.HomeConfigEntity;
import org.icedamericanomall.item.service.HomeConfigService;
import org.icedamericanomall.common.domain.Result;
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

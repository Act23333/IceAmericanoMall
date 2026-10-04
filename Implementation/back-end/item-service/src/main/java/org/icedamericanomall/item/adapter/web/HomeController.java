package org.icedamericanomall.item.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.item.adapter.response.HomeConfigVO;
import org.icedamericanomall.item.app.HomeConfigManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeConfigManager homeConfigManager;

    /** 前台首页配置（仅启用中的配置）。 */
    @GetMapping("/config")
    public Result<List<HomeConfigVO>> config() {
        return Result.ok(homeConfigManager.listEnabled());
    }
}

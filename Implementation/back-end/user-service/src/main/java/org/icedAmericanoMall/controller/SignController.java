package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.service.UserSignService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Sign-in controller — daily check-in with Redis Bitmap.
 */
@RestController
@RequestMapping("/user/sign")
@RequiredArgsConstructor
public class SignController {

    private final UserSignService userSignService;

    @PostMapping
    public Result<Map<String, Object>> sign() {
        Long userId = UserContext.getUser();
        userSignService.sign(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("signed", true);
        result.put("monthCount", userSignService.countCurrentMonthSign(userId));
        result.put("continuousDays", userSignService.countContinuousSign(userId));
        return Result.ok(result);
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Long userId = UserContext.getUser();
        Map<String, Object> result = new HashMap<>();
        result.put("todaySigned", userSignService.isTodaySigned(userId));
        result.put("monthCount", userSignService.countCurrentMonthSign(userId));
        result.put("continuousDays", userSignService.countContinuousSign(userId));
        return Result.ok(result);
    }
}

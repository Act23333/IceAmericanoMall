package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.noLazy.common.domain.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 操作日志查询 — 管理后台。
 */
@RestController
@RequestMapping("/api/admin/log")
@RequiredArgsConstructor
public class AdminLogController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        return Result.ok(jdbcTemplate.queryForList(
                "SELECT * FROM operation_log ORDER BY create_time DESC LIMIT ? OFFSET ?", size, offset));
    }
}

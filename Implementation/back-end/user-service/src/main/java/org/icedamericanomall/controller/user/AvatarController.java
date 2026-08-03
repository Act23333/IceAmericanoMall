package org.icedamericanomall.controller.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.service.UserProfileService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 大厂标准: 头像管理 — MinIO 上传/下载/缩放。
 */
@Slf4j
@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class AvatarController {

    private final UserProfileService userProfileService;

    /** POST /api/user/profile/avatar — 上传头像到 MinIO，生成多尺寸缩略图 */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        String url = userProfileService.uploadAvatar(userId, file);
        return Result.ok("头像上传成功", url);
    }

    /**
     * GET /api/user/profile/avatar — 下载头像。
     * ?width=200&height=200 → 缩略图；无参数 → 200x200 默认
     */
    @GetMapping("/avatar")
    public ResponseEntity<byte[]> getAvatar(
            @RequestParam(defaultValue = "200") int width,
            @RequestParam(defaultValue = "200") int height) {
        Long userId = UserContext.getUserId();
        byte[] data = userProfileService.getAvatar(userId, width, height);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(data);
    }
}

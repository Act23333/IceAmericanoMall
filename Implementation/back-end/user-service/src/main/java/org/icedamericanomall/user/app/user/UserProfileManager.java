package org.icedamericanomall.user.app.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.adapter.request.UpdateProfileReq;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.app.port.AvatarStorage;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * 用户资料领域编排（应用层）—— PATCH 增量更新 + 头像上传/下载。
 *
 * <p>Controller → Manager → Repository 分层；图片缩放/对象存储通过
 * {@link AvatarStorage} 端口下沉到基础设施实现。
 *
 * <p>V5.2：PATCH 白名单新增 {@code gender} / {@code birthday}（资料重构增量字段）；
 * 会员等级 {@code memberLevel} 不开放用户自改（运营/系统口径）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileManager {

    /** 允许通过 PATCH 更新的白名单字段 */
    private static final Set<String> ALLOWED_FIELDS = Set.of("nickname", "avatar", "gender", "birthday");

    private final UserRepository userRepository;
    private final AvatarStorage avatarStorage;

    /**
     * 增量更新用户资料（大厂标准 PATCH 语义）。
     *
     * <ol>
     *   <li>先查全量聚合（装载后按 id 更新）</li>
     *   <li>白名单校验 → merge（实体方法内聚字段变更与校验）</li>
     *   <li>@Version 乐观锁更新</li>
     *   <li>返回完整用户信息</li>
     * </ol>
     */
    public UserInfoResp patchProfile(Long userId, UpdateProfileReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        if (req.updateMask() != null && !req.updateMask().isEmpty()) {
            for (String field : req.updateMask()) {
                if (!ALLOWED_FIELDS.contains(field)) {
                    log.warn("patchProfile blocked field: userId={}, field={}", userId, field);
                    continue;
                }
                applyField(user, field, req);
            }
        } else {
            applyAllPresentFields(user, req);
        }

        userRepository.update(user);
        return userRepository.findById(userId)
                .map(UserInfoResp::from)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 上传头像到对象存储（原图 + 多尺寸缩略图）并更新 {@code user.avatar}。
     *
     * @return 原图可访问 URL
     */
    public String uploadAvatar(Long userId, MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "头像上传失败");
        }
        String url = avatarStorage.store(userId, bytes, file.getContentType());

        userRepository.findById(userId).ifPresent(user -> {
            user.changeAvatar(url);
            userRepository.update(user);
        });
        return url;
    }

    /** 获取头像字节（按尺寸降级）。 */
    public byte[] getAvatar(Long userId, int width, int height) {
        return avatarStorage.load(userId, width, height);
    }

    // ==================== 内部方法 ====================

    private void applyField(User user, String field, UpdateProfileReq req) {
        switch (field) {
            case "nickname" -> {
                if (req.nickname() != null) {
                    user.changeNickname(req.nickname());
                }
            }
            case "avatar" -> {
                if (req.avatar() != null) {
                    user.changeAvatar(req.avatar());
                }
            }
            case "gender" -> {
                if (req.gender() != null) {
                    user.changeGender(req.gender());
                }
            }
            case "birthday" -> {
                if (req.birthday() != null) {
                    user.changeBirthday(req.birthday());
                }
            }
            default -> {
                // 白名单已过滤，不可达
            }
        }
    }

    /** 未携带 updateMask：按非空字段全量合并（历史语义）。 */
    private void applyAllPresentFields(User user, UpdateProfileReq req) {
        if (req.nickname() != null) {
            user.changeNickname(req.nickname());
        }
        if (req.avatar() != null) {
            user.changeAvatar(req.avatar());
        }
        if (req.gender() != null) {
            user.changeGender(req.gender());
        }
        if (req.birthday() != null) {
            user.changeBirthday(req.birthday());
        }
    }
}

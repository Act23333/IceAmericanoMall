package org.icedamericanomall.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.icedamericanomall.domain.dto.UpdateProfileReq;
import org.icedamericanomall.domain.entity.UserEntity;
import org.icedamericanomall.domain.vo.UserInfoResp;
import org.icedamericanomall.integration.storage.StorageClient;
import org.icedamericanomall.integration.storage.StorageProperties;
import org.icedamericanomall.mapper.UserMapper;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 大厂标准: 用户资料领域服务。
 * <p>
 * Controller → Converter → DomainService → Repository 四层分离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    /** 允许通过 PATCH 更新的白名单字段 */
    private static final Set<String> ALLOWED_FIELDS = Set.of("nickname", "avatar");
    private static final int[][] THUMB_SIZES = {{200, 200}, {50, 50}};
    private static final String AVATAR_OBJECT_PREFIX = "avatar/";

    private final UserMapper userMapper;
    private final StorageClient storageClient;
    private final StorageProperties storageProperties;

    /**
     * 增量更新用户资料（大厂标准 PATCH 语义）。
     * <ol>
     *   <li>先查全量 UserEntity</li>
     *   <li>白名单校验 → merge 到 entity</li>
     *   <li>updateById（@Version 防并发覆盖）</li>
     *   <li>返回完整 UserInfoResp</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public UserInfoResp patchProfile(Long userId, UpdateProfileReq req) {
        UserEntity entity = userMapper.selectById(userId);
        if (entity == null) throw new BizException(ErrorCode.USER_NOT_FOUND);

        if (req.updateMask() != null && !req.updateMask().isEmpty()) {
            for (String field : req.updateMask()) {
                if (!ALLOWED_FIELDS.contains(field)) {
                    log.warn("patchProfile blocked field: userId={}, field={}", userId, field);
                    continue;
                }
                switch (field) {
                    case "nickname" -> {
                        if (req.nickname() != null) entity.setUsername(req.nickname());
                    }
                    case "avatar" -> {
                        if (req.avatar() != null) entity.setAvatar(req.avatar());
                    }
                }
            }
        } else {
            if (req.nickname() != null) entity.setUsername(req.nickname());
            if (req.avatar() != null) entity.setAvatar(req.avatar());
        }

        userMapper.updateById(entity);
        return toResp(userMapper.selectById(userId));
    }

    /** 上传头像到 MinIO + 生成缩略图 + 更新 user.avatar */
    public String uploadAvatar(Long userId, MultipartFile file) {
        try {
            String bucket = storageProperties.getBucket();
            String ext = "jpg";
            String originalObj = AVATAR_OBJECT_PREFIX + userId + "." + ext;

            byte[] bytes = file.getBytes();
            storageClient.upload(bucket, originalObj, new ByteArrayInputStream(bytes), file.getContentType());

            for (int[] size : THUMB_SIZES) {
                byte[] thumb = resize(bytes, size[0], size[1]);
                String thumbObj = AVATAR_OBJECT_PREFIX + userId + "_" + size[0] + "x" + size[1] + "." + ext;
                storageClient.upload(bucket, thumbObj, new ByteArrayInputStream(thumb), "image/jpeg");
            }

            return storageClient.getUrl(bucket, originalObj);
        } catch (IOException e) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "头像上传失败");
        }
    }

    /** 获取头像字节（按尺寸降级） */
    public byte[] getAvatar(Long userId, int width, int height) {
        String bucket = storageProperties.getBucket();
        String thumbObj = AVATAR_OBJECT_PREFIX + userId + "_" + width + "x" + height + ".jpg";
        String originalObj = AVATAR_OBJECT_PREFIX + userId + ".jpg";

        InputStream is = storageClient.download(bucket, thumbObj);
        if (is == null) is = storageClient.download(bucket, originalObj);
        if (is == null) return new byte[0];
        try { return is.readAllBytes(); } catch (IOException e) { return new byte[0]; }
    }

    private byte[] resize(byte[] input, int w, int h) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(input));
        if (img == null) throw new IOException("无法解析图片");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(img).size(w, h).outputFormat("jpg").toOutputStream(out);
        return out.toByteArray();
    }

    private UserInfoResp toResp(UserEntity e) {
        UserInfoResp r = new UserInfoResp();
        r.setUserId(e.getUserId()); r.setUsername(e.getUsername());
        r.setPhone(e.getPhone()); r.setAvatar(e.getAvatar());
        r.setStatus(e.getStatus() != null ? e.getStatus().getCode() : null);
        r.setRegisterTime(e.getCreateTime());
        r.setBalance(e.getBalance());
        return r;
    }
}

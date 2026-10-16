package org.icedamericanomall.item.app.share;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.integration.auth.client.WechatMiniApiClient;
import org.icedamericanomall.integration.storage.StorageClient;
import org.icedamericanomall.integration.storage.StorageProperties;
import org.icedamericanomall.item.adapter.response.WxaCodeVO;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 商品分享小程序码编排（P37，契约 §4.4.x V5.18）—— 详情页「分享」位用例族收口
 * （ADR-025 用例族拆分；公开匿名 GET，无写状态、无事务）。
 *
 * <p><b>缓存策略</b>：生成结果按 {@code wxa-code/product/{id}.png} 缓存于 MinIO ——
 * 命中（download 非空即存在，{@code StorageClient} 无 exists 探测接口）直返 URL 跳过微信调用；
 * 未命中调 {@code wxacode.getUnlimited}（scene={@code p={spuId}}，远小于 32 字符上限）
 * 后覆盖上传（覆盖幂等无害）。
 *
 * <p><b>失败语义（fail-loud）</b>：与 RAG/推荐的旁路 fail-open 定类不同，分享码是显式
 * 功能端点 —— 商品不存在/不在售 → 1103；微信 API 失败/未配置 → 异常原样透出由调用方感知；
 * 上传失败 → {@code OSS_UPLOAD_FAILED}（对齐发票批 {@code MockInvoiceIssueAdapter} 口径）。
 */
@Slf4j
@Component
public class SpuShareManager {

    /** 小程序码 objectKey 前缀（对象命名空间）。 */
    private static final String OBJECT_KEY_PREFIX = "wxa-code/product/";

    /** 小程序码 objectKey 后缀。 */
    private static final String OBJECT_KEY_SUFFIX = ".png";

    /** 小程序码内容类型。 */
    private static final String PNG_CONTENT_TYPE = "image/png";

    /** 预签名下载地址有效期（分钟）—— 发票批先例同口径。 */
    private static final int PRESIGN_EXPIRY_MINUTES = 30;

    /** scene 前缀：分享打开后透传商品 ID（{@code p=123}）。 */
    private static final String SCENE_PREFIX = "p=";

    private final SpuRepository spuRepository;
    private final StorageClient storageClient;
    private final StorageProperties storageProperties;
    private final WechatMiniApiClient wechatMiniApiClient;
    private final String wxaPage;

    public SpuShareManager(SpuRepository spuRepository,
                               StorageClient storageClient,
                               StorageProperties storageProperties,
                               WechatMiniApiClient wechatMiniApiClient,
                               @Value("${item.share.wxa-page:pages/product/detail/index}") String wxaPage) {
        this.spuRepository = spuRepository;
        this.storageClient = storageClient;
        this.storageProperties = storageProperties;
        this.wechatMiniApiClient = wechatMiniApiClient;
        this.wxaPage = wxaPage;
    }

    /**
     * 获取商品分享小程序码：MinIO 缓存命中直返，未命中生成并上传。
     *
     * @param spuId 商品 ID
     * @return 含预签名 URL 的 VO
     * @throws BizException 商品不存在/不在售（1103）；微信生成失败/未配置原样透出；上传失败（4040）
     */
    public WxaCodeVO getWxaCode(Long spuId) {
        requireOnSale(spuId);
        String objectKey = OBJECT_KEY_PREFIX + spuId + OBJECT_KEY_SUFFIX;
        if (existsInStorage(objectKey)) {
            return WxaCodeVO.of(presignedUrl(objectKey));
        }
        return generateAndUpload(spuId, objectKey);
    }

    /** 商品存在且在售校验 —— 分享码只对可售商品有意义，否则统一 1103（任务口径）。 */
    private void requireOnSale(Long spuId) {
        Spu spu = spuRepository.findById(spuId);
        if (spu == null || spu.getStatus() == null
                || spu.getStatus() != SpuStatusEnum.ON_SHELF.getCode()) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND);
        }
    }

    /** 缓存探测：download 非空即视为已生成过（MinIO 缺对象降级返回 null）。 */
    private boolean existsInStorage(String objectKey) {
        try (InputStream in = storageClient.download(storageProperties.getBucket(), objectKey)) {
            return in != null;
        } catch (IOException e) {
            log.warn("[wxa-code][exists-check][fail] objectKey={} 按未命中处理", objectKey);
            return false;
        }
    }

    /** 未命中路径：调微信生成 → 覆盖上传 → 返回预签名 URL。 */
    private WxaCodeVO generateAndUpload(Long spuId, String objectKey) {
        String scene = SCENE_PREFIX + spuId;
        byte[] png = wechatMiniApiClient.getWxaCodeUnlimited(scene, wxaPage);
        String url = storageClient.upload(storageProperties.getBucket(), objectKey,
                new ByteArrayInputStream(png), PNG_CONTENT_TYPE);
        if (url == null) {
            throw new BizException(ErrorCode.OSS_UPLOAD_FAILED, "小程序码上传失败");
        }
        log.info("[wxa-code][generated] spuId={} objectKey={} bytes={}", spuId, objectKey, png.length);
        return WxaCodeVO.of(presignedUrl(objectKey));
    }

    private String presignedUrl(String objectKey) {
        return storageClient.getPresignedUrl(storageProperties.getBucket(), objectKey, PRESIGN_EXPIRY_MINUTES);
    }
}

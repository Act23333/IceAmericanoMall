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
 * <p><b>缓存策略</b>：生成结果按 {@code wxa-code/product/{技术键}.png} 缓存于 MinIO ——
 * 命中（download 非空即存在，{@code StorageClient} 无 exists 探测接口）直返 URL 跳过微信调用；
 * 未命中调 {@code wxacode.getUnlimited} 后覆盖上传（覆盖幂等无害）。
 *
 * <p><b>失败语义（fail-loud）</b>：与 RAG/推荐的旁路 fail-open 定类不同，分享码是显式
 * 功能端点 —— 商品不存在/不在售 → 1103；微信 API 失败/未配置 → 异常原样透出由调用方感知；
 * 上传失败 → {@code OSS_UPLOAD_FAILED}（对齐发票批 {@code MockInvoiceIssueAdapter} 口径）。
 *
 * <p><b>⚠ P37 {@code scene} 与业务键寻址的冲突（本批显式登记，未裁决，不静默截断）</b>
 * <ul>
 *   <li>入参口径已随 ADR-062（§决策 2/3）改为 <b>SPU 业务键</b> 32-hex（路径变量 {@code {spuId}}）；</li>
 *   <li>而微信 {@code wxacode.getUnlimited} 的 {@code scene} 上限为 <b>32 个可见字符</b>：
 *       {@code "p=" + 32-hex} = <b>34 字符 → 超限</b>（这条约束只对旧的技术键形态不成立）；</li>
 *   <li>本批（B5）<b>不做短码/短链映射</b>（属小程序承载层设计，须单独立项），
 *       也<b>不截断</b>（截断会把 {@code scene} 指向另一个商品，是不可接受的静默错误）；</li>
 *   <li>故此处<b>保留技术键出 {@code scene} 与 objectKey</b>，即「本规则唯一例外」的寄存形态：
 *       语义与长度均不变（零行为回归），冲突本身登记在案（ADR-062 §后果 E1、契约 §4.2.11 注、
 *       本批交付报告「残余风险」）。</li>
 *   <li>待裁决选项：(a) 保技术键并显式登记为例外（现状）；(b) 短码/短链映射（推荐，需新批次）；
 *       (c) 截断（<b>不可接受</b>）。<b>未裁决前不得改为业务键入 scene</b>。</li>
 *   <li>另注：小程序端目前<b>零处</b>解析该 {@code scene}（前端仅登录用 {@code scene}），
 *       故本冲突暂无可观测影响；一旦前端开始按 {@code scene} 回链详情，必须同步落地 (b)。</li>
 * </ul>
 */
@Slf4j
@Component
public class SpuShareManager {

    /** 小程序码 objectKey 前缀（对象命名空间；ADR-062 §决策 2 表 B #3 判定为非 API 路径段，保留）。 */
    private static final String OBJECT_KEY_PREFIX = "wxa-code/product/";

    /** 小程序码 objectKey 后缀。 */
    private static final String OBJECT_KEY_SUFFIX = ".png";

    /** 小程序码内容类型。 */
    private static final String PNG_CONTENT_TYPE = "image/png";

    /** 预签名下载地址有效期（分钟）—— 发票批先例同口径。 */
    private static final int PRESIGN_EXPIRY_MINUTES = 30;

    /**
     * scene 前缀：分享打开后透传商品标识（{@code p=123}）。
     *
     * <p>⚠ 只在「技术键」形态下 ≤32 字符；业务键（32-hex）加本前缀为 34 字符，<b>超过微信上限</b>
     * —— 这正是本类 javadoc 登记的 P37 冲突（见类注释；未裁决前不得改用业务键）。
     */
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
     * @param spuId SPU <b>业务键</b>（32-hex，路径变量 {@code {spuId}} 口径，ADR-062 §决策 2/3）
     * @return 含预签名 URL 的 VO
     * @throws BizException 商品不存在/不在售（1103）；微信生成失败/未配置原样透出；上传失败（4040）
     */
    public WxaCodeVO getWxaCode(String spuId) {
        Spu spu = spuRepository.findByBusinessId(spuId);
        requireOnSale(spu);
        // scene / objectKey 继续用技术键（P37 冲突的寄存形态，见类注释）：语义不变、长度不超限，
        // 且缓存对象不因寻址口径变更而孤立（ADR-062 §决策 2 表 B #3）。
        Long spuTechnicalId = spu.getId();
        String objectKey = OBJECT_KEY_PREFIX + spuTechnicalId + OBJECT_KEY_SUFFIX;
        if (existsInStorage(objectKey)) {
            return WxaCodeVO.of(presignedUrl(objectKey));
        }
        return generateAndUpload(spuTechnicalId, objectKey);
    }

    /** 商品存在且在售校验 —— 分享码只对可售商品有意义，否则统一 1103（任务口径）。 */
    private void requireOnSale(Spu spu) {
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

    /** 未命中路径：调微信生成 → 覆盖上传 → 返回预签名 URL（scene 用技术键，见类注释的 P37 登记）。 */
    private WxaCodeVO generateAndUpload(Long spuTechnicalId, String objectKey) {
        String scene = SCENE_PREFIX + spuTechnicalId;
        byte[] png = wechatMiniApiClient.getWxaCodeUnlimited(scene, wxaPage);
        String url = storageClient.upload(storageProperties.getBucket(), objectKey,
                new ByteArrayInputStream(png), PNG_CONTENT_TYPE);
        if (url == null) {
            throw new BizException(ErrorCode.OSS_UPLOAD_FAILED, "小程序码上传失败");
        }
        log.info("[wxa-code][generated] spuId={} objectKey={} bytes={}", spuTechnicalId, objectKey, png.length);
        return WxaCodeVO.of(presignedUrl(objectKey));
    }

    private String presignedUrl(String objectKey) {
        return storageClient.getPresignedUrl(storageProperties.getBucket(), objectKey, PRESIGN_EXPIRY_MINUTES);
    }
}

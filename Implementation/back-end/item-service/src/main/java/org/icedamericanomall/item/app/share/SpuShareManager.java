package org.icedamericanomall.item.app.share;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.Sha256Util;
import org.icedamericanomall.integration.auth.client.WechatMiniApiClient;
import org.icedamericanomall.integration.storage.StorageClient;
import org.icedamericanomall.integration.storage.StorageProperties;
import org.icedamericanomall.item.adapter.response.WxaCodeVO;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.spu.ShortCode;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 商品分享小程序码编排（P37，契约 §4.4.x V5.18；承载位解耦落码批次 B7b）—— 详情页「分享」位
 * 用例族收口（ADR-025 用例族拆分；公开匿名 GET，无写状态、无事务）。
 *
 * <p><b>承载位口径（ADR-063 §决策 7 表 #2/#3，本批落码）</b>：
 * <ul>
 *   <li><b>{@code scene} = {@code "p=" + 短码}</b>（共 <b>12 字符</b>，微信上限
 *       {@value org.icedamericanomall.integration.auth.client.WechatMiniApiClient#SCENE_MAX_LENGTH}，
 *       余 20 字符）。<b>不放业务键、也不放技术键</b>：{@code "p=" + 32-hex 业务键 = 34 字符已超限，
 *       而截断会指向<b>另一个商品</b>（不可接受的静默错误）；技术键则是「把内部标识外发」。
 *       短码是 SPU 的<b>对外寻址别名</b>，落地页用 P38 {@code GET /api/item/spu/by-code/{code}} 换回。</li>
 *   <li><b>objectKey = {@code wxa-code/v1/product/{sha256("scene|page")[0:16]}.png}</b> ——
 *       <b>内容派生值</b>：业务键与技术键一律不得作对象键。理由：换内部 ID 方案会令全部既有对象
 *       <b>孤立</b>；内容不变而键变（改名）会<b>重复生成</b>；对外对象键可被<b>枚举/反推</b>。
 *       同一 {@code scene}+{@code page} 恒得同一键 ⇒ 覆盖写天然幂等，<b>无需新增存储列</b>。</li>
 * </ul>
 *
 * <p><b>关于 {@code v1/} 版本段（显式说明，别误读为灰度计划）</b>：项目<b>从未发布运行过</b>
 * ⇒ <b>无存量对象、无需灰度、无需清理</b>。{@code v1/} 只是为<b>将来换算法留的免费保险</b>
 * —— 届时新键走 {@code v2/}，与 {@code v1/} 并存即可平滑过渡，<b>不需要迁移/清理</b>。
 * （若真在已发布环境改算法，才需要「回滚期旧对象仍在 → 新键并存 → 按前缀批量清理旧键」那套动作。）
 *
 * <p><b>失败语义（fail-loud）</b>：与 RAG/推荐的旁路 fail-open 定类不同，分享码是显式功能端点 ——
 * 商品不存在/不在售 → 1103；商品缺短码（数据不变量破坏）→ 500；微信 API 失败/未配置 → 原样透出；
 * 上传失败 → {@code OSS_UPLOAD_FAILED}（对齐发票批 {@code MockInvoiceIssueAdapter} 口径）。
 */
@Slf4j
@Component
public class SpuShareManager {

    /** 小程序码 objectKey 前缀（含 {@code v1/} 版本段；见类注释的版本段说明）。 */
    private static final String OBJECT_KEY_PREFIX = "wxa-code/v1/product/";

    /** 小程序码 objectKey 后缀。 */
    private static final String OBJECT_KEY_SUFFIX = ".png";

    /** 对象键中内容哈希的取长（16 位 hex = 64 bit，碰撞概率远低于对象规模）。 */
    private static final int OBJECT_KEY_HASH_LENGTH = 16;

    /** scene 与 page 的分隔符（内容哈希的输入的一部分，改动即令全部对象键变化）。 */
    private static final String OBJECT_KEY_HASH_SEPARATOR = "|";

    /** 小程序码内容类型。 */
    private static final String PNG_CONTENT_TYPE = "image/png";

    /** 预签名下载地址有效期（分钟）—— 发票批先例同口径。 */
    private static final int PRESIGN_EXPIRY_MINUTES = 30;

    /**
     * scene 前缀：落地页按 {@code p=} 切出短码（{@code scene = "p=" + 短码}）。
     *
     * <p>前缀长度必须计入 {@code scene} 预算：{@code 2 + 10 = 12 ≤ 32}。
     * 若将来加长前缀，必须与承载位预算同批评审（ADR-063 §决策 7「前缀预算」禁令）。
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
     * @throws BizException 商品不存在/不在售（1103）；商品缺短码（500）；微信生成失败/未配置原样透出；
     *                      上传失败（4040）
     */
    public WxaCodeVO getWxaCode(String spuId) {
        Spu spu = spuRepository.findByBusinessId(spuId);
        requireOnSale(spu);
        String scene = sceneOf(spu);
        // 内容派生键：同 scene+page 恒得同键（覆盖写幂等），且与内部标识彻底解耦
        String objectKey = objectKeyOf(scene, wxaPage);
        if (existsInStorage(objectKey)) {
            return WxaCodeVO.of(presignedUrl(objectKey));
        }
        return generateAndUpload(spu.getId(), scene, objectKey);
    }

    /**
     * 组装 {@code scene}：{@code "p=" + 短码}（短码口径见 {@link ShortCode}）。
     *
     * <p><b>商品缺短码怎么办</b>：{@code spu.short_code} 是 {@code NOT NULL}，理论上不可能为
     * {@code null}；为空说明「数据不变量被破坏」（例如绕过聚合工厂直接插库）——
     * 此时<b>禁止懒生成</b>（懒生成会让同一 SPU 在不同请求拿到不同短码，已投放物料即失效），
     * 直接 500 fail-loud，让人看见问题。
     */
    private String sceneOf(Spu spu) {
        String shortCode = spu.getShortCode();
        if (!ShortCode.isValid(shortCode)) {
            log.error("[wxa-code][invariant] 商品缺少合法短码（{}.short_code NOT NULL 被破坏）: spuId={}, shortCode={}",
                    "spu", spu.getSpuId(), shortCode);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "商品分享码不可用（缺少对外短码）");
        }
        return SCENE_PREFIX + shortCode;
    }

    /**
     * 内容派生的对象键：{@code wxa-code/v1/product/}{@code sha256("scene|page")[0:16]}{@code .png}。
     *
     * <p>键只由<b>内容</b>（scene + 落地页）决定，不含任何内部标识 —— 换 ID 方案不会让对象孤立，
     * 也不会因为「同一个商品换了写法」而重复生成。包内可见（{@code static}）以便单测直接断言键形态。
     *
     * @param scene 场景参数（{@code p=<短码>}）
     * @param page  落地页路径
     * @return 对象键
     */
    static String objectKeyOf(String scene, String page) {
        String digest = Sha256Util.hexPrefix(scene + OBJECT_KEY_HASH_SEPARATOR + page, OBJECT_KEY_HASH_LENGTH);
        return OBJECT_KEY_PREFIX + digest + OBJECT_KEY_SUFFIX;
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

    /** 未命中路径：调微信生成 → 覆盖上传 → 返回预签名 URL。 */
    private WxaCodeVO generateAndUpload(Long spuTechnicalId, String scene, String objectKey) {
        byte[] png = wechatMiniApiClient.getWxaCodeUnlimited(scene, wxaPage);
        String url = storageClient.upload(storageProperties.getBucket(), objectKey,
                new ByteArrayInputStream(png), PNG_CONTENT_TYPE);
        if (url == null) {
            throw new BizException(ErrorCode.OSS_UPLOAD_FAILED, "小程序码上传失败");
        }
        log.info("[wxa-code][generated] spuId={} scene={} objectKey={} bytes={}",
                spuTechnicalId, scene, objectKey, png.length);
        return WxaCodeVO.of(presignedUrl(objectKey));
    }

    private String presignedUrl(String objectKey) {
        return storageClient.getPresignedUrl(storageProperties.getBucket(), objectKey, PRESIGN_EXPIRY_MINUTES);
    }
}

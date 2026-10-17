package org.icedamericanomall.item.app.share;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.integration.auth.client.WechatMiniApiClient;
import org.icedamericanomall.integration.storage.StorageClient;
import org.icedamericanomall.integration.storage.StorageProperties;
import org.icedamericanomall.item.adapter.response.WxaCodeVO;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.spu.ShortCode;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SpuShareManager 单元测试（P37；承载位解耦落码批次 B7b）—— mock MinIO（{@link StorageClient}）
 * 与微信客户端，锁定：
 *
 * <ul>
 *   <li>商品不存在/不在售 → 1103；</li>
 *   <li><b>{@code scene} 用短码</b>（{@code p=<10 位 hex>}，共 12 字符 ≤ 微信上限 32）；</li>
 *   <li><b>objectKey 是内容派生值</b>（{@code wxa-code/v1/product/{sha256(scene|page)[0:16]}.png}）
 *       —— 不含任何内部标识（技术键/业务键/短码都不出现在键里）；</li>
 *   <li>缓存未命中「生成→上传→URL」、缓存命中直返（零微信调用）。</li>
 * </ul>
 *
 * <p><b>为何断言「同 scene+page 恒得同键」</b>：这是覆盖写幂等的前提（同一商品重复分享不产生
 * 第二份对象）；也正是「内容派生」相对「技术键命名」的核心差别。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpuShareManager —— 商品分享小程序码编排（scene=短码 / objectKey=内容哈希）")
class SpuShareManagerTest {

    /** 商品技术键（{@code spu.id}；<b>不得</b>出现在 scene/objectKey 里，本类有断言守住）。 */
    private static final Long SPU_TECHNICAL_ID = 7L;

    /** 商品业务键（{@code spu.spu_id} 的 32-hex 形态，路径变量口径）。 */
    private static final String SPU_ID = String.format("%032x", SPU_TECHNICAL_ID);

    /** 商品短码（10 位小写 hex；线上由 {@code Spu#publish} 定稿，测试用固定值便于断言）。 */
    private static final String SHORT_CODE = "8f3a1c9d20";

    private static final String PAGE = "pages/product/detail/index";

    /** 期望的 scene：{@code p=} + 短码 = 12 字符。 */
    private static final String SCENE = "p=" + SHORT_CODE;

    /** 期望的 objectKey（内容派生；由被测类的唯一键算法给出，测试侧只断言形态与稳定性）。 */
    private static final String OBJECT_KEY = SpuShareManager.objectKeyOf(SCENE, PAGE);

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47};

    @Mock
    private SpuRepository spuRepository;

    @Mock
    private StorageClient storageClient;

    @Mock
    private WechatMiniApiClient wechatMiniApiClient;

    private SpuShareManager manager;

    @BeforeEach
    void setUp() {
        // StorageProperties 为纯配置载体，用真实实例（bucket 缺省 icedmall）
        manager = new SpuShareManager(spuRepository, storageClient,
                new StorageProperties(), wechatMiniApiClient, PAGE);
    }

    /** 在售/下架商品桩（技术键 + 业务键 + 短码成对，模拟同库真实行）。 */
    private Spu spu(int status) {
        return Spu.builder().id(SPU_TECHNICAL_ID).spuId(SPU_ID).shortCode(SHORT_CODE)
                .name("测试商品").status(status).build();
    }

    // ==================== 商品守卫 ====================

    @Test
    @DisplayName("getWxaCode — 业务键解析不到（商品不存在）→ 1103，不触达微信与存储")
    void shouldThrow1103_whenSpuAbsent() {
        when(spuRepository.findByBusinessId(SPU_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> manager.getWxaCode(SPU_ID));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND.getCode(), ex.getErrorCode().getCode());
        verifyNoInteractions(storageClient, wechatMiniApiClient);
    }

    @Test
    @DisplayName("getWxaCode — 商品下架（非在售）→ 1103")
    void shouldThrow1103_whenSpuOffShelf() {
        when(spuRepository.findByBusinessId(SPU_ID)).thenReturn(spu(SpuStatusEnum.OFF_SHELF.getCode()));

        BizException ex = assertThrows(BizException.class, () -> manager.getWxaCode(SPU_ID));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND.getCode(), ex.getErrorCode().getCode());
        verifyNoInteractions(storageClient, wechatMiniApiClient);
    }

    @Test
    @DisplayName("getWxaCode — 商品缺短码（NOT NULL 不变量被破坏）→ 500 fail-loud，绝不懒生成")
    void shouldFailLoud_whenShortCodeMissing() {
        Spu broken = Spu.builder().id(SPU_TECHNICAL_ID).spuId(SPU_ID).name("测试商品")
                .status(SpuStatusEnum.ON_SHELF.getCode()).build();
        when(spuRepository.findByBusinessId(SPU_ID)).thenReturn(broken);

        BizException ex = assertThrows(BizException.class, () -> manager.getWxaCode(SPU_ID));

        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
        verifyNoInteractions(storageClient, wechatMiniApiClient);
    }

    // ==================== 缓存路径 ====================

    @Test
    @DisplayName("getWxaCode — 缓存未命中：调微信生成 → 上传 MinIO → 返回预签名 URL（scene=短码）")
    void shouldGenerateUploadAndReturnUrl_whenCacheMissed() {
        when(spuRepository.findByBusinessId(SPU_ID)).thenReturn(spu(SpuStatusEnum.ON_SHELF.getCode()));
        when(storageClient.download(anyString(), eq(OBJECT_KEY))).thenReturn(null);
        when(wechatMiniApiClient.getWxaCodeUnlimited(SCENE, PAGE)).thenReturn(PNG_BYTES);
        when(storageClient.upload(anyString(), eq(OBJECT_KEY), any(InputStream.class), eq("image/png")))
                .thenReturn("http://minio/icedmall/" + OBJECT_KEY);
        when(storageClient.getPresignedUrl(anyString(), eq(OBJECT_KEY), anyInt()))
                .thenReturn("http://presigned/" + OBJECT_KEY);

        WxaCodeVO vo = manager.getWxaCode(SPU_ID);

        assertEquals("http://presigned/" + OBJECT_KEY, vo.getUrl());
        verify(wechatMiniApiClient).getWxaCodeUnlimited(SCENE, PAGE);
        verify(storageClient).upload(anyString(), eq(OBJECT_KEY), any(InputStream.class), eq("image/png"));
    }

    @Test
    @DisplayName("getWxaCode — 缓存命中：MinIO 已有对象 → 直返 URL，零微信调用零上传")
    void shouldReturnCachedUrlDirectly_whenObjectExists() {
        when(spuRepository.findByBusinessId(SPU_ID)).thenReturn(spu(SpuStatusEnum.ON_SHELF.getCode()));
        when(storageClient.download(anyString(), eq(OBJECT_KEY)))
                .thenReturn(new ByteArrayInputStream(PNG_BYTES));
        when(storageClient.getPresignedUrl(anyString(), eq(OBJECT_KEY), anyInt()))
                .thenReturn("http://presigned/" + OBJECT_KEY);

        WxaCodeVO vo = manager.getWxaCode(SPU_ID);

        assertEquals("http://presigned/" + OBJECT_KEY, vo.getUrl());
        verify(wechatMiniApiClient, never()).getWxaCodeUnlimited(anyString(), anyString());
        verify(storageClient, never())
                .upload(anyString(), anyString(), any(InputStream.class), anyString());
    }

    // ==================== 承载位口径（ADR-063 §决策 7 表 #2/#3） ====================

    @Test
    @DisplayName("承载位预算 — scene = 'p=' + 10 位短码 = 12 字符 ≤ 微信上限 32（业务键 34 字符超限的反证）")
    void shouldKeepSceneWithinWechatLimit() {
        assertEquals(12, SCENE.length());
        assertTrue(SCENE.length() <= WechatMiniApiClient.SCENE_MAX_LENGTH);
        assertTrue(ShortCode.isValid(SCENE.substring(2)), "scene 里放的必须是合法短码");
        // 反证：若按 32-hex 业务键直接拼 scene 即超限（这正是短码存在的唯一理由）
        assertTrue(("p=" + SPU_ID).length() > WechatMiniApiClient.SCENE_MAX_LENGTH);
    }

    @Test
    @DisplayName("objectKey — 内容派生：wxa-code/v1/product/{16 hex}.png，且不含任何内部标识")
    void shouldDeriveObjectKeyFromContent() {
        assertTrue(OBJECT_KEY.matches("^wxa-code/v1/product/[0-9a-f]{16}\\.png$"), OBJECT_KEY);
        // 键的「名字」只有 16 位内容哈希 —— 没有任何位置能塞进技术键 / 业务键 / 短码
        String name = OBJECT_KEY.substring("wxa-code/v1/product/".length(), OBJECT_KEY.length() - ".png".length());
        assertEquals(16, name.length());
        assertFalse(OBJECT_KEY.contains(SPU_ID), "对象键不得含业务键");
        assertFalse(OBJECT_KEY.contains(SHORT_CODE), "对象键不得含短码（它不是内容，是别名）");
    }

    @Test
    @DisplayName("objectKey — 同 scene+page 恒得同键（覆盖写天然幂等）；page 变则键变")
    void shouldBeStableForSameContent() {
        assertEquals(OBJECT_KEY, SpuShareManager.objectKeyOf(SCENE, PAGE));
        assertFalse(OBJECT_KEY.equals(SpuShareManager.objectKeyOf(SCENE, PAGE + "-v2")));
    }
}

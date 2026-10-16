package org.icedamericanomall.item.app.share;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.integration.auth.client.WechatMiniApiClient;
import org.icedamericanomall.integration.storage.StorageClient;
import org.icedamericanomall.integration.storage.StorageProperties;
import org.icedamericanomall.item.adapter.response.WxaCodeVO;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SpuShareManager 单元测试（P37）—— mock MinIO（{@link StorageClient}）与微信客户端，
 * 锁定：商品不存在/不在售 1103、缓存未命中「生成→上传→URL」、缓存命中直返（零微信调用）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpuShareManager —— 商品分享小程序码编排")
class SpuShareManagerTest {

    private static final Long SPU_ID = 7L;
    private static final String PAGE = "pages/product/detail/index";
    private static final String OBJECT_KEY = "wxa-code/product/7.png";
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

    /** 在售商品桩。 */
    private Spu spu(int status) {
        return Spu.builder().id(SPU_ID).name("测试商品").status(status).build();
    }

    // ==================== 商品守卫 ====================

    @Test
    @DisplayName("getWxaCode — 商品不存在 → 1103，不触达微信与存储")
    void shouldThrow1103_whenSpuAbsent() {
        when(spuRepository.findById(SPU_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> manager.getWxaCode(SPU_ID));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND.getCode(), ex.getErrorCode().getCode());
        verifyNoInteractions(storageClient, wechatMiniApiClient);
    }

    @Test
    @DisplayName("getWxaCode — 商品下架（非在售）→ 1103")
    void shouldThrow1103_whenSpuOffShelf() {
        when(spuRepository.findById(SPU_ID)).thenReturn(spu(SpuStatusEnum.OFF_SHELF.getCode()));

        BizException ex = assertThrows(BizException.class, () -> manager.getWxaCode(SPU_ID));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND.getCode(), ex.getErrorCode().getCode());
        verifyNoInteractions(storageClient, wechatMiniApiClient);
    }

    // ==================== 缓存路径 ====================

    @Test
    @DisplayName("getWxaCode — 缓存未命中：调微信生成 → 上传 MinIO → 返回预签名 URL")
    void shouldGenerateUploadAndReturnUrl_whenCacheMissed() {
        when(spuRepository.findById(SPU_ID)).thenReturn(spu(SpuStatusEnum.ON_SHELF.getCode()));
        when(storageClient.download(anyString(), eq(OBJECT_KEY))).thenReturn(null);
        when(wechatMiniApiClient.getWxaCodeUnlimited("p=" + SPU_ID, PAGE)).thenReturn(PNG_BYTES);
        when(storageClient.upload(anyString(), eq(OBJECT_KEY), any(InputStream.class), eq("image/png")))
                .thenReturn("http://minio/icedmall/" + OBJECT_KEY);
        when(storageClient.getPresignedUrl(anyString(), eq(OBJECT_KEY), anyInt()))
                .thenReturn("http://presigned/" + OBJECT_KEY);

        WxaCodeVO vo = manager.getWxaCode(SPU_ID);

        assertEquals("http://presigned/" + OBJECT_KEY, vo.getUrl());
        verify(wechatMiniApiClient).getWxaCodeUnlimited("p=" + SPU_ID, PAGE);
        verify(storageClient).upload(anyString(), eq(OBJECT_KEY), any(InputStream.class), eq("image/png"));
    }

    @Test
    @DisplayName("getWxaCode — 缓存命中：MinIO 已有对象 → 直返 URL，零微信调用零上传")
    void shouldReturnCachedUrlDirectly_whenObjectExists() {
        when(spuRepository.findById(SPU_ID)).thenReturn(spu(SpuStatusEnum.ON_SHELF.getCode()));
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
}

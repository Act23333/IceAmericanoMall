package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品分享小程序码（P37，契约 §4.4.x V5.18）—— 详情页「分享」位展示对象。
 *
 * <p>{@code url} 为 MinIO 预签名下载地址（发票批 {@code pdfKey} 同口径，短有效期临时访问）；
 * 生成结果按 productId 缓存于对象存储，二次请求直返缓存 URL。
 */
@Data
public class WxaCodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 小程序码图片访问 URL（预签名）。 */
    private String url;

    public static WxaCodeVO of(String url) {
        WxaCodeVO vo = new WxaCodeVO();
        vo.setUrl(url);
        return vo;
    }
}

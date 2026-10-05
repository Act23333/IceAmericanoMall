package org.icedamericanomall.trade.infrastructure.adapter;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.domain.port.InvoiceIssueParam;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * 发票样票 PDF 生成器（Phase 1 模拟开具，决策 O5）—— 版式从简：A4 单页 + 渲染位图。
 *
 * <p>位图渲染（中文票面与"样票"水印）委托 {@link InvoiceSampleRenderer}，本类只负责
 * 把位图包成 PDF 字节流（openpdf，无 CJK 字体包依赖）。
 */
@Component
@RequiredArgsConstructor
public class InvoicePdfGenerator {

    /** 位图与 A4 页边的留白（pt）。 */
    private static final float PAGE_MARGIN = 20f;

    private final InvoiceSampleRenderer sampleRenderer;

    /**
     * 生成样票 PDF 字节流。
     *
     * @param param         发票单要素快照
     * @param mockInvoiceNo 模拟票号
     * @return 非空 PDF 字节（{@code %PDF-} 头）
     */
    public byte[] generate(InvoiceIssueParam param, String mockInvoiceNo) {
        BufferedImage sample = sampleRenderer.render(param, mockInvoiceNo);
        Rectangle a4 = PageSize.A4;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(a4, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
            PdfWriter.getInstance(document, out);
            document.open();
            Image image = Image.getInstance(toPngBytes(sample));
            image.scaleToFit(a4.getWidth() - 2 * PAGE_MARGIN, a4.getHeight() - 2 * PAGE_MARGIN);
            image.setAlignment(Image.ALIGN_CENTER);
            document.add(image);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("发票样票 PDF 生成失败: " + e.getMessage(), e);
        }
    }

    /** BufferedImage → PNG 字节（openpdf Image 通用格式）。 */
    private byte[] toPngBytes(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}

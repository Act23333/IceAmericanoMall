package org.icedamericanomall.trade.infrastructure.adapter;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.domain.port.InvoiceIssueParam;
import org.springframework.stereotype.Component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 发票样票版面渲染器（Phase 1 模拟开具，决策 O5：简化版式 + "样票"水印）。
 *
 * <p>用 Java2D 把整张样票渲染为位图，再由 {@link InvoicePdfGenerator} 包进 PDF：
 * 中文票面（抬头/税号/水印）不依赖 openpdf 的 CJK 字体包，渲染能力随 JVM 系统字体，
 * 模拟环境版式从简。Phase 2 接税局后由服务商版式整体替换，本类随防腐适配器退役。
 */
@Slf4j
@Component
public class InvoiceSampleRenderer {

    /** 票面宽度（像素）。 */
    private static final int CANVAS_WIDTH = 1000;

    /** 票面高度（像素）。 */
    private static final int CANVAS_HEIGHT = 700;

    /** 水印文字（决策 O5：样票水印字样，明示非税务效力）。 */
    private static final String WATERMARK_TEXT = "样票";

    /** 票面标题。 */
    private static final String TITLE_TEXT = "电子普通发票（模拟样票）";

    /** 金额分 → 元展示换算（两位小数）。 */
    private static final int FEN_PER_YUAN = 100;

    /** 票面时间格式。 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 渲染样票版面（含斜向"样票"水印）。
     *
     * @param param         发票单要素快照
     * @param mockInvoiceNo 模拟票号
     * @return 样票位图
     */
    public BufferedImage render(InvoiceIssueParam param, String mockInvoiceNo) {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBackground(g);
            paintWatermark(g);
            paintContent(g, param, mockInvoiceNo);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** 白底 + 票面边框。 */
    private void paintBackground(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g.setColor(new Color(60, 60, 60));
        g.setStroke(new BasicStroke(3));
        g.drawRect(20, 20, CANVAS_WIDTH - 40, CANVAS_HEIGHT - 40);
    }

    /** 斜向"样票"水印（半透明，铺三处，明示模拟票非税务效力）。 */
    private void paintWatermark(Graphics2D g) {
        g.setColor(new Color(255, 0, 0, 45));
        Font watermarkFont = new Font(Font.SANS_SERIF, Font.BOLD, 110);
        g.setFont(watermarkFont);
        g.rotate(Math.toRadians(-30), CANVAS_WIDTH / 2.0, CANVAS_HEIGHT / 2.0);
        g.drawString(WATERMARK_TEXT, 130, 250);
        g.drawString(WATERMARK_TEXT, 420, 470);
        g.drawString(WATERMARK_TEXT, 120, 660);
        g.rotate(Math.toRadians(30), CANVAS_WIDTH / 2.0, CANVAS_HEIGHT / 2.0);
    }

    /** 票面要素（标题 + 键值行）。 */
    private void paintContent(Graphics2D g, InvoiceIssueParam param, String mockInvoiceNo) {
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        g.drawString(TITLE_TEXT, 250, 90);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
        int y = 160;
        y = drawField(g, y, "发票号码：" + mockInvoiceNo);
        y = drawField(g, y, "关联订单：" + param.orderNo());
        y = drawField(g, y, "开票金额：" + amountText(param.amount()));
        y = drawField(g, y, "购买方：" + param.title());
        if (param.taxNo() != null && !param.taxNo().isBlank()) {
            y = drawField(g, y, "纳税人识别号：" + param.taxNo());
        }
        if (param.email() != null && !param.email().isBlank()) {
            y = drawField(g, y, "交付邮箱：" + param.email());
        }
        y = drawField(g, y, "销售方（开票主体）商家编号：" + param.sellerId());
        drawField(g, y, "开具时间：" + TIME_FORMATTER.format(LocalDateTime.now()));
    }

    /** 画一行键值，返回下一行的纵坐标。 */
    private int drawField(Graphics2D g, int y, String text) {
        g.drawString(text, 90, y);
        return y + 50;
    }

    /** 金额（分）→ 元展示串（两位小数）。 */
    private String amountText(int amountInFen) {
        long yuan = amountInFen / FEN_PER_YUAN;
        long fen = Math.abs(amountInFen % FEN_PER_YUAN);
        return String.format("￥%d.%02d", yuan, fen);
    }
}

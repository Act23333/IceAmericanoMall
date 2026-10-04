package org.icedamericanomall.notification.adapter.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.notification.adapter.response.InAppMessageVO;
import org.icedamericanomall.notification.domain.model.inapp.InAppMessage;
import org.icedamericanomall.notification.domain.model.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 站内信出站映射单元测试 —— 领域分页 {@link PageResult} → MyBatis-Plus 分页信封。
 */
@DisplayName("站内信 — 领域分页 → 出站分页信封")
class InAppMessageVOTest {

    @Test
    @DisplayName("fromPage — 字段与分页元信息完整映射到 IPage 信封")
    void shouldConvertPageResult_toIPage() {
        InAppMessage message = new InAppMessage(9L, 1001L, 2002L, "商家", null,
                "REPLY", "回复", "内容", "/product/1", false,
                LocalDateTime.of(2026, 10, 1, 12, 0));
        PageResult<InAppMessage> page = new PageResult<>(List.of(message), 1L, 2L, 10L);

        IPage<InAppMessageVO> voPage = InAppMessageVO.fromPage(page);

        assertEquals(1, voPage.getRecords().size());
        assertEquals(1L, voPage.getTotal());
        assertEquals(2L, voPage.getCurrent());
        assertEquals(10L, voPage.getSize());
        InAppMessageVO vo = voPage.getRecords().get(0);
        assertEquals(9L, vo.getId());
        assertEquals("REPLY", vo.getType());
        assertEquals(0, vo.getIsRead());
        assertTrue(vo.isUnread());
    }

    @Test
    @DisplayName("PageResult — records 为 null 归一化为空列表")
    void shouldNormalizeNullRecords_toEmpty() {
        PageResult<InAppMessage> page = new PageResult<>(null, 0L, 1L, 10L);

        assertTrue(page.records().isEmpty());
        assertEquals(0L, page.total());
    }
}

package org.icedamericanomall.auth.app.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CaptchaVerifyParam — 装配语义（未携带 → null，厂商中立参数集）")
class CaptchaVerifyParamTest {

    @Test
    @DisplayName("参数集为空 / 全空值 → of() 返回 null（跳过人机验证）")
    void shouldReturnNull_whenNothingProvided() {
        assertNull(CaptchaVerifyParam.of("GEETEST", null, "1.2.3.4"));
        assertNull(CaptchaVerifyParam.of("GEETEST", Map.of(), "1.2.3.4"));
        assertNull(CaptchaVerifyParam.of("GEETEST", Map.of("lotNumber", "  "), "1.2.3.4"));
    }

    @Test
    @DisplayName("任一参数非空 → of() 返回完整参数对象（防御性拷贝 + null 项剔除）")
    void shouldReturnParam_whenAnyElementProvided() {
        Map<String, String> raw = new HashMap<>();
        raw.put("lotNumber", "lot");
        raw.put("junk", null);

        CaptchaVerifyParam param = CaptchaVerifyParam.of("GEETEST", raw, "1.2.3.4");

        assertNotNull(param);
        assertEquals("lot", param.params().get("lotNumber"));
        assertFalse(param.params().containsKey("junk"), "null 值项应被剔除");
        assertEquals("1.2.3.4", param.clientIp());
    }

    @Test
    @DisplayName("isProvided — 残缺参数（仅一项非空）也算携带，交由远端 fail-closed 判定")
    void shouldMarkPartialParamsAsProvided() {
        assertTrue(new CaptchaVerifyParam("GEETEST", Map.of("lotNumber", "lot"), "1.2.3.4").isProvided());
    }
}

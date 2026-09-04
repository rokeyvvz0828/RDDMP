package com.ccb.testmanagement.announcement;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAnnouncementSanitizerTest {
    @Test
    void retainsSupportedEditorStylesAndDropsUnsafeStyles() throws Exception {
        Method method = TestAnnouncementService.class.getDeclaredMethod("sanitize", String.class);
        method.setAccessible(true);

        String sanitized = (String) method.invoke(null, "<h2 style=\"text-align:center;color:#1677ff\">测试公告</h2><p><span style=\"background-color:rgb(255, 247, 230);font-size:16px;font-family:微软雅黑;line-height:1.5\">请验证</span></p><p style=\"position:fixed;background-image:url(https://bad.example/a);text-indent:2em\" onclick=\"alert(1)\">正文</p><script>alert(2)</script>");

        assertTrue(sanitized.contains("<h2 style=\"text-align:center;color:#1677ff\">测试公告</h2>"));
        assertTrue(sanitized.contains("background-color:rgb(255, 247, 230);font-size:16px;font-family:微软雅黑;line-height:1.5"));
        assertTrue(sanitized.contains("style=\"text-indent:2em\""));
        assertFalse(sanitized.contains("onclick"));
        assertFalse(sanitized.contains("position:fixed"));
        assertFalse(sanitized.contains("background-image"));
        assertFalse(sanitized.contains("script"));
    }
}

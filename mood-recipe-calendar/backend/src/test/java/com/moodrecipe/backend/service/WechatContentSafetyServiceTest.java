package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatContentSafetyServiceTest {
    @Test
    void productionFailsClosedWhenWechatCredentialsAreMissing() {
        WechatContentSafetyService production = new WechatContentSafetyService(
                new ObjectMapper(), "", "", true, true);
        assertFalse(production.allowsText("user-1", "测试文字"));

        WechatContentSafetyService development = new WechatContentSafetyService(
                new ObjectMapper(), "", "", false, false);
        assertTrue(development.allowsText("user-1", "测试文字"));
    }
}

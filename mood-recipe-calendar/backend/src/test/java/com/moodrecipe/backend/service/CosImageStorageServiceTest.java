package com.moodrecipe.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CosImageStorageServiceTest {

    @Test
    void keepsAiImageUrlWhenCosTransferFails() {
        CosImageStorageService service = new CosImageStorageService();
        ReflectionTestUtils.setField(service, "cosEnabled", true);
        ReflectionTestUtils.setField(service, "cosSecretId", "id");
        ReflectionTestUtils.setField(service, "cosSecretKey", "key");
        ReflectionTestUtils.setField(service, "cosRegion", "ap-test");
        ReflectionTestUtils.setField(service, "cosBucket", "bucket");
        String aiImageUrl = "https://127.0.0.1:1/generated-cover.png";

        assertEquals(aiImageUrl, service.transferFromUrl(aiImageUrl, "recipes/").orElseThrow());
    }
}

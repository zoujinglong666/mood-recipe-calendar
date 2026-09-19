package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.VirtualOrder;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.VirtualOrderRepository;
import com.moodrecipe.backend.repository.VirtualProductRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WechatVirtualPaymentServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final WechatVirtualPaymentService service = new WechatVirtualPaymentService(
            mock(VirtualOrderRepository.class), mock(VirtualProductRepository.class), mock(UserRepository.class),
            mock(SessionKeyCipher.class), json, mock(VirtualCommerceService.class), mock(OperationalEventService.class));

    @Test
    void recognizesPaidIosOrderAndRejectsMismatchedAmount() throws Exception {
        VirtualOrder order = new VirtualOrder();
        order.setOrderNo("VP-IOS");
        order.setAmountFen(990);

        var paid = service.parseQueryOrder(order, json.readTree("""
                {"errcode":0,"order":{"order_id":"VP-IOS","status":2,"order_fee":990,"wx_order_id":"WX-IOS"}}
                """));
        assertTrue(paid.paid());
        assertTrue("WX-IOS".equals(paid.platformOrderNo()));

        var pending = service.parseQueryOrder(order, json.readTree("""
                {"errcode":0,"order":{"order_id":"VP-IOS","status":1,"order_fee":990}}
                """));
        assertFalse(pending.paid());

        assertThrows(IllegalStateException.class, () -> service.parseQueryOrder(order, json.readTree("""
                {"errcode":0,"order":{"order_id":"VP-IOS","status":2,"order_fee":1}}
                """)));
    }
}

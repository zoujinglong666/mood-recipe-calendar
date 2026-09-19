package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.entity.VirtualOrder;
import com.moodrecipe.backend.entity.VirtualProduct;
import com.moodrecipe.backend.repository.VirtualOrderRepository;
import com.moodrecipe.backend.repository.VirtualProductRepository;
import com.moodrecipe.backend.service.OperationalEventService;
import com.moodrecipe.backend.service.VirtualCommerceService;
import com.moodrecipe.backend.service.WechatMessageCrypto;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WechatVirtualPaymentCallbackControllerTest {
    @Test
    void rejectsInvalidSignatureAndTamperedAmount() {
        VirtualOrderRepository orders = mock(VirtualOrderRepository.class);
        VirtualProductRepository products = mock(VirtualProductRepository.class);
        VirtualCommerceService commerce = mock(VirtualCommerceService.class);
        WechatMessageCrypto crypto = mock(WechatMessageCrypto.class);
        OperationalEventService events = mock(OperationalEventService.class);
        WechatVirtualPaymentCallbackController controller = new WechatVirtualPaymentCallbackController(
                orders, products, commerce, crypto, events);

        Map<String, Object> body = Map.of("Event", "xpay_goods_deliver_notify");
        assertEquals(403, controller.deliver("bad", null, "1", "n", body).getStatusCode().value());
        verifyNoInteractions(commerce);

        when(crypto.verify("ok", "1", "n", null)).thenReturn(true);
        VirtualOrder order = new VirtualOrder();
        order.setOrderNo("VP1");
        order.setOpenid("user-1");
        order.setSku("MEMBER");
        order.setAmountFen(990);
        VirtualProduct product = new VirtualProduct();
        product.setPlatformItemId("GZHY_30D");
        when(orders.findByOrderNo("VP1")).thenReturn(Optional.of(order));
        when(products.findById("MEMBER")).thenReturn(Optional.of(product));
        Map<String, Object> tampered = Map.of(
                "Event", "xpay_goods_deliver_notify", "OutTradeNo", "VP1", "OpenId", "user-1",
                "GoodsInfo", Map.of("ProductId", "GZHY_30D", "ActualPrice", "1"),
                "WeChatPayInfo", Map.of("TransactionId", "tx-1"));

        assertEquals(500, controller.deliver("ok", null, "1", "n", tampered).getStatusCode().value());
        verify(commerce, never()).fulfillPaidOrder(anyString(), anyString());
    }

    @Test
    void acceptsEncryptedIosDeliveryWithoutWechatPayInfo() {
        VirtualOrderRepository orders = mock(VirtualOrderRepository.class);
        VirtualProductRepository products = mock(VirtualProductRepository.class);
        VirtualCommerceService commerce = mock(VirtualCommerceService.class);
        WechatMessageCrypto crypto = mock(WechatMessageCrypto.class);
        WechatVirtualPaymentCallbackController controller = new WechatVirtualPaymentCallbackController(
                orders, products, commerce, crypto, mock(OperationalEventService.class));

        VirtualOrder order = new VirtualOrder();
        order.setOrderNo("VP-IOS");
        order.setOpenid("user-1");
        order.setSku("MEMBER");
        order.setAmountFen(990);
        VirtualProduct product = new VirtualProduct();
        product.setPlatformItemId("GZHY_30D");
        Map<String, Object> decrypted = Map.of(
                "Event", "xpay_goods_deliver_notify", "OutTradeNo", "VP-IOS", "OpenId", "user-1",
                "GoodsInfo", Map.of("ProductId", "GZHY_30D", "ActualPrice", "990"));

        when(crypto.verify("safe-signature", "1", "n", "ciphertext")).thenReturn(true);
        when(crypto.decryptJson("ciphertext")).thenReturn(decrypted);
        when(orders.findByOrderNo("VP-IOS")).thenReturn(Optional.of(order));
        when(products.findById("MEMBER")).thenReturn(Optional.of(product));

        assertEquals(200, controller.deliver(null, "safe-signature", "1", "n",
                Map.of("Encrypt", "ciphertext")).getStatusCode().value());
        verify(commerce).fulfillPaidOrder("VP-IOS", "VP-IOS");
    }
}

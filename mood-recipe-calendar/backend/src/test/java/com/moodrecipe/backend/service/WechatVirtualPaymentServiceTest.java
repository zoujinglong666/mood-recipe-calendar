package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.VirtualOrder;
import com.moodrecipe.backend.service.WxPusherNotifier;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.VirtualOrderRepository;
import com.moodrecipe.backend.repository.VirtualProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatVirtualPaymentServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final VirtualOrderRepository orders = mock(VirtualOrderRepository.class);
    private final WechatVirtualPaymentService service = new WechatVirtualPaymentService(
            orders, mock(VirtualProductRepository.class), mock(UserRepository.class),
            mock(SessionKeyCipher.class), json, mock(VirtualCommerceService.class), mock(OperationalEventService.class),
            mock(WxPusherNotifier.class));

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

    // ---------- 超时未支付订单关闭（本地终态化，微信无关单 API） ----------

    @Test
    void paidOrderIsNeverCancelled() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 12, 0);
        assertFalse(WechatVirtualPaymentService.shouldCancelTimedOutOrder(
                now.minusDays(30), now, true));
    }

    @Test
    void confirmedUnpaidOrderIsCancelled() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 12, 0);
        assertTrue(WechatVirtualPaymentService.shouldCancelTimedOutOrder(
                now.minusHours(25), now, false));
    }

    /** 查单失败（多为从未拉起支付、微信侧无此单）：7 天内不关，留着下轮再查。 */
    @Test
    void unconfirmableOrderIsOnlyCancelledAfterSevenDays() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 12, 0);
        assertFalse(WechatVirtualPaymentService.shouldCancelTimedOutOrder(
                now.minusDays(3), now, null));
        assertTrue(WechatVirtualPaymentService.shouldCancelTimedOutOrder(
                now.minusDays(7).minusMinutes(1), now, null));
    }

    /** 微信侧确认未支付的超时单：本地置 CANCELLED。 */
    @Test
    void scanCancelsConfirmedUnpaidOrder() throws Exception {
        VirtualOrder order = pendingOrder("VP-UNPAID", AppClock.now().minusHours(25));
        WechatVirtualPaymentService spy = configuredSpy();
        when(orders.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq("PENDING"), any()))
                .thenReturn(List.of(order));
        doReturn(new WechatVirtualPaymentService.QueryOrderResult(false, "WX-1"))
                .when(spy).queryOrder(any());

        spy.cancelTimedOutOrders();

        assertEquals("CANCELLED", order.getStatus());
        verify(orders).save(order);
    }

    /** 保险：扫描撞上「其实已支付」的订单，必须补发货而不是关闭。 */
    @Test
    void scanFulfillsPaidOrderInsteadOfCancelling() throws Exception {
        VirtualOrder order = pendingOrder("VP-PAID", AppClock.now().minusHours(25));
        WechatVirtualPaymentService spy = configuredSpy();
        when(orders.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq("PENDING"), any()))
                .thenReturn(List.of(order));
        doReturn(new WechatVirtualPaymentService.QueryOrderResult(true, "WX-2"))
                .when(spy).queryOrder(any());
        doNothing().when(spy).fulfillAndNotify(anyString(), anyString(), anyString());

        spy.cancelTimedOutOrders();

        assertEquals("PENDING", order.getStatus());
        verify(spy).fulfillAndNotify("openid-1", "VP-PAID", "WX-2");
        verify(orders, never()).save(any());
    }

    /** 查单失败且单龄 < 7 天：本轮跳过，绝不动状态。 */
    @Test
    void scanSkipsUnconfirmableOrderWithinSevenDays() throws Exception {
        VirtualOrder order = pendingOrder("VP-FRESH", AppClock.now().minusDays(3));
        WechatVirtualPaymentService spy = configuredSpy();
        when(orders.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq("PENDING"), any()))
                .thenReturn(List.of(order));
        doThrow(new IllegalStateException("订单不存在")).when(spy).queryOrder(any());

        spy.cancelTimedOutOrders();

        assertEquals("PENDING", order.getStatus());
        verify(orders, never()).save(any());
    }

    /** 无 Spring 容器时 @Value 字段全为空，serverApiConfigured 会直接短路；
     *  反射补齐配置项，让扫描真正跑起来。 */
    private WechatVirtualPaymentService configuredSpy() {
        WechatVirtualPaymentService spy = spy(service);
        ReflectionTestUtils.setField(spy, "environment", 0);
        ReflectionTestUtils.setField(spy, "offerId", "offer-1");
        ReflectionTestUtils.setField(spy, "appKey", "key-1");
        ReflectionTestUtils.setField(spy, "appid", "wx-test");
        ReflectionTestUtils.setField(spy, "secret", "secret-1");
        ReflectionTestUtils.setField(spy, "orderTimeoutHours", 24);
        return spy;
    }

    private VirtualOrder pendingOrder(String orderNo, LocalDateTime createdAt) {
        VirtualOrder order = new VirtualOrder();
        order.setOrderNo(orderNo);
        order.setOpenid("openid-1");
        order.setStatus("PENDING");
        order.setCreatedAt(createdAt);
        return order;
    }
}

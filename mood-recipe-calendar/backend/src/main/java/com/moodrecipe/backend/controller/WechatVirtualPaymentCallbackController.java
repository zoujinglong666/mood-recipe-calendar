package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.entity.VirtualOrder;
import com.moodrecipe.backend.entity.VirtualProduct;
import com.moodrecipe.backend.repository.VirtualOrderRepository;
import com.moodrecipe.backend.repository.VirtualProductRepository;
import com.moodrecipe.backend.service.VirtualCommerceService;
import com.moodrecipe.backend.service.WechatMessageCrypto;
import com.moodrecipe.backend.service.OperationalEventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 微信小程序消息推送中的虚拟支付异步发货入口。 */
@RestController
@RequestMapping("/api/wechat/virtual-payment/callback")
public class WechatVirtualPaymentCallbackController {

    private final VirtualOrderRepository orderRepository;
    private final VirtualProductRepository productRepository;
    private final VirtualCommerceService commerceService;
    private final WechatMessageCrypto messageCrypto;
    private final OperationalEventService operationalEvents;

    public WechatVirtualPaymentCallbackController(
            VirtualOrderRepository orderRepository,
            VirtualProductRepository productRepository,
            VirtualCommerceService commerceService,
            WechatMessageCrypto messageCrypto, OperationalEventService operationalEvents
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.commerceService = commerceService;
        this.messageCrypto = messageCrypto;
        this.operationalEvents = operationalEvents;
    }

    /** 微信后台首次配置消息推送 URL 时的握手校验。 */
    @GetMapping
    public ResponseEntity<String> verifyUrl(
            @RequestParam("signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr
    ) {
        return messageCrypto.verify(signature, timestamp, nonce, null)
                ? ResponseEntity.ok(echostr)
                : ResponseEntity.status(HttpStatus.FORBIDDEN).body("invalid signature");
    }

    /**
     * 仅接受通过微信消息推送 Token 校验的明文发货事件。权益发放以 OutTradeNo 为幂等键，
     * 客户端 success 回调不会触发这里的发货逻辑。
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> deliver(
            @RequestParam("signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestBody Map<String, Object> body
    ) {
        String encrypted = stringValue(body.get("Encrypt"));
        if (encrypted == null) encrypted = stringValue(body.get("encrypt"));
        if (!messageCrypto.verify(signature, timestamp, nonce, encrypted)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(fail("invalid signature"));
        }
        try {
            if (encrypted != null) body = messageCrypto.decryptJson(encrypted);
            if (!"xpay_goods_deliver_notify".equals(String.valueOf(body.get("Event")))) {
                return ResponseEntity.ok(ok());
            }
            String orderNo = required(body, "OutTradeNo");
            VirtualOrder order = orderRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
            if (!order.getOpenid().equals(required(body, "OpenId"))) {
                throw new IllegalArgumentException("支付用户不匹配");
            }
            Map<String, Object> goodsInfo = object(body.get("GoodsInfo"), "GoodsInfo");
            VirtualProduct product = productRepository.findById(order.getSku())
                    .orElseThrow(() -> new IllegalArgumentException("订单商品不存在"));
            if (product.getPlatformItemId() == null || !product.getPlatformItemId().equals(required(goodsInfo, "ProductId"))) {
                throw new IllegalArgumentException("支付道具不匹配");
            }
            if (order.getAmountFen() != Integer.parseInt(required(goodsInfo, "ActualPrice"))) {
                throw new IllegalArgumentException("支付金额不匹配");
            }
            Map<String, Object> payInfo = object(body.get("WeChatPayInfo"), "WeChatPayInfo");
            commerceService.fulfillPaidOrder(orderNo, required(payInfo, "TransactionId"));
            return ResponseEntity.ok(ok());
        } catch (Exception exception) {
            operationalEvents.record("VIRTUAL_PAYMENT_DELIVERY_FAILED", "ALERT", null, null, "callback delivery validation failed");
            // 失败时让微信重试；同一订单的重复通知会由 source_order_no 唯一约束安全处理。
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(fail("delivery failed"));
        }
    }

    private String stringValue(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value, String field) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException(field + " 格式错误");
    }

    private String required(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return String.valueOf(value);
    }

    private Map<String, Object> ok() {
        return Map.of("ErrCode", 0, "ErrMsg", "success");
    }

    private Map<String, Object> fail(String message) {
        return Map.of("ErrCode", -1, "ErrMsg", message);
    }
}

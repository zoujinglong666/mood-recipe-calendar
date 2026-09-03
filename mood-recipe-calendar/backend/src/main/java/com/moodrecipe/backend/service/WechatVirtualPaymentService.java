package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.entity.VirtualOrder;
import com.moodrecipe.backend.entity.VirtualProduct;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.VirtualOrderRepository;
import com.moodrecipe.backend.repository.VirtualProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** 为 wx.requestVirtualPayment 生成服务端签名参数。 */
@Service
public class WechatVirtualPaymentService {

    private final VirtualOrderRepository orderRepository;
    private final VirtualProductRepository productRepository;
    private final UserRepository userRepository;
    private final SessionKeyCipher sessionKeyCipher;
    private final ObjectMapper objectMapper;

    @Value("${wechat.virtual-payment.offer-id:}")
    private String offerId;

    @Value("${wechat.virtual-payment.app-key:}")
    private String appKey;

    @Value("${wechat.virtual-payment.environment:0}")
    private int environment;

    public WechatVirtualPaymentService(
            VirtualOrderRepository orderRepository,
            VirtualProductRepository productRepository,
            UserRepository userRepository,
            SessionKeyCipher sessionKeyCipher,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.sessionKeyCipher = sessionKeyCipher;
        this.objectMapper = objectMapper;
    }

    public VirtualPaymentParams createPaymentParams(String orderNo, String openid) {
        if (offerId == null || offerId.isBlank() || appKey == null || appKey.isBlank()) {
            throw new IllegalStateException("尚未配置微信虚拟支付 OfferID/AppKey");
        }
        if (!sessionKeyCipher.isConfigured()) {
            throw new IllegalStateException("尚未配置 session_key 加密密钥");
        }
        if (environment != 0 && environment != 1) {
            throw new IllegalStateException("虚拟支付环境仅支持 0（正式）或 1（沙箱）");
        }

        VirtualOrder order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getOpenid().equals(openid)) throw new IllegalArgumentException("无权支付该订单");
        if (!"PENDING".equals(order.getStatus())) throw new IllegalStateException("订单当前不可支付");

        VirtualProduct product = productRepository.findById(order.getSku())
                .orElseThrow(() -> new IllegalStateException("订单商品不存在"));
        if (product.getPlatformItemId() == null || product.getPlatformItemId().isBlank()) {
            throw new IllegalStateException("该商品尚未配置微信虚拟支付道具 ID");
        }
        User user = userRepository.findByOpenid(openid)
                .orElseThrow(() -> new IllegalStateException("用户不存在"));
        if (user.getSessionKeyEncrypted() == null || user.getSessionKeyEncrypted().isBlank()) {
            throw new IllegalStateException("支付登录态已失效，请重新登录后再试");
        }

        try {
            Map<String, Object> signDataObject = new LinkedHashMap<>();
            signDataObject.put("offerId", offerId);
            signDataObject.put("buyQuantity", 1);
            signDataObject.put("env", environment);
            signDataObject.put("currencyType", "CNY");
            signDataObject.put("productId", product.getPlatformItemId());
            signDataObject.put("goodsPrice", order.getAmountFen());
            signDataObject.put("outTradeNo", order.getOrderNo());
            signDataObject.put("attach", order.getOrderNo());
            String signData = objectMapper.writeValueAsString(signDataObject);
            String paySig = hmacSha256(appKey, "requestVirtualPayment&" + signData);
            String signature = hmacSha256(sessionKeyCipher.decrypt(user.getSessionKeyEncrypted()), signData);
            return new VirtualPaymentParams("short_series_goods", signData, paySig, signature);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("生成虚拟支付签名失败", exception);
        }
    }

    private String hmacSha256(String secret, String source) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(source.getBytes(StandardCharsets.UTF_8)));
    }

    /** 返回给小程序的内容；AppKey 与 session_key 永不离开服务端。 */
    public record VirtualPaymentParams(String mode, String signData, String paySig, String signature) { }
}

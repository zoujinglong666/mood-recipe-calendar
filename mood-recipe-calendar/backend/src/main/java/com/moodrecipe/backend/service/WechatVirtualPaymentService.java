package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.entity.VirtualOrder;
import com.moodrecipe.backend.entity.VirtualProduct;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.VirtualOrderRepository;
import com.moodrecipe.backend.repository.VirtualProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 为 wx.requestVirtualPayment 生成服务端签名参数。 */
@Service
public class WechatVirtualPaymentService {
    private static final Logger log = LoggerFactory.getLogger(WechatVirtualPaymentService.class);

    private final VirtualOrderRepository orderRepository;
    private final VirtualProductRepository productRepository;
    private final UserRepository userRepository;
    private final SessionKeyCipher sessionKeyCipher;
    private final ObjectMapper objectMapper;
    private final VirtualCommerceService commerceService;
    private final OperationalEventService operationalEvents;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile String accessToken = "";
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    @Value("${wechat.virtual-payment.offer-id:}")
    private String offerId;

    @Value("${wechat.virtual-payment.app-key:}")
    private String appKey;

    @Value("${wechat.virtual-payment.environment:0}")
    private int environment;

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    public WechatVirtualPaymentService(
            VirtualOrderRepository orderRepository,
            VirtualProductRepository productRepository,
            UserRepository userRepository,
            SessionKeyCipher sessionKeyCipher,
            ObjectMapper objectMapper,
            VirtualCommerceService commerceService,
            OperationalEventService operationalEvents
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.sessionKeyCipher = sessionKeyCipher;
        this.objectMapper = objectMapper;
        this.commerceService = commerceService;
        this.operationalEvents = operationalEvents;
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

    /** 发货推送丢失时主动向微信查单；只补最近七天、已等待至少一分钟的订单。 */
    @Scheduled(
            fixedDelayString = "${wechat.virtual-payment.reconcile-delay-ms:300000}",
            initialDelayString = "${wechat.virtual-payment.reconcile-initial-delay-ms:60000}")
    public void reconcilePendingOrders() {
        if (!serverApiConfigured()) return;
        LocalDateTime now = LocalDateTime.now();
        int failures = 0;
        for (VirtualOrder order : orderRepository.findTop100ByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                "PENDING", now.minusDays(7), now.minusMinutes(1))) {
            try {
                QueryOrderResult result = queryOrder(order);
                if (result.paid()) commerceService.fulfillPaidOrder(order.getOrderNo(), result.platformOrderNo());
            } catch (Exception exception) {
                failures++;
                log.warn("微信虚拟支付查单失败 orderNo={}: {}", order.getOrderNo(), exception.toString());
            }
        }
        if (failures > 0) {
            operationalEvents.record("VIRTUAL_PAYMENT_RECONCILE_FAILED", "WARN", null, null,
                    failures + " virtual payment orders failed to reconcile");
        }
    }

    QueryOrderResult queryOrder(VirtualOrder order) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("openid", order.getOpenid());
        requestBody.put("env", environment);
        requestBody.put("order_id", order.getOrderNo());
        String body = objectMapper.writeValueAsString(requestBody);
        String paySig = hmacSha256(appKey, "/xpay/query_order&" + body);
        URI uri = URI.create("https://api.weixin.qq.com/xpay/query_order?access_token="
                + URLEncoder.encode(accessToken(), StandardCharsets.UTF_8) + "&pay_sig=" + paySig);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("微信查单 HTTP " + response.statusCode());
        return parseQueryOrder(order, objectMapper.readTree(response.body()));
    }

    QueryOrderResult parseQueryOrder(VirtualOrder localOrder, JsonNode response) {
        if (response.path("errcode").asInt(-1) != 0) {
            throw new IllegalStateException("微信查单失败: " + response.path("errmsg").asText("unknown"));
        }
        JsonNode remoteOrder = response.path("order");
        if (!localOrder.getOrderNo().equals(remoteOrder.path("order_id").asText())) {
            throw new IllegalStateException("微信查单订单号不匹配");
        }
        if (localOrder.getAmountFen() != remoteOrder.path("order_fee").asInt(-1)) {
            throw new IllegalStateException("微信查单金额不匹配");
        }
        int status = remoteOrder.path("status").asInt(-1);
        String platformOrderNo = firstNonBlank(
                remoteOrder.path("wx_order_id").asText(null),
                remoteOrder.path("channel_order_id").asText(null),
                remoteOrder.path("wxpay_order_id").asText(null),
                localOrder.getOrderNo());
        return new QueryOrderResult(status >= 2 && status <= 4, platformOrderNo);
    }

    private boolean serverApiConfigured() {
        return environment == 0 && offerId != null && !offerId.isBlank()
                && appKey != null && !appKey.isBlank() && appid != null && !appid.isBlank()
                && secret != null && !secret.isBlank();
    }

    private synchronized String accessToken() throws Exception {
        if (!accessToken.isBlank() && Instant.now().isBefore(tokenExpiresAt)) return accessToken;
        URI uri = URI.create("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                + URLEncoder.encode(appid, StandardCharsets.UTF_8) + "&secret="
                + URLEncoder.encode(secret, StandardCharsets.UTF_8));
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        accessToken = json.path("access_token").asText();
        if (accessToken.isBlank()) throw new IllegalStateException("获取微信 access_token 失败: " + json.path("errmsg").asText());
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, json.path("expires_in").asLong(7200) - 300));
        return accessToken;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        throw new IllegalArgumentException("支付平台订单号不能为空");
    }

    /** 返回给小程序的内容；AppKey 与 session_key 永不离开服务端。 */
    public record VirtualPaymentParams(String mode, String signData, String paySig, String signature) { }
    record QueryOrderResult(boolean paid, String platformOrderNo) { }
}

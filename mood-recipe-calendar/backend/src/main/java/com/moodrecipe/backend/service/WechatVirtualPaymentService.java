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

    /** 发货后是否回传微信 notify_deliver。沙箱联调若该模式不支持可置 false。 */
    @Value("${wechat.virtual-payment.notify-deliver-enabled:true}")
    private boolean notifyDeliverEnabled;

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

    /** 列出商品时预判断该商品当前是否可购买，并返回不可购买的文案。 */
    public PaymentAvailability getAvailability(VirtualProduct product) {
        if (offerId == null || offerId.isBlank() || appKey == null || appKey.isBlank()) {
            return new PaymentAvailability(false, "支付服务未配置");
        }
        if (!sessionKeyCipher.isConfigured()) {
            return new PaymentAvailability(false, "支付登录态加密未配置");
        }
        if (environment != 0 && environment != 1) {
            return new PaymentAvailability(false, "支付环境配置异常");
        }
        if (product.getPlatformItemId() == null || product.getPlatformItemId().isBlank()) {
            return new PaymentAvailability(false, "会员道具 ID 配置后即可开通");
        }
        return new PaymentAvailability(true, "");
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
                if (result.paid()) fulfillAndNotify(order.getOpenid(), order.getOrderNo(), result.platformOrderNo());
            } catch (Exception exception) {
                failures++;
                log.warn("微信虚拟支付查单失败 orderNo={}: {}", order.getOrderNo(), exception.toString());
            }
        }
        // 已发货但微信侧尚未收到发货推送的订单，按重试计数重推（最多 MAX_NOTIFY_ATTEMPTS 次）。
        for (VirtualOrder order : orderRepository
                .findTop100ByStatusAndWxNotifiedFalseAndNotifyAttemptsLessThanAndCreatedAtBetweenOrderByCreatedAtAsc(
                        "DELIVERED", MAX_NOTIFY_ATTEMPTS, now.minusDays(7), now.minusMinutes(1))) {
            try {
                fulfillAndNotify(order.getOpenid(), order.getOrderNo(), order.getPlatformTransactionId());
            } catch (Exception exception) {
                failures++;
                log.warn("微信发货推送重试失败 orderNo={}: {}", order.getOrderNo(), exception.toString());
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

    /**
     * 发货后回传微信发货状态（notify_deliver）。短系列虚拟商品需在发货后通知微信，
     * 否则订单可能停留在未发货、无法结算。本调用幂等（按 out_trade_no），失败仅告警，
     * 不回滚已发放的本地权益；是否启用由 notify-deliver-enabled 控制。
     */
    public boolean notifyDeliver(String openid, String outTradeNo) {
        if (!notifyDeliverEnabled || !serverApiConfigured()) return true;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("openid", openid);
            body.put("env", environment);
            body.put("out_trade_no", outTradeNo);
            body.put("deliver_type", 1);
            body.put("deliver_msg", "");
            String payload = objectMapper.writeValueAsString(body);
            String paySig = hmacSha256(appKey, "/xpay/notify_deliver&" + payload);
            URI uri = URI.create("https://api.weixin.qq.com/xpay/notify_deliver?access_token="
                    + URLEncoder.encode(accessToken(), StandardCharsets.UTF_8) + "&pay_sig=" + paySig);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("微信发货推送 HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("errcode").asInt(-1) != 0) {
                throw new IllegalStateException("微信发货推送失败: " + root.path("errmsg").asText("unknown"));
            }
            return true;
        } catch (Exception exception) {
            log.warn("微信发货推送失败 outTradeNo={}: {}", outTradeNo, exception.toString());
            operationalEvents.record("VIRTUAL_PAYMENT_NOTIFY_DELIVER_FAILED", "WARN", null, null,
                    "notify_deliver failed for " + outTradeNo + ": " + exception.getMessage());
            return false;
        }
    }

    private static final int MAX_NOTIFY_ATTEMPTS = 5;

    /** 统一编排：发放权益 + 回传微信发货状态（带失败重试计数），供回调与查单兜底共同使用。 */
    public void fulfillAndNotify(String openid, String orderNo, String platformTransactionId) {
        commerceService.fulfillPaidOrder(orderNo, platformTransactionId);
        VirtualOrder order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null) return;
        int attempts = order.getNotifyAttempts() == null ? 0 : order.getNotifyAttempts();
        if (attempts >= MAX_NOTIFY_ATTEMPTS) return;
        boolean ok = notifyDeliver(openid, orderNo);
        order.setNotifyAttempts(attempts + 1);
        if (ok) order.setWxNotified(true);
        orderRepository.save(order);
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
    /** 商品是否可购买的预判定结果；message 为不可购买时的展示文案。 */
    public record PaymentAvailability(boolean available, String message) { }
    record QueryOrderResult(boolean paid, String platformOrderNo) { }
}

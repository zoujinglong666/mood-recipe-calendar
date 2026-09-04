package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.UserEntitlement;
import com.moodrecipe.backend.entity.VirtualOrder;
import com.moodrecipe.backend.entity.VirtualProduct;
import com.moodrecipe.backend.service.VirtualCommerceService;
import com.moodrecipe.backend.service.WechatVirtualPaymentService;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 小程序端读取数字商品、创建订单和查看已获权益的接口。 */
@RestController
@RequestMapping("/api/virtual-commerce")
public class VirtualCommerceController {

    private final VirtualCommerceService commerceService;
    private final WechatVirtualPaymentService paymentService;

    public VirtualCommerceController(VirtualCommerceService commerceService, WechatVirtualPaymentService paymentService) {
        this.commerceService = commerceService;
        this.paymentService = paymentService;
    }

    @GetMapping("/products")
    public ApiResponse<List<VirtualProduct>> products() {
        return ApiResponse.ok(commerceService.listProducts());
    }

    @PostMapping("/orders")
    public ApiResponse<VirtualOrder> createOrder(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody CreateOrderRequest request) {
        try {
            return ApiResponse.ok(commerceService.createOrder(openid, request.sku()));
        } catch (IllegalArgumentException exception) {
            return ApiResponse.error(400, exception.getMessage());
        }
    }

    /**
     * 服务端生成 wx.requestVirtualPayment 的三项签名参数。
     * 前端不能自行拼价格、道具 ID、签名或修改订单号。
     */
    @PostMapping("/orders/{orderNo}/payment-params")
    public ApiResponse<WechatVirtualPaymentService.VirtualPaymentParams> paymentParams(
            @PathVariable String orderNo,
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody PaymentParamsRequest request
    ) {
        try {
            return ApiResponse.ok(paymentService.createPaymentParams(orderNo, openid));
        } catch (IllegalArgumentException exception) {
            return ApiResponse.error(400, exception.getMessage());
        } catch (IllegalStateException exception) {
            return ApiResponse.error(409, exception.getMessage());
        }
    }

    @GetMapping("/entitlements")
    public ApiResponse<List<UserEntitlement>> entitlements(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(commerceService.listActiveEntitlements(openid));
    }

    public record CreateOrderRequest(String sku) { }
    public record PaymentParamsRequest() { }
}

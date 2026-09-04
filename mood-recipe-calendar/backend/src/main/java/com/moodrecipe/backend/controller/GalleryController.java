package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.Product;
import com.moodrecipe.backend.entity.ShopOrder;
import com.moodrecipe.backend.repository.ProductRepository;
import com.moodrecipe.backend.repository.ShopOrderRepository;
import com.moodrecipe.backend.service.GalleryService;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 锅仔形象馆：内容资产（表情包）/ 周边商城 / 签到福利 / 订单
 * 内容资产区为前端静态资源展示与保存，本接口负责商城与签到。
 */
@RestController
@RequestMapping("/api/gallery")
public class GalleryController {

    private final GalleryService galleryService;
    private final ProductRepository productRepository;
    private final ShopOrderRepository shopOrderRepository;

    public GalleryController(GalleryService galleryService,
                             ProductRepository productRepository,
                             ShopOrderRepository shopOrderRepository) {
        this.galleryService = galleryService;
        this.productRepository = productRepository;
        this.shopOrderRepository = shopOrderRepository;
    }

    /** 周边商品列表 */
    @GetMapping("/products")
    public ApiResponse<List<Product>> products() {
        return ApiResponse.ok(productRepository.findByIsActiveTrueOrderBySortOrderAsc());
    }

    /** 今日签到 POST /api/gallery/checkin body: { openid } */
    @PostMapping("/checkin")
    public ApiResponse<Map<String, Object>> checkin(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(galleryService.checkin(openid));
    }

    /** 签到状态 GET /api/gallery/checkin/status?openid= */
    @GetMapping("/checkin/status")
    public ApiResponse<Map<String, Object>> checkinStatus(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(galleryService.checkinStatus(openid));
    }

    /**
     * 创建订单 POST /api/gallery/orders body: { openid, productId, payType }
     * payType: normal 原价购买 / exchange 1元兑换（需连续签到30天）
     */
    @PostMapping("/orders")
    public ApiResponse<ShopOrder> createOrder(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody Map<String, Object> body) {
        Object pid = body.get("productId");
        if (pid == null) return ApiResponse.error("productId 不能为空");
        try {
            ShopOrder order = galleryService.createOrder(openid, Long.valueOf(String.valueOf(pid)), (String) body.get("payType"));
            return ApiResponse.ok(order);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 我的订单 GET /api/gallery/orders?openid= */
    @GetMapping("/orders")
    public ApiResponse<List<ShopOrder>> myOrders(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(shopOrderRepository.findByOpenidOrderByCreatedAtDesc(openid));
    }

}

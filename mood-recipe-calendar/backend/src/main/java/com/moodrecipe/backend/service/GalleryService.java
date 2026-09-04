package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Checkin;
import com.moodrecipe.backend.entity.Product;
import com.moodrecipe.backend.entity.ShopOrder;
import com.moodrecipe.backend.repository.CheckinRepository;
import com.moodrecipe.backend.repository.ProductRepository;
import com.moodrecipe.backend.repository.ShopOrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 锅仔形象馆业务：签到福利 + 周边商城下单
 */
@Service
public class GalleryService {

    /** 1 元兑换所需连续签到天数 */
    public static final int EXCHANGE_DAYS = 30;

    private final CheckinRepository checkinRepository;
    private final ProductRepository productRepository;
    private final ShopOrderRepository shopOrderRepository;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public GalleryService(CheckinRepository checkinRepository,
                          ProductRepository productRepository,
                          ShopOrderRepository shopOrderRepository) {
        this.checkinRepository = checkinRepository;
        this.productRepository = productRepository;
        this.shopOrderRepository = shopOrderRepository;
    }

    /**
     * 今日签到。返回签到状态：
     * { checkedIn, streak, exchangeReady, daysToExchange }
     */
    public Map<String, Object> checkin(String openid) {
        String today = LocalDate.now().format(DATE_FMT);
        if (checkinRepository.findByOpenidAndCheckinDate(openid, today).isEmpty()) {
            checkinRepository.save(Checkin.builder()
                .openid(openid)
                .checkinDate(today)
                .build());
        }
        return checkinStatus(openid);
    }

    /**
     * 查询签到状态
     */
    public Map<String, Object> checkinStatus(String openid) {
        String today = LocalDate.now().format(DATE_FMT);
        List<Checkin> all = checkinRepository.findByOpenidOrderByCheckinDateAsc(openid);
        Set<String> dateSet = all.stream().map(Checkin::getCheckinDate).collect(Collectors.toSet());

        boolean checkedIn = dateSet.contains(today);
        int streak = calcStreak(dateSet);

        Map<String, Object> res = new HashMap<>();
        res.put("checkedIn", checkedIn);
        res.put("streak", streak);
        res.put("exchangeReady", streak >= EXCHANGE_DAYS);
        res.put("daysToExchange", Math.max(0, EXCHANGE_DAYS - streak));
        res.put("totalDays", dateSet.size());
        return res;
    }

    /**
     * 计算连续签到天数（从今天往前连续）
     */
    private int calcStreak(Set<String> dateSet) {
        if (dateSet.isEmpty()) return 0;
        int streak = 0;
        LocalDate day = LocalDate.now();
        while (dateSet.contains(day.format(DATE_FMT))) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    /**
     * 下单。payType: normal 原价 / exchange 1元兑换
     * 返回订单（含实付金额）
     */
    public ShopOrder createOrder(String openid, Long productId, String payType) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("商品不存在"));

        if (product.getIsActive() == null || !product.getIsActive()) {
            throw new RuntimeException("商品已下架");
        }
        if (product.getStock() != null && product.getStock() <= 0) {
            throw new RuntimeException("商品库存不足");
        }

        String type = payType == null ? "normal" : payType;
        BigDecimal amount;
        if ("exchange".equals(type)) {
            Map<String, Object> status = checkinStatus(openid);
            if (!Boolean.TRUE.equals(status.get("exchangeReady"))) {
                throw new RuntimeException("连续签到满 " + EXCHANGE_DAYS + " 天才能 1 元兑换，还差 "
                    + status.get("daysToExchange") + " 天");
            }
            amount = product.getExchangePrice() != null ? product.getExchangePrice() : new BigDecimal("1.00");
        } else {
            type = "normal";
            amount = product.getPrice();
        }

        ShopOrder order = ShopOrder.builder()
            .orderNo(genOrderNo())
            .openid(openid)
            .productId(product.getId())
            .productName(product.getName())
            .productImage(product.getImage())
            .amount(amount)
            .payType(type)
            .status("pending")
            .build();
        return shopOrderRepository.save(order);
    }

    private String genOrderNo() {
        return "GZ" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}

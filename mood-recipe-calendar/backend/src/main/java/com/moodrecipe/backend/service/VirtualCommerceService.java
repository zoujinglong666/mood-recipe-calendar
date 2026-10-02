package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.entity.UserEntitlement;
import com.moodrecipe.backend.entity.VirtualOrder;
import com.moodrecipe.backend.entity.VirtualProduct;
import com.moodrecipe.backend.repository.UserEntitlementRepository;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.VirtualOrderRepository;
import com.moodrecipe.backend.repository.VirtualProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 处理虚拟商品的订单和发货。支付平台的签名校验后才能调用 fulfillPaidOrder，
 * 因此不提供可被客户端直接调用的“支付成功”接口。
 */
@Service
public class VirtualCommerceService {

    private final VirtualProductRepository productRepository;
    private final VirtualOrderRepository orderRepository;
    private final UserEntitlementRepository entitlementRepository;
    private final UserRepository userRepository;
    private final ObjectMapper mapper;

    public VirtualCommerceService(VirtualProductRepository productRepository,
                                  VirtualOrderRepository orderRepository,
                                  UserEntitlementRepository entitlementRepository,
                                  UserRepository userRepository) {
        this(productRepository, orderRepository, entitlementRepository, userRepository, new ObjectMapper());
    }

    @Autowired
    public VirtualCommerceService(
            VirtualProductRepository productRepository,
            VirtualOrderRepository orderRepository,
            UserEntitlementRepository entitlementRepository,
            UserRepository userRepository,
            ObjectMapper mapper
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.entitlementRepository = entitlementRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    public List<VirtualProduct> listProducts() {
        return productRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    /** 单条会员权益：分组 + 图标 + 标题 + 与免费的差异 + 说明。 */
    public record MemberBenefit(String group, String groupTitle, String icon,
                                String title, String value, String detail) {}

    /** 会员权益分组：会员页按组渲染。 */
    public record MemberBenefitGroup(String key, String title, List<MemberBenefit> items) {}

    /**
     * 读取会员 SKU 的权益清单（接口驱动，前端不再硬编码）。
     *
     * 数据来自 virtual_products.benefits（JSON 数组），按 group 归并为分组；
     * 若该字段为空（老库未迁移）则返回空列表，由前端回退内置文案，保证不出现白屏。
     */
    public List<MemberBenefitGroup> listMemberBenefits() {
        VirtualProduct memberProduct = productRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .filter(VirtualProduct::isMemberPass)
                .findFirst()
                .orElse(null);
        if (memberProduct == null || memberProduct.getBenefits() == null || memberProduct.getBenefits().isBlank()) {
            return List.of();
        }
        try {
            List<MemberBenefit> benefits = mapper.readValue(memberProduct.getBenefits(),
                    mapper.getTypeFactory().constructCollectionType(List.class, MemberBenefit.class));
            // 按 group 归并，保持 JSON 中的原始顺序
            Map<String, List<MemberBenefit>> grouped = new LinkedHashMap<>();
            Map<String, String> titles = new LinkedHashMap<>();
            for (MemberBenefit benefit : benefits) {
                String key = benefit.group() == null || benefit.group().isBlank() ? "OTHER" : benefit.group();
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(benefit);
                titles.putIfAbsent(key, benefit.groupTitle());
            }
            List<MemberBenefitGroup> result = new ArrayList<>();
            grouped.forEach((key, items) -> result.add(new MemberBenefitGroup(key, titles.get(key), items)));
            return result;
        } catch (Exception error) {
            // 权益 JSON 损坏时返回空列表，前端回退内置文案，绝不让会员页崩掉
            return List.of();
        }
    }

    public VirtualOrder createOrder(String openid, String sku) {
        if (openid == null || openid.isBlank()) throw new IllegalArgumentException("openid 不能为空");
        VirtualProduct product = productRepository.findById(sku)
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("商品不存在或已下架"));

        VirtualOrder order = new VirtualOrder();
        order.setOrderNo("VP" + UUID.randomUUID().toString().replace("-", "").substring(0, 28).toUpperCase());
        order.setOpenid(openid);
        order.setSku(product.getSku());
        order.setAmountFen(product.getPriceFen());
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public List<UserEntitlement> listActiveEntitlements(String openid) {
        LocalDateTime now = LocalDateTime.now();
        return entitlementRepository.findByOpenidAndStatus(openid, "ACTIVE").stream()
                .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
                .toList();
    }

    public boolean isActiveMember(String openid) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.findByOpenid(openid)
                .filter(user -> Integer.valueOf(1).equals(user.getIsMember()))
                .filter(user -> user.getMemberExpire() != null && user.getMemberExpire().isAfter(now))
                .isPresent();
    }

    public Optional<VirtualOrder> findOrderForUser(String openid, String orderNo) {
        return orderRepository.findByOrderNo(orderNo).filter(order -> openid.equals(order.getOpenid()));
    }

    public List<VirtualOrder> listOrders(String openid) {
        return orderRepository.findByOpenidOrderByCreatedAtDesc(openid);
    }

    /** 预扣一次权益。调用方在模型生成失败时必须调用 restoreEntitlement。 */
    @Transactional
    public Optional<UserEntitlement> consumeEntitlement(String openid, String code) {
        LocalDateTime now = LocalDateTime.now();
        Optional<UserEntitlement> entitlement = entitlementRepository.findByOpenidAndStatusForUpdate(openid, "ACTIVE").stream()
                .filter(item -> code.equals(item.getCode()))
                .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
                .filter(item -> item.getRemainingUses() != null && item.getRemainingUses() > 0)
                .findFirst();
        entitlement.ifPresent(item -> {
            item.setRemainingUses(item.getRemainingUses() - 1);
            entitlementRepository.save(item);
        });
        return entitlement;
    }

    @Transactional
    public void restoreEntitlement(Long entitlementId) {
        entitlementRepository.findById(entitlementId).ifPresent(item -> {
            item.setRemainingUses((item.getRemainingUses() == null ? 0 : item.getRemainingUses()) + 1);
            entitlementRepository.save(item);
        });
    }

    /** 仅供已验签的微信虚拟支付回调适配器调用。可安全重试。 */
    @Transactional
    public VirtualOrder fulfillPaidOrder(String orderNo, String platformTransactionId) {
        VirtualOrder order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if ("DELIVERED".equals(order.getStatus())) return order;
        if (!"PENDING".equals(order.getStatus()) && !"PAID".equals(order.getStatus())) {
            throw new IllegalStateException("订单状态不允许发货");
        }

        VirtualProduct product = productRepository.findById(order.getSku())
                .orElseThrow(() -> new IllegalStateException("订单商品不存在"));
        LocalDateTime now = LocalDateTime.now();
        order.setStatus("PAID");
        order.setPaidAt(now);
        order.setPlatformTransactionId(platformTransactionId);

        // sourceOrderNo 唯一约束保证支付回调重试时不会重复加权益。
        if (entitlementRepository.findBySourceOrderNo(orderNo).isEmpty()) {
            grantEntitlement(order, product, now);
        }
        order.setStatus("DELIVERED");
        order.setDeliveredAt(now);
        return orderRepository.save(order);
    }

    private void grantEntitlement(VirtualOrder order, VirtualProduct product, LocalDateTime now) {
        if (product.isMemberPass()) {
            User user = userRepository.findByOpenid(order.getOpenid())
                    .orElseThrow(() -> new IllegalStateException("用户不存在，无法发放会员"));
            LocalDateTime base = user.getMemberExpire() != null && user.getMemberExpire().isAfter(now)
                    ? user.getMemberExpire() : now;
            user.setMemberExpire(base.plusDays(product.getValidDays()));
            user.setIsMember(1);
            userRepository.save(user);
            return;
        }

        UserEntitlement entitlement = new UserEntitlement();
        entitlement.setOpenid(order.getOpenid());
        entitlement.setCode(product.getEntitlementCode());
        entitlement.setRemainingUses(product.getEntitlementAmount());
        entitlement.setSourceOrderNo(order.getOrderNo());
        entitlement.setExpiresAt(product.getValidDays() > 0 ? now.plusDays(product.getValidDays()) : null);
        entitlement.setCreatedAt(now);
        entitlementRepository.save(entitlement);
    }
}

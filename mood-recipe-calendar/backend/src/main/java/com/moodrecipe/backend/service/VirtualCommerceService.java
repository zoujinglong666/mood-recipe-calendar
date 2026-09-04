package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.entity.UserEntitlement;
import com.moodrecipe.backend.entity.VirtualOrder;
import com.moodrecipe.backend.entity.VirtualProduct;
import com.moodrecipe.backend.repository.UserEntitlementRepository;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.VirtualOrderRepository;
import com.moodrecipe.backend.repository.VirtualProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
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

    public VirtualCommerceService(
            VirtualProductRepository productRepository,
            VirtualOrderRepository orderRepository,
            UserEntitlementRepository entitlementRepository,
            UserRepository userRepository
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.entitlementRepository = entitlementRepository;
        this.userRepository = userRepository;
    }

    public List<VirtualProduct> listProducts() {
        return productRepository.findByActiveTrueOrderBySortOrderAsc();
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
        VirtualOrder order = orderRepository.findByOrderNo(orderNo)
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

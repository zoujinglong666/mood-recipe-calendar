package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.UserEntitlement;
import com.moodrecipe.backend.entity.VirtualOrder;
import com.moodrecipe.backend.entity.VirtualProduct;
import com.moodrecipe.backend.repository.UserEntitlementRepository;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.VirtualOrderRepository;
import com.moodrecipe.backend.repository.VirtualProductRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VirtualCommerceServiceTest {

    @Test
    void serverPriceWinsAndRepeatedDeliveryGrantsOnce() {
        VirtualProductRepository products = mock(VirtualProductRepository.class);
        VirtualOrderRepository orders = mock(VirtualOrderRepository.class);
        UserEntitlementRepository entitlements = mock(UserEntitlementRepository.class);
        VirtualProduct product = new VirtualProduct();
        product.setSku("ALBUM_HD_EXPORT");
        product.setActive(true);
        product.setPriceFen(990);
        product.setEntitlementCode("ALBUM_HD_EXPORT");
        product.setEntitlementAmount(1);
        product.setValidDays(30);
        when(products.findById(product.getSku())).thenReturn(Optional.of(product));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VirtualCommerceService service = new VirtualCommerceService(products, orders, entitlements, mock(UserRepository.class));
        VirtualOrder order = service.createOrder("user-1", product.getSku());
        assertEquals(990, order.getAmountFen());

        when(orders.findByOrderNoForUpdate(order.getOrderNo())).thenReturn(Optional.of(order));
        when(entitlements.findBySourceOrderNo(order.getOrderNo())).thenReturn(Optional.empty());
        service.fulfillPaidOrder(order.getOrderNo(), "tx-1");
        service.fulfillPaidOrder(order.getOrderNo(), "tx-1");

        assertEquals("DELIVERED", order.getStatus());
        verify(entitlements, times(1)).save(any(UserEntitlement.class));
    }
}

package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.FridgeItem;
import com.moodrecipe.backend.repository.FridgeItemRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FridgeInventoryServiceTest {
    private final FridgeItemRepository repository = mock(FridgeItemRepository.class);
    private final FridgeInventoryService service = new FridgeInventoryService(repository);

    @Test
    void todayExpiryIsSoonWithZeroDaysLeft() {
        LocalDate today = today();
        FridgeItem item = item("番茄", today.toString(), today.toString(), "3");
        when(repository.findByOpenidOrderByExpiresOnAscUpdatedAtDesc("user-1")).thenReturn(List.of(item));

        FridgeInventoryService.ItemView view = service.list("user-1").get(0);

        assertEquals("SOON", view.status());
        assertEquals(0, view.daysLeft());
    }

    @Test
    void expiredItemIsMarkedExpired() {
        LocalDate yesterday = today().minusDays(1);
        FridgeItem item = item("生菜", yesterday.toString(), yesterday.toString(), "1");
        when(repository.findByOpenidOrderByExpiresOnAscUpdatedAtDesc("user-1")).thenReturn(List.of(item));

        assertEquals("EXPIRED", service.list("user-1").get(0).status());
    }

    @Test
    void itemExpiringWithinThreeDaysIsSoon() {
        LocalDate expires = today().plusDays(2);
        FridgeItem item = item("鸡蛋", today().minusDays(4).toString(), expires.toString(), "6");
        when(repository.findByOpenidOrderByExpiresOnAscUpdatedAtDesc("user-1")).thenReturn(List.of(item));

        assertEquals("SOON", service.list("user-1").get(0).status());
    }

    @Test
    void missingExpiryDateIsNoDate() {
        FridgeItem item = item("大米", "2026-09-28", null, "2");
        when(repository.findByOpenidOrderByExpiresOnAscUpdatedAtDesc("user-1")).thenReturn(List.of(item));

        assertEquals("NO_DATE", service.list("user-1").get(0).status());
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        FridgeInventoryService.CreateRequest request = new FridgeInventoryService.CreateRequest(
                "鸡蛋", BigDecimal.ZERO, "个", LocalDate.now(), LocalDate.now().plusDays(3), "");

        assertThrows(IllegalArgumentException.class, () -> service.create("user-1", request));
        verifyNoInteractions(repository);
    }

    @Test
    void anotherUsersItemCannotBeUpdated() {
        when(repository.findByIdAndOpenid(9L, "user-1")).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), service.update("user-1", 9L,
                new FridgeInventoryService.UpdateRequest("鸡蛋", BigDecimal.ONE, "个", null, null, "")));
    }

    @Test
    void consumingAllQuantityDeletesItem() {
        FridgeItem item = item("豆腐", today().toString(), today().plusDays(2).toString(), "1");
        when(repository.findByIdAndOpenid(1L, "user-1")).thenReturn(Optional.of(item));

        Optional<FridgeInventoryService.ItemView> result = service.consume("user-1", 1L, BigDecimal.ONE);

        assertEquals(Optional.empty(), result);
        verify(repository).delete(item);
    }

    private FridgeItem item(String name, String purchasedOn, String expiresOn, String quantity) {
        FridgeItem item = new FridgeItem();
        item.setId(1L);
        item.setOpenid("user-1");
        item.setName(name);
        item.setQuantity(new BigDecimal(quantity));
        item.setUnit("份");
        item.setPurchasedOn(LocalDate.parse(purchasedOn));
        item.setExpiresOn(expiresOn == null ? null : LocalDate.parse(expiresOn));
        return item;
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai"));
    }
}

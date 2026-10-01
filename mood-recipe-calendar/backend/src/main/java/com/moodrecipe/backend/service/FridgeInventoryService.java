package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.FridgeItem;
import com.moodrecipe.backend.repository.FridgeItemRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class FridgeInventoryService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final FridgeItemRepository repository;

    public FridgeInventoryService(FridgeItemRepository repository) {
        this.repository = repository;
    }

    public List<ItemView> list(String openid) {
        return repository.findByOpenidOrderByExpiresOnAscUpdatedAtDesc(openid).stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public ItemView create(String openid, CreateRequest request) {
        validate(request.name(), request.quantity(), request.unit(), request.note());
        FridgeItem item = new FridgeItem();
        item.setOpenid(openid);
        apply(item, request.name(), request.quantity(), request.unit(), request.purchasedOn(), request.expiresOn(), request.note());
        return view(repository.save(item));
    }

    @Transactional
    public Optional<ItemView> update(String openid, Long id, UpdateRequest request) {
        Optional<FridgeItem> found = repository.findByIdAndOpenid(id, openid);
        if (found.isEmpty()) return Optional.empty();
        validate(request.name(), request.quantity(), request.unit(), request.note());
        FridgeItem item = found.get();
        apply(item, request.name(), request.quantity(), request.unit(), request.purchasedOn(), request.expiresOn(), request.note());
        return Optional.of(view(repository.save(item)));
    }

    @Transactional
    public Optional<ItemView> consume(String openid, Long id, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("消耗数量必须大于 0");
        Optional<FridgeItem> found = repository.findByIdAndOpenid(id, openid);
        if (found.isEmpty()) return Optional.empty();
        FridgeItem item = found.get();
        if (amount.compareTo(item.getQuantity()) > 0) throw new IllegalArgumentException("消耗数量不能超过库存");
        if (amount.compareTo(item.getQuantity()) == 0) {
            repository.delete(item);
            return Optional.empty();
        }
        item.setQuantity(item.getQuantity().subtract(amount));
        return Optional.of(view(repository.save(item)));
    }

    @Transactional
    public boolean delete(String openid, Long id) {
        Optional<FridgeItem> found = repository.findByIdAndOpenid(id, openid);
        if (found.isEmpty()) return false;
        repository.delete(found.get());
        return true;
    }

    public SummaryView summary(String openid) {
        List<ItemView> items = list(openid);
        List<ItemView> priority = items.stream()
                .filter(item -> "SOON".equals(item.status()) || "EXPIRED".equals(item.status()))
                .sorted(Comparator.comparing(ItemView::daysLeft, Comparator.nullsLast(Integer::compareTo)))
                .limit(3)
                .toList();
        long soon = items.stream().filter(item -> "SOON".equals(item.status())).count();
        long expired = items.stream().filter(item -> "EXPIRED".equals(item.status())).count();
        return new SummaryView(items.size(), (int) soon, (int) expired, priority);
    }

    private ItemView view(FridgeItem item) {
        LocalDate today = LocalDate.now(ZONE);
        Integer daysLeft = item.getExpiresOn() == null ? null : (int) (item.getExpiresOn().toEpochDay() - today.toEpochDay());
        String status = status(daysLeft);
        return new ItemView(item.getId(), item.getName(), item.getQuantity(), item.getUnit(), item.getPurchasedOn(),
                item.getExpiresOn(), item.getNote(), status, daysLeft);
    }

    private String status(Integer daysLeft) {
        if (daysLeft == null) return "NO_DATE";
        if (daysLeft < 0) return "EXPIRED";
        return daysLeft <= 3 ? "SOON" : "FRESH";
    }

    private void apply(FridgeItem item, String name, BigDecimal quantity, String unit,
                       LocalDate purchasedOn, LocalDate expiresOn, String note) {
        item.setName(name.trim());
        item.setQuantity(quantity);
        item.setUnit(unit.trim());
        item.setPurchasedOn(purchasedOn);
        item.setExpiresOn(expiresOn);
        item.setNote(note == null ? "" : note.trim());
    }

    private void validate(String name, BigDecimal quantity, String unit, String note) {
        if (name == null || name.isBlank() || name.trim().length() > 80) throw new IllegalArgumentException("食材名称不能为空且不超过80字");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("数量必须大于 0");
        if (unit == null || unit.isBlank() || unit.trim().length() > 20) throw new IllegalArgumentException("单位不能为空且不超过20字");
        if (note != null && note.length() > 240) throw new IllegalArgumentException("备注不能超过240字");
    }

    public record CreateRequest(String name, BigDecimal quantity, String unit, LocalDate purchasedOn, LocalDate expiresOn, String note) {}
    public record UpdateRequest(String name, BigDecimal quantity, String unit, LocalDate purchasedOn, LocalDate expiresOn, String note) {}
    public record ItemView(Long id, String name, BigDecimal quantity, String unit, LocalDate purchasedOn, LocalDate expiresOn,
                           String note, String status, Integer daysLeft) {}
    public record SummaryView(int total, int soon, int expired, List<ItemView> priorityItems) {}
}

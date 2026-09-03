package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.VirtualProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VirtualProductRepository extends JpaRepository<VirtualProduct, String> {
    List<VirtualProduct> findByActiveTrueOrderBySortOrderAsc();
}

package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 上架商品，按排序权重升序 */
    List<Product> findByIsActiveTrueOrderBySortOrderAsc();
}

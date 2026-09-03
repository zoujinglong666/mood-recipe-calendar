package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.ShopOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {

    /** 某用户全部订单，按创建时间倒序 */
    List<ShopOrder> findByOpenidOrderByCreatedAtDesc(String openid);
}

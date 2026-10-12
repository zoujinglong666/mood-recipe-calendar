package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.ThemeAlbum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThemeAlbumRepository extends JpaRepository<ThemeAlbum, String> {

    List<ThemeAlbum> findByActiveTrueOrderBySortOrderAsc();

    Optional<ThemeAlbum> findBySkuAndActiveTrue(String sku);
}

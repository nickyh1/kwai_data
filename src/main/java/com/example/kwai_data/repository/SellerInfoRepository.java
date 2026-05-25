package com.example.kwai_data.repository;

import com.example.kwai_data.data.SellerInfo_Doc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerInfoRepository extends JpaRepository<SellerInfo_Doc, Long> {
    Optional<SellerInfo_Doc> findByShopId(Long shopId);
}

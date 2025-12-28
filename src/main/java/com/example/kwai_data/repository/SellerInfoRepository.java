package com.example.kwai_data.repository;

import com.example.kwai_data.data.SellerInfo_Doc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SellerInfoRepository extends MongoRepository<SellerInfo_Doc, String> {
    Optional<SellerInfo_Doc> findByShopId(Long shopId);
}


package com.example.kwai_data.repository;

// src/main/java/com/example/kwai_data/repo/OrderRepository.java


import com.example.kwai_data.data.Order_Doc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order_Doc, String> {

    Optional<Order_Doc> findByOrderNo(String orderNo);

    boolean existsByOrderNo(String orderNo);
}


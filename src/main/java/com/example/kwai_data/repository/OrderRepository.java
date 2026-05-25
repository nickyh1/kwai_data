package com.example.kwai_data.repository;

import com.example.kwai_data.data.Order_Doc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order_Doc, Long> {
    Optional<Order_Doc> findByOrderNo(String orderNo);
    boolean existsByOrderNo(String orderNo);
}

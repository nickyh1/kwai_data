package com.example.kwai_data.repository;

import com.example.kwai_data.domain.ShopAuthDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopAuthJpaRepository extends JpaRepository<ShopAuthDoc, String> {
}

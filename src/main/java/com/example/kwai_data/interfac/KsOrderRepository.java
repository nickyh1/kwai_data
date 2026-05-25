package com.example.kwai_data.interfac;

import com.example.kwai_data.dto.order.KsOrderDoc;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KsOrderRepository extends JpaRepository<KsOrderDoc, String> {
}

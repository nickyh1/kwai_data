package com.example.kwai_data.interfac;

import com.example.kwai_data.dto.order.KsOrderDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface KsOrderRepository extends MongoRepository<KsOrderDoc, String> {
}


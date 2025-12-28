package com.example.kwai_data.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final MongoTemplate mongoTemplate;

    @GetMapping("/debug/mongo/collections")
    public Map<String, Object> collections() {
        String dbName = mongoTemplate.getDb().getName();
        var collections = mongoTemplate.getCollectionNames(); // Set<String>

        return Map.of(
                "db", dbName,
                "collections", collections
        );
    }
}

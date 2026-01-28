package com.example.kwai_data.repository;

import com.example.kwai_data.data.ShopAuthDoc;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 店铺认证信息 MongoDB 仓库
 */
@Repository
public interface ShopAuthMongoRepository extends MongoRepository<ShopAuthDoc, String> {
}

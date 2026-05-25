package com.example.kwai_data.repository;

import com.example.kwai_data.data.SyncCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyncCheckpointRepository extends JpaRepository<SyncCheckpoint, Long> {

    Optional<SyncCheckpoint> findByShopKeyAndSyncType(String shopKey, String syncType);
}

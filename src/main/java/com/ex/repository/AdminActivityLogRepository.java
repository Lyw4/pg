package com.ex.repository;

import com.ex.entity.AdminActivityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActivityLogRepository extends JpaRepository<AdminActivityLog, Long> {
    List<AdminActivityLog> findTop100ByOrderByCreatedAtDesc();
}

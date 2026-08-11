package com.ex.service;

import com.ex.entity.AdminActivityLog;
import com.ex.repository.AdminActivityLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminActivityService {
    private final AdminActivityLogRepository repository;

    @Transactional
    public void record(String username, String actionType, String targetType,
            String targetIdentifier, String description, String ipAddress) {
        repository.save(new AdminActivityLog(
                username == null || username.isBlank() ? "system" : username,
                actionType, targetType, targetIdentifier, description, ipAddress));
    }

    @Transactional(readOnly = true)
    public List<AdminActivityLog> findRecent() {
        return repository.findTop100ByOrderByCreatedAtDesc();
    }
}

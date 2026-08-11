package com.ex.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_activity_log", indexes = {
        @Index(name = "idx_admin_activity_created", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActivityLog extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80, nullable = false)
    private String adminUsername;
    @Column(length = 40, nullable = false)
    private String actionType;
    @Column(length = 40, nullable = false)
    private String targetType;
    @Column(length = 120)
    private String targetIdentifier;
    @Column(length = 500, nullable = false)
    private String description;
    @Column(length = 64)
    private String ipAddress;

    public AdminActivityLog(String adminUsername, String actionType,
            String targetType, String targetIdentifier, String description,
            String ipAddress) {
        this.adminUsername = adminUsername;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetIdentifier = targetIdentifier;
        this.description = description;
        this.ipAddress = ipAddress;
    }
}

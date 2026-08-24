package com.ex.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한 번만 실행해야 하는 기준 데이터 보정의 완료 여부를 기록한다. */
@Entity
@Table(name = "data_initialization_marker")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataInitializationMarker {

    @Id
    @Column(name = "marker_key", length = 100)
    private String markerKey;

    @Column(nullable = false)
    private LocalDateTime completedAt;

    public DataInitializationMarker(String markerKey) {
        if (markerKey == null || markerKey.isBlank()) {
            throw new IllegalArgumentException("초기화 표식 키가 필요합니다.");
        }
        this.markerKey = markerKey.trim();
    }

    @PrePersist
    void onCreate() {
        if (completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }
}

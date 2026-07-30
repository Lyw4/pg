package com.ex.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "defect_record",
        uniqueConstraints = @UniqueConstraint(columnNames = "defect_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefectRecord {

    public enum DefectType {
        DAMAGE("파손"), CONTAMINATION("오염"), SPECIFICATION("규격 미달"),
        FUNCTION("기능 불량"), EXPIRED("유통기한"), OTHER("기타");

        private final String label;
        DefectType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum OccurrenceStage {
        RECEIVING("입고 검사"), STORAGE("보관"), PRODUCTION("생산"),
        SHIPPING("출고 검사"), RETURNED("고객 반품");

        private final String label;
        OccurrenceStage(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum DefectStatus {
        QUARANTINED("격리"), INSPECTING("검사 중"), RESOLVED("처리 완료");

        private final String label;
        DefectStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum ResolutionType {
        REWORK("재작업 후 정상 복귀"), SUPPLIER_RETURN("공급업체 반품"),
        DISPOSAL("폐기"), CONCESSION("특채 사용");

        private final String label;
        ResolutionType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long defectId;

    private String defectNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id")
    private ProductLot lot;

    private int quantity;

    @Enumerated(EnumType.STRING)
    private DefectType defectType;

    @Enumerated(EnumType.STRING)
    private OccurrenceStage occurrenceStage;

    @Enumerated(EnumType.STRING)
    private DefectStatus status;

    @Enumerated(EnumType.STRING)
    private ResolutionType resolutionType;

    @Column(length = 1000)
    private String description;
    private String reporter;
    private String processor;
    @Column(length = 1000)
    private String resolutionNote;
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    public DefectRecord(ProductLot lot, int quantity, DefectType defectType,
            OccurrenceStage occurrenceStage, String description, String reporter,
            LocalDateTime occurredAt) {
        this.lot = lot;
        this.quantity = quantity;
        this.defectType = defectType;
        this.occurrenceStage = occurrenceStage;
        this.description = description;
        this.reporter = reporter;
        this.occurredAt = occurredAt;
        this.status = DefectStatus.QUARANTINED;
    }

    public void startInspection() {
        if (status == DefectStatus.RESOLVED) {
            throw new IllegalStateException("이미 처리 완료된 불량 건입니다.");
        }
        status = DefectStatus.INSPECTING;
    }

    public void resolve(ResolutionType resolutionType, String processor, String note) {
        if (status == DefectStatus.RESOLVED) {
            throw new IllegalStateException("이미 처리 완료된 불량 건입니다.");
        }
        this.resolutionType = resolutionType;
        this.processor = processor;
        this.resolutionNote = note;
        this.resolvedAt = LocalDateTime.now();
        this.status = DefectStatus.RESOLVED;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (occurredAt == null) occurredAt = createdAt;
        if (defectNo == null) {
            defectNo = "DF-" + createdAt.toLocalDate().toString().replace("-", "")
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }
    }
}

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
@Table(name = "consultation_request", indexes = {
        @Index(name = "idx_consultation_request_status", columnList = "status"),
        @Index(name = "idx_consultation_request_member", columnList = "member_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultationRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consultation_request_id")
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "requester_name", nullable = false, length = 40)
    private String requesterName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "animal_type", nullable = false, length = 30)
    private String animalType;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false, length = 20)
    private String status;

    public ConsultationRequest(
            Long memberId,
            String requesterName,
            String phone,
            String animalType,
            String message) {
        this.memberId = memberId;
        this.requesterName = requesterName.trim();
        this.phone = phone.trim();
        this.animalType = animalType.trim();
        this.message = message.trim();
        this.status = "PENDING";
    }
}

package com.feedflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 물류센터 - 기준 정보(Master Data).
 *
 * <h3>왜 enum 이 아니라 엔티티인가</h3>
 * 원래 창고는 {@code Warehouse} enum(WH1 · WH2)이었다. 단일 물류센터 안의 건물 2동을
 * 표현하기에는 충분했지만, <b>전국 단위가 되면 센터는 운영 중에 늘고 줄어든다.</b>
 * enum 은 코드를 다시 배포해야 값을 추가할 수 있어 맞지 않는다.
 *
 * <h3>구역(Bin)과의 관계</h3>
 * 구역은 반드시 하나의 센터에 속한다({@code WarehouseBin.center}).
 * 2D 도면은 센터 단위로 한 장씩 그린다. 서로 떨어진 센터의 구역이 한 도면에 섞이면
 * 실제 위치를 오해하게 된다.
 *
 * <h3>정렬</h3>
 * 목록과 선택 상자는 {@link #centerCode} 순으로 정렬한다. 코드 체계에 지역 순서를
 * 담아두면 별도 정렬 컬럼 없이도 원하는 순서가 나온다.
 */
@Entity
@Table(
        name = "centers",
        uniqueConstraints = @UniqueConstraint(name = "ukCenterCode", columnNames = "centerCode")
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Center {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "centerId")
    private Long centerId;

    /**
     * 센터 코드 (업무 식별자, 중복 불가).
     * <p>
     * 화면 정렬 기준이기도 하다.
     */
    @Column(name = "centerCode", nullable = false, unique = true, length = 20)
    private String centerCode;

    /** 센터명 (화면 표기) 예: 제1창고 */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 권역 예: 수도권 · 영남 · 호남 */
    @Column(name = "region", length = 30)
    private String region;

    @Column(name = "address", length = 200)
    private String address;

    /**
     * 보관 정책 요약 (도면 탭의 부제로 쓴다) 예: 상온 · 배합사료
     * <p>
     * 온도 조건 같은 운영 특성을 한 줄로 적어 센터를 고를 때 참고한다.
     */
    @Column(name = "note", length = 100)
    private String note;

    /** 사용 여부 (false = 운영 중지) */
    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /* ------------------------------------------------------------------
     * 도메인 로직
     * ------------------------------------------------------------------ */

    /** 기준 정보 수정 */
    public void updateMasterData(String centerCode,
                                 String name,
                                 String region,
                                 String address,
                                 String note) {
        this.centerCode = centerCode;
        this.name = name;
        this.region = region;
        this.address = address;
        this.note = note;
    }

    /** 운영 여부 변경 */
    public void changeActive(boolean active) {
        this.active = active;
    }

    /**
     * 화면 표기용 이름.
     * <p>
     * 센터명이 비어 있으면 코드로 대체한다. 라벨이 빈칸으로 나오면
     * 어느 센터인지 알 수 없어 선택 상자가 무용해진다.
     */
    public String displayName() {
        return (name == null || name.isBlank()) ? centerCode : name;
    }
}

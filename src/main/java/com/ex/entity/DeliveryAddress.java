package com.ex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "delivery_address")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DeliveryAddress extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_address_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType addressType;

    @Column(name = "recipient_name", nullable = false, length = 40)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "base_address", nullable = false, length = 200)
    private String baseAddress;

    @Column(name = "detail_address", length = 200)
    private String detailAddress;

    @Column(name = "unloading_location", length = 200)
    private String unloadingLocation;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultAddress = false;
}

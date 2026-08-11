package com.ex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "member", indexes = {
        @Index(name = "idx_member_username", columnList = "username", unique = true),
        @Index(name = "idx_member_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(unique = true, length = 20)
    private String username;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "farm_name", length = 100)
    private String farmName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "business_number", length = 20)
    private String businessNumber;

    @Column(name = "regular_delivery_day")
    private Integer regularDeliveryDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MemberRole role = MemberRole.CUSTOMER;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeliveryAddress> addresses = new ArrayList<>();
}

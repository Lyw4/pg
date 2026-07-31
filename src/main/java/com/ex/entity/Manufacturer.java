package com.ex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "manufacturer")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Manufacturer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "manufacturer_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "business_number", length = 20)
    private String businessNumber;

    @Column(length = 20)
    private String phone;
}

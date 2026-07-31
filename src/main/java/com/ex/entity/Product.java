package com.ex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product", indexes = {
        @Index(name = "idx_product_animal_active", columnList = "animal_type, active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "animal_type", nullable = false, length = 30)
    private AnimalType animalType;

    @Column(name = "feed_stage", nullable = false, length = 50)
    private String feedStage;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private int price;

    @Column(name = "original_price")
    private Integer originalPrice;

    @Column(name = "protein_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal proteinPercent;

    @Column(name = "fat_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal fatPercent;

    @Column(name = "fiber_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal fiberPercent;

    @Column(name = "calcium_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal calciumPercent;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 30)
    private String badge;

    @Column(name = "display_tone", nullable = false, length = 30)
    private String displayTone;

    @Column(name = "display_shape", nullable = false, length = 30)
    private String displayShape;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("expirationDate ASC")
    @Builder.Default
    private List<ProductLot> lots = new ArrayList<>();
}

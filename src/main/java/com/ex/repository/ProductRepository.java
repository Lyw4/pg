package com.ex.repository;

import com.ex.entity.AnimalType;
import com.ex.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"manufacturer", "lots"})
    List<Product> findAllByActiveTrueOrderByIdAsc();

    @EntityGraph(attributePaths = {"manufacturer", "lots"})
    List<Product> findAllByActiveTrueAndAnimalTypeOrderByIdAsc(AnimalType animalType);

    @EntityGraph(attributePaths = {"manufacturer", "lots"})
    Optional<Product> findByIdAndActiveTrue(Long id);
}

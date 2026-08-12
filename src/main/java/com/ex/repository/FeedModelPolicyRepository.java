package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.FeedModelPolicy;

public interface FeedModelPolicyRepository extends JpaRepository<FeedModelPolicy, Long> {
    Optional<FeedModelPolicy> findByAnimalType(String animalType);
    List<FeedModelPolicy> findAllByOrderByAnimalTypeAsc();
}

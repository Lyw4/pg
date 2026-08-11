package com.ex.repository;

import com.ex.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUsernameIgnoreCase(String username);
    Optional<Member> findByUsernameIgnoreCaseAndEmailIgnoreCase(String username, String email);
    Optional<Member> findByEmail(String email);
    Optional<Member> findByNameIgnoreCaseAndEmailIgnoreCase(String name, String email);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmail(String email);
}

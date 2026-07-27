package com.feedflow.repository;

import com.feedflow.domain.Role;
import com.feedflow.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * 사원 목록 조회.
     * role 은 STRING 으로 저장되므로 오름차순 정렬 시 ADMIN → STAFF 순서가 된다.
     */
    @Query("""
            select u
            from User u
            where u.role in :roles
            order by u.role asc, u.name asc
            """)
    List<User> findEmployees(Collection<Role> roles);
}

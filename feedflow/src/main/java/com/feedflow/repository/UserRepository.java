package com.feedflow.repository;

import com.feedflow.domain.Role;
import com.feedflow.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * 사원 목록 조회.
     * role 은 STRING 으로 저장되므로 오름차순 정렬 시 ADMIN → STAFF 순서가 된다.
     * <p>
     * 이름 있는 파라미터(:roles)를 사용하므로 반드시 {@code @Param} 을 붙인다.
     * (IDE 컴파일러는 -parameters 옵션이 꺼져 있을 수 있어 파라미터 이름을 신뢰할 수 없다)
     */
    @Query("""
            select u
            from User u
            where u.role in :roles
            order by u.role asc, u.name asc
            """)
    List<User> findEmployees(@Param("roles") Collection<Role> roles);
}

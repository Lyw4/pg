package com.feedflow.admin.service;

import com.feedflow.admin.dto.EmployeeDto;
import com.feedflow.domain.Role;
import com.feedflow.domain.User;
import com.feedflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 사원 계정 및 권한 관리 서비스.
 * 고객(USER) 계정은 이 서비스의 조회/변경 대상에서 제외된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private static final Set<Role> EMPLOYEE_ROLES = Set.of(Role.STAFF, Role.ADMIN);

    private final UserRepository userRepository;

    /** 사원 전체 목록 (STAFF + ADMIN) */
    public List<EmployeeDto> getEmployees(Long loginUserId) {
        return userRepository.findEmployees(EMPLOYEE_ROLES).stream()
                .map(user -> EmployeeDto.of(user, loginUserId))
                .toList();
    }

    /**
     * 사원 권한 변경 (STAFF <-> ADMIN).
     *
     * @param targetUserId 변경 대상 사원 ID
     * @param newRole      변경할 권한 (STAFF 또는 ADMIN)
     * @param loginUserId  요청자(로그인 사용자) ID
     * @return 변경된 사원 이름
     */
    @Transactional
    public String changeRole(Long targetUserId, Role newRole, Long loginUserId) {
        if (newRole == null || !EMPLOYEE_ROLES.contains(newRole)) {
            throw new IllegalArgumentException("사원 권한은 STAFF 또는 ADMIN 만 지정할 수 있습니다.");
        }
        if (loginUserId != null && loginUserId.equals(targetUserId)) {
            throw new IllegalStateException("본인의 권한은 변경할 수 없습니다.");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원입니다. id=" + targetUserId));

        if (!target.isEmployee()) {
            throw new IllegalStateException("사원 계정이 아니므로 권한을 변경할 수 없습니다. (고객 계정)");
        }
        if (target.getRole() == newRole) {
            throw new IllegalStateException("이미 " + newRole.getDescription() + " 권한입니다.");
        }

        target.changeRole(newRole);
        return target.getName();
    }
}

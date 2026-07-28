package com.feedflow.security;

import com.feedflow.domain.Role;
import com.feedflow.domain.User;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

/**
 * 인증 주체(Principal). userId / 이름 / 권한을 함께 보관한다.
 */
@Getter
public class LoginUser extends org.springframework.security.core.userdetails.User
        implements UserDetails {

    private final Long userId;
    private final String displayName;
    private final Role role;

    public LoginUser(User user) {
        super(user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole().authority())));
        this.userId = user.getUserId();
        this.displayName = user.getName();
        this.role = user.getRole();
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /* ------------------------------------------------------------------
     * 컨트롤러 편의 메서드
     *  · 이력에 처리자 스냅샷을 남길 때 매번 null 체크하던 중복을 없앤다.
     * ------------------------------------------------------------------ */

    /** 처리자 ID (미인증이면 null) */
    public static Long idOf(LoginUser loginUser) {
        return loginUser == null ? null : loginUser.getUserId();
    }

    /** 처리자 이름 (미인증이면 null) */
    public static String nameOf(LoginUser loginUser) {
        return loginUser == null ? null : loginUser.getDisplayName();
    }
}

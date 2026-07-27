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
}

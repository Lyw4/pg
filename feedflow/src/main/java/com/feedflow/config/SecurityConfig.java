package com.feedflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 관리자 시스템 보안 설정.
 * - /admin/**, /api/admin/** : ROLE_STAFF, ROLE_ADMIN 만 접근 가능
 * - 책임자 전용 기능은 @PreAuthorize("hasRole('ADMIN')") 로 메서드 단위 차단
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // @PreAuthorize 활성화
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 정적 자원 / 로그인 / 개발용 H2 콘솔
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/login", "/access-denied", "/error", "/h2-console/**").permitAll()
                        // 관리자 시스템: 사원만 접근 (고객 USER 는 차단)
                        .requestMatchers("/admin/**", "/api/admin/**").hasAnyRole("STAFF", "ADMIN")
                        // 그 외 경로(B2C 쇼핑몰 영역)는 다른 팀원이 담당
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/admin/dashboard", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/access-denied")
                )
                // H2 콘솔(개발용)만 CSRF / frame 예외 처리
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * {noop}, {bcrypt} 등 prefix 를 인식하는 위임 인코더.
     * 초기 데이터(data.sql)는 {noop} 평문을 사용하고, 실제 회원가입 시에는 bcrypt 로 저장된다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}

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
 *
 * <h3>접근 정책 (화이트리스트 방식)</h3>
 * <ul>
 *     <li>정적 자원 / 로그인 / 에러 화면 : 전체 공개</li>
 *     <li>{@code /shop/**}, {@code /api/shop/**} : B2C 쇼핑몰 담당 영역으로 임시 공개</li>
 *     <li>{@code /admin/**}, {@code /api/admin/**} : ROLE_STAFF, ROLE_ADMIN 만 접근</li>
 *     <li><b>그 외 모든 경로는 인증 필요</b> (기존 {@code anyRequest().permitAll()} 은
 *         새 경로가 실수로 무인증 공개되는 위험이 있어 제거했다)</li>
 * </ul>
 * 책임자 전용 기능은 추가로 {@code @PreAuthorize("hasRole('ADMIN')")} 로 메서드 단위 차단한다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // @PreAuthorize 활성화
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 1) 정적 자원
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()

                        // 2) 인증 없이 접근해야 하는 화면
                        .requestMatchers("/login", "/access-denied", "/error").permitAll()

                        // 3) 개발용 H2 콘솔 (운영 배포 시 반드시 제거)
                        .requestMatchers("/h2-console/**").permitAll()

                        // 4) B2C 쇼핑몰 영역 (다른 팀원 담당) - 경로를 명시적으로 한정해 개방
                        .requestMatchers("/shop/**", "/api/shop/**").permitAll()

                        // 5) 관리자 화면 : 사원(STAFF/ADMIN)만. 고객(USER)은 차단
                        .requestMatchers("/admin/**").hasAnyRole("STAFF", "ADMIN")

                        // 6) 관리자 API : 화면과 동일한 권한을 요구한다.
                        //    (단순 인증만 허용하면 고객 계정으로 관리자 API 를 호출할 수 있다)
                        .requestMatchers("/api/admin/**").hasAnyRole("STAFF", "ADMIN")

                        // 7) 그 외 모든 경로는 기본 차단 (신규 경로가 실수로 공개되는 것을 방지)
                        .anyRequest().authenticated()
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

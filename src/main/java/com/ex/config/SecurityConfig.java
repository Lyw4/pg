package com.ex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.ex.repository.EmployeeAccountRepository;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RememberMeServices rememberMeServices) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler =
                new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        // /api/** 는 fetch 기반 JSON 호출이라 CSRF 토큰을 쓰지 않습니다.
                        .ignoringRequestMatchers("/api/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/shop/products/**",
                                "/admin/login",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/uploads/product-images/**",
                                "/favicon.ico",
                                "/api/products/**",
                                "/api/orders/**",
                                "/api/members/**",
                                "/error")
                        .permitAll()
                        // 사원 계정 관리는 employee_account.role 을 직접 바꿀 수
                        // 있어 권한 상승 경로가 됩니다. 사원(STAFF) 권한에서
                        // 제외하고 관리자에게만 허용합니다.
                        .requestMatchers("/admin/employees/**")
                        .hasRole("ADMIN")
                        .requestMatchers(
                                "/admin/**",
                                "/inventory/**",
                                "/distribution/**",
                                "/shipments/**",
                                "/products/**",
                                "/api/admin/**",
                                "/api/inventory/**",
                                "/api/distribution/**",
                                "/api/shipments/**")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().permitAll());
        http.formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/admin/login?error")
                .permitAll());
        http.rememberMe(remember -> remember
                .rememberMeServices(rememberMeServices));
        http.logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/?adminLogout=true")
                .invalidateHttpSession(true)
                .deleteCookies(
                        "JSESSIONID",
                        "FEEDFLOW_ADMIN_REMEMBER",
                        "FEEDFLOW_ADMIN_SESSION_REMEMBER"));
        http.httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    RememberMeServices rememberMeServices(
            UserDetailsService userDetailsService,
            @Value("${feedflow.security.remember-me-key}") String key) {
        TokenBasedRememberMeServices services =
                new TokenBasedRememberMeServices(key, userDetailsService);
        // 관리자도 브라우저 종료 후에는 세션 쿠키가 사라져 다시 로그인해야 합니다.
        // 체크박스 등으로 명시적으로 요청하지 않은 Remember-Me 쿠키는 발급하지 않습니다.
        services.setAlwaysRemember(false);
        services.setTokenValiditySeconds(60 * 60 * 24 * 30);
        // 이전에 발급된 장기 쿠키와도 분리해 기존 로그인 상태가 재사용되지 않게 합니다.
        services.setCookieName("FEEDFLOW_ADMIN_SESSION_REMEMBER");
        return services;
    }

    @Bean
    UserDetailsService userDetailsService(
            EmployeeAccountRepository employeeRepository
    ) {
        return username -> employeeRepository
                .findByUsernameIgnoreCase(username.trim())
                .filter(employee -> employee.isActive())
                .map(employee -> User.withUsername(employee.getUsername())
                        .password(employee.getPassword())
                        .roles(employee.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "등록되지 않은 사원 계정입니다."));
    }
}

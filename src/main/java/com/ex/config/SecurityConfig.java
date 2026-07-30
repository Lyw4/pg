package com.ex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		CsrfTokenRequestAttributeHandler csrfRequestHandler =
				new CsrfTokenRequestAttributeHandler();

		// Thymeleaf가 큰 화면을 렌더링한 뒤 세션을 만들지 않도록
		// CSRF 토큰을 요청 초기에 미리 로딩한다.
		csrfRequestHandler.setCsrfRequestAttributeName(null);

		http
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.csrf(csrf -> csrf
					.csrfTokenRequestHandler(csrfRequestHandler)
					.ignoringRequestMatchers("/h2-console/**"))
			.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable());
		return http.build();
	}
}

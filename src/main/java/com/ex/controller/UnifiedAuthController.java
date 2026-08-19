package com.ex.controller;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ex.dto.LoginRequest;
import com.ex.dto.MemberResponse;
import com.ex.dto.UnifiedLoginRequest;
import com.ex.dto.UnifiedLoginResponse;
import com.ex.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 판매 페이지의 한 로그인 폼에서 농장 회원과 관리자를 구분해 인증합니다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UnifiedAuthController {

    private final AuthenticationManager authenticationManager;
    private final MemberService memberService;

    @PostMapping("/login")
    public UnifiedLoginResponse login(
            @Valid @RequestBody UnifiedLoginRequest request,
            HttpServletRequest servletRequest) {
        String identifier = request.identifier().trim();

        Authentication operatorAuthentication = authenticateOperator(
                identifier,
                request.password());
        if (operatorAuthentication != null) {
            servletRequest.getSession(true).removeAttribute("memberId");
            establishAdminSession(
                    operatorAuthentication,
                    servletRequest);
            String accountType = operatorAuthentication.getAuthorities()
                    .stream()
                    .anyMatch(authority -> "ROLE_ADMIN"
                            .equals(authority.getAuthority()))
                    ? "ADMIN"
                    : "STAFF";
            return UnifiedLoginResponse.operator(accountType);
        }

        try {
            MemberResponse member = memberService.login(
                    new LoginRequest(identifier, request.password()));
            HttpSession session = servletRequest.getSession(true);
            SecurityContextHolder.clearContext();
            session.removeAttribute(HttpSessionSecurityContextRepository
                    .SPRING_SECURITY_CONTEXT_KEY);
            servletRequest.changeSessionId();
            session.setAttribute("memberId", member.id());
            return UnifiedLoginResponse.customer(member);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "아이디(이메일) 또는 비밀번호가 일치하지 않습니다.");
        }
    }

    private Authentication authenticateOperator(
            String identifier,
            String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            identifier,
                            password));
            return authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN"
                            .equals(authority.getAuthority())
                            || "ROLE_STAFF"
                            .equals(authority.getAuthority()))
                    ? authentication
                    : null;
        } catch (AuthenticationException exception) {
            return null;
        }
    }

    private void establishAdminSession(
            Authentication authentication,
            HttpServletRequest request) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        request.changeSessionId();
        request.getSession().setAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY,
                context);
    }
}

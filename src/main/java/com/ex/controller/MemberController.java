package com.ex.controller;

import com.ex.dto.FindUsernameRequest;
import com.ex.dto.FindUsernameResponse;
import com.ex.dto.LoginRequest;
import com.ex.dto.MemberResponse;
import com.ex.dto.MemberUpdateRequest;
import com.ex.dto.PasswordResetCodeRequest;
import com.ex.dto.PasswordResetCodeResponse;
import com.ex.dto.ResetPasswordRequest;
import com.ex.dto.SignupRequest;
import com.ex.service.MemberService;
import com.ex.service.PasswordRecoveryService;
import com.ex.repository.EmployeeAccountRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final EmployeeAccountRepository employeeAccountRepository;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse signup(@Valid @RequestBody SignupRequest request) {
        return memberService.signup(request);
    }

    @GetMapping("/check-username")
    public Map<String, Boolean> checkUsername(
            @RequestParam("username") String username) {
        boolean employeeUsername = employeeAccountRepository
                .findByUsernameIgnoreCase(username.trim())
                .isPresent();
        return Map.of(
                "available",
                !employeeUsername && memberService.isUsernameAvailable(username));
    }

    @GetMapping("/check-email")
    public Map<String, Boolean> checkEmail(
            @RequestParam("email") String email) {
        return Map.of("available", memberService.isEmailAvailable(email));
    }

    @PostMapping("/login")
    public MemberResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        MemberResponse member = memberService.login(request);
        HttpSession session = servletRequest.getSession(true);
        SecurityContextHolder.clearContext();
        session.removeAttribute(HttpSessionSecurityContextRepository
                .SPRING_SECURITY_CONTEXT_KEY);
        servletRequest.changeSessionId();
        session.setAttribute("memberId", member.id());
        return member;
    }

    @PostMapping("/find-username")
    public FindUsernameResponse findUsername(@Valid @RequestBody FindUsernameRequest request) {
        return memberService.findUsername(request);
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordRecoveryService.resetPassword(request);
        return Map.of("message", "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.");
    }

    @PostMapping("/password-reset/code")
    public PasswordResetCodeResponse issuePasswordResetCode(
            @Valid @RequestBody PasswordResetCodeRequest request) {
        return passwordRecoveryService.issueCode(request);
    }

    @GetMapping("/me")
    public MemberResponse me(HttpSession session) {
        return memberService.findById(memberId(session));
    }

    @PutMapping("/me")
    public MemberResponse updateMe(
            @Valid @RequestBody MemberUpdateRequest request,
            HttpSession session) {
        return memberService.update(memberId(session), request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        session.invalidate();
    }

    private Long memberId(HttpSession session) {
        return SessionMemberSupport.requireMemberId(session);
    }
}

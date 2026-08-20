package com.ex.service;

import com.ex.config.PasswordResetProperties;
import com.ex.dto.PasswordResetCodeRequest;
import com.ex.dto.PasswordResetCodeResponse;
import com.ex.dto.ResetPasswordRequest;
import com.ex.entity.Member;
import com.ex.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordRecoveryServiceTest {

    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;
    private PasswordResetProperties properties;
    private PasswordResetMailSender passwordResetMailSender;
    private PasswordRecoveryService service;
    private Member member;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        passwordResetMailSender = mock(PasswordResetMailSender.class);
        when(passwordResetMailSender.isConfigured()).thenReturn(true);
        properties = new PasswordResetProperties();
        properties.setExposeCode(true);
        properties.setCodeTtl(Duration.ofMinutes(5));
        properties.setResendCooldown(Duration.ofSeconds(30));
        properties.setMaxAttempts(5);
        member = Member.builder()
                .username("happyfarm")
                .email("farm@example.com")
                .password("old-hash")
                .name("김농부")
                .phone("010-1234-5678")
                .active(true)
                .build();
        member.setId(42L);
        when(memberRepository.findByUsernameIgnoreCaseAndEmailIgnoreCase(
                "happyfarm", "farm@example.com"))
                .thenReturn(Optional.of(member));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
        service = new PasswordRecoveryService(
                memberRepository,
                passwordEncoder,
                properties,
                passwordResetMailSender);
    }

    @Test
    void sixDigitCodeExpiresAndCannotBeIssuedAgainWithinThirtySeconds() {
        PasswordResetCodeResponse issued = service.issueCode(
                new PasswordResetCodeRequest(
                        "happyfarm", "farm@example.com", "010-1234-5678"));

        assertTrue(issued.debugCode().matches("\\d{6}"));
        assertEquals(300, issued.expiresInSeconds());
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.issueCode(new PasswordResetCodeRequest(
                        "happyfarm", "farm@example.com", "010-1234-5678")));
        assertTrue(exception.getMessage().contains("30초"));
    }

    @Test
    void productionModeSendsCodeToRegisteredEmail() {
        properties.setExposeCode(false);

        PasswordResetCodeResponse issued = service.issueCode(
                new PasswordResetCodeRequest(
                        "happyfarm", "farm@example.com", "010-1234-5678"));

        assertNull(issued.debugCode());
        verify(passwordResetMailSender).sendCode(
                eq("farm@example.com"),
                argThat(code -> code != null && code.matches("\\d{6}")),
                any());
    }

    @Test
    void localModeReturnsCodeWhenNoDeliveryChannelIsConfigured() {
        when(passwordResetMailSender.isConfigured()).thenReturn(false);

        PasswordResetCodeResponse issued = service.issueCode(
                new PasswordResetCodeRequest(
                        "happyfarm", "farm@example.com", "010-1234-5678"));

        assertTrue(issued.debugCode().matches("\\d{6}"));
        verify(passwordResetMailSender, never()).sendCode(anyString(), anyString(), any());
    }

    @Test
    void fifthWrongCodeDiscardsChallenge() {
        PasswordResetCodeResponse issued = service.issueCode(new PasswordResetCodeRequest(
                "happyfarm", "farm@example.com", "010-1234-5678"));
        String wrongCode = issued.debugCode().equals("000000") ? "000001" : "000000";
        ResetPasswordRequest wrong = new ResetPasswordRequest(
                "happyfarm", "farm@example.com", null,
                "010-1234-5678", wrongCode, "NewFarm!456");

        for (int attempt = 1; attempt < 5; attempt++) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.resetPassword(wrong));
            assertTrue(exception.getMessage().contains("남은 시도"));
        }
        IllegalArgumentException discarded = assertThrows(
                IllegalArgumentException.class,
                () -> service.resetPassword(wrong));
        assertTrue(discarded.getMessage().contains("폐기"));
        IllegalArgumentException expired = assertThrows(
                IllegalArgumentException.class,
                () -> service.resetPassword(wrong));
        assertTrue(expired.getMessage().contains("없거나 만료"));
    }
}

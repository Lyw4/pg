package com.ex.config;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 비밀번호 재설정 인증번호 정책입니다. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "feedflow.password-reset")
public class PasswordResetProperties {

    private Duration codeTtl = Duration.ofMinutes(5);
    private Duration resendCooldown = Duration.ofSeconds(30);
    private int maxAttempts = 5;
    /** 외부 문자/메일 연동 전 로컬 테스트에서만 인증번호를 응답에 포함합니다. */
    private boolean exposeCode = false;
}

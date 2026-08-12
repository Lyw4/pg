package com.ex.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 비밀번호 재설정 6자리 인증번호를 회원의 등록 이메일로 발송합니다. */
@Service
public class PasswordResetMailSender {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm")
                    .withZone(ZoneId.of("Asia/Seoul"));

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;

    public PasswordResetMailSender(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${feedflow.password-reset.mail.from:}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
    }

    public void sendCode(String recipient, String code, Instant expiresAt) {
        if (!StringUtils.hasText(recipient)) {
            throw new IllegalArgumentException("회원 이메일이 없어 인증번호를 발송할 수 없습니다.");
        }
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException(
                    "비밀번호 찾기 이메일 발송 주소가 설정되지 않았습니다. 관리자에게 문의해 주세요.");
        }

        sendNotice(
                recipient,
                "[FeedFlow] 비밀번호 재설정 인증번호",
                """
                FeedFlow 비밀번호 재설정 요청이 접수되었습니다.

                인증번호: %s
                유효 시간: %s까지

                본인이 요청하지 않았다면 이 이메일을 무시해 주세요.
                """.formatted(code, EXPIRY_FORMAT.format(expiresAt)));
    }

    public boolean isConfigured() {
        return StringUtils.hasText(from)
                && mailSenderProvider.getIfAvailable() != null;
    }

    public void sendNotice(String recipient, String subject, String text) {
        if (!StringUtils.hasText(recipient)) {
            throw new IllegalArgumentException("회원 이메일이 없어 안내를 발송할 수 없습니다.");
        }
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException("이메일 발송 주소가 설정되지 않았습니다.");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException(
                    "비밀번호 찾기 이메일(SMTP) 설정이 완료되지 않았습니다. 관리자에게 문의해 주세요.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient.trim());
        message.setFrom(from.trim());
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "인증 이메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.",
                    exception);
        }
    }
}

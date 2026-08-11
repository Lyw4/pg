package com.ex.service;

import com.ex.config.PasswordResetProperties;
import com.ex.dto.PasswordResetCodeRequest;
import com.ex.dto.PasswordResetCodeResponse;
import com.ex.dto.ResetPasswordRequest;
import com.ex.entity.Member;
import com.ex.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 6자리 비밀번호 재설정 인증번호를 관리합니다. */
@Slf4j
@Service
public class PasswordRecoveryService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;
    private final PasswordResetMailSender passwordResetMailSender;
    private final PasswordResetSmsSender passwordResetSmsSender;
    private final Map<Long, Challenge> challenges = new ConcurrentHashMap<>();

    @Autowired
    public PasswordRecoveryService(MemberRepository memberRepository,
            PasswordEncoder passwordEncoder, PasswordResetProperties properties,
            PasswordResetMailSender passwordResetMailSender,
            PasswordResetSmsSender passwordResetSmsSender) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.passwordResetMailSender = passwordResetMailSender;
        this.passwordResetSmsSender = passwordResetSmsSender;
    }

    public PasswordRecoveryService(MemberRepository memberRepository,
            PasswordEncoder passwordEncoder, PasswordResetProperties properties,
            PasswordResetMailSender passwordResetMailSender) {
        this(memberRepository, passwordEncoder, properties, passwordResetMailSender,
                new PasswordResetSmsSender(false, "", "", "", ""));
    }

    @Transactional(readOnly = true)
    public synchronized PasswordResetCodeResponse issueCode(PasswordResetCodeRequest request) {
        Member member = findMember(request.username(), request.email(), request.phone());
        long memberId = member.getId();
        Instant now = Instant.now();
        Challenge existing = challenges.get(memberId);
        if (existing != null && now.isBefore(existing.resendAvailableAt())) {
            long seconds = secondsUntil(now, existing.resendAvailableAt());
            throw new IllegalStateException(
                    "인증번호는 30초 후에 다시 발급할 수 있습니다. " + seconds + "초 후 재시도해주세요.");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        Instant expiresAt = now.plus(properties.getCodeTtl());
        Instant resendAvailableAt = now.plus(properties.getResendCooldown());
        if (!properties.isExposeCode()) {
            passwordResetMailSender.sendCode(member.getEmail(), code, expiresAt);
            passwordResetSmsSender.sendCode(member.getPhone(), code);
        }
        challenges.put(memberId, new Challenge(
                salt, hash(salt, code), expiresAt, resendAvailableAt, 0));

        // 실제 서비스에서는 이 지점에 SMS/메일 발송 어댑터를 연결합니다.
        log.info("비밀번호 재설정 인증번호가 발급되었습니다. memberId={}, expiresAt={}",
                memberId, expiresAt);
        return new PasswordResetCodeResponse(
                "인증번호를 발급했습니다. 5분 안에 입력해주세요.",
                Math.max(0, properties.getCodeTtl().toSeconds()),
                Math.max(0, properties.getResendCooldown().toSeconds()),
                properties.isExposeCode() ? code : null);
    }

    @Transactional
    public synchronized void resetPassword(ResetPasswordRequest request) {
        Member member = findMember(request.username(), request.email(), request.phone());
        Challenge challenge = challenges.get(member.getId());
        Instant now = Instant.now();
        if (challenge == null || !now.isBefore(challenge.expiresAt())) {
            challenges.remove(member.getId());
            throw new IllegalArgumentException("인증번호가 없거나 만료되었습니다. 새 인증번호를 발급해주세요.");
        }

        if (!MessageDigest.isEqual(challenge.codeHash(), hash(challenge.salt(), request.code()))) {
            int failures = challenge.failures() + 1;
            if (failures >= Math.max(1, properties.getMaxAttempts())) {
                challenges.remove(member.getId());
                throw new IllegalArgumentException(
                        "인증번호를 5회 잘못 입력하여 폐기했습니다. 새 인증번호를 발급해주세요.");
            }
            challenges.put(member.getId(), challenge.withFailures(failures));
            throw new IllegalArgumentException(
                    "인증번호가 일치하지 않습니다. 남은 시도 횟수: "
                            + (Math.max(1, properties.getMaxAttempts()) - failures) + "회");
        }

        if (passwordEncoder.matches(request.newPassword(), member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호와 다른 새 비밀번호를 입력해주세요.");
        }
        member.setPassword(passwordEncoder.encode(request.newPassword()));
        challenges.remove(member.getId());
    }

    private Member findMember(String username, String email, String phone) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String normalizedPhone = normalizePhone(phone);
        return memberRepository.findByUsernameIgnoreCaseAndEmailIgnoreCase(
                        normalizedUsername, normalizedEmail)
                .filter(Member::isActive)
                .filter(member -> normalizePhone(member.getPhone()).equals(normalizedPhone))
                .orElseThrow(() -> new IllegalArgumentException(
                        "입력한 회원정보와 일치하는 회원을 찾을 수 없습니다."));
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }

    private byte[] hash(byte[] salt, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return digest.digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("인증번호 보안 해시를 초기화하지 못했습니다.", exception);
        }
    }

    private long secondsUntil(Instant from, Instant to) {
        return Math.max(1, Duration.between(from, to).toSeconds());
    }

    private record Challenge(
            byte[] salt,
            byte[] codeHash,
            Instant expiresAt,
            Instant resendAvailableAt,
            int failures) {
        private Challenge withFailures(int nextFailures) {
            return new Challenge(
                    Arrays.copyOf(salt, salt.length),
                    Arrays.copyOf(codeHash, codeHash.length),
                    expiresAt,
                    resendAvailableAt,
                    nextFailures);
        }
    }
}

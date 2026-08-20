package com.ex.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 세션에 저장된 로그인 회원 식별자를 읽는 공통 로직입니다.
 *
 * <p>컨트롤러마다 같은 코드를 복사해 두면서 동작이 갈라져 있었습니다. 일부는
 * {@code (Long)} 무검사 캐스팅이라 세션에 {@code Integer}가 들어가면
 * {@link ClassCastException}으로 500이 났고, 로그인 필요 상황의 응답 코드도
 * 401과 400이 뒤섞여 있었습니다. 판단 기준을 이 클래스로 모아 통일합니다.
 */
public final class SessionMemberSupport {

    public static final String MEMBER_ID_ATTRIBUTE = "memberId";

    private SessionMemberSupport() {
    }

    /**
     * 로그인 상태가 아니면 {@code null}을 돌려줍니다. 비로그인 사용자도 열 수
     * 있는 화면·API에서 사용합니다.
     *
     * <p>세션 속성은 구현에 따라 {@code Integer}, {@code Long}, 문자열로
     * 저장될 수 있어 {@link Number}와 문자열을 모두 허용합니다.
     */
    public static Long memberIdOrNull(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(MEMBER_ID_ATTRIBUTE);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 로그인이 반드시 필요한 API에서 사용합니다. 비로그인 상태면 401로
     * 응답해 클라이언트가 인증 실패와 입력값 오류(400)를 구분할 수 있게 합니다.
     */
    public static Long requireMemberId(HttpSession session) {
        Long memberId = memberIdOrNull(session);
        if (memberId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return memberId;
    }
}

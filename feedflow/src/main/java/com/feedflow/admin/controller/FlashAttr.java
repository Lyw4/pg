package com.feedflow.admin.controller;

/**
 * 리다이렉트 후 화면에 한 번만 보여줄 메시지의 키 모음.
 * <p>
 * 컨트롤러 14곳에서 {@code "successMessage"} 같은 문자열을 직접 적고 있었다.
 * 이런 키는 <b>템플릿과 이름이 한 글자만 달라도 메시지가 조용히 사라진다.</b>
 * (컴파일 오류도, 런타임 예외도 나지 않아 발견이 늦다)
 * 한 곳에 모아 오타를 컴파일 단계에서 잡는다.
 *
 * <p>여기 상수를 바꾸면 {@code fragments/layout.html} 의 알림 영역과
 * 각 화면의 {@code th:if} 조건도 함께 바꿔야 한다.</p>
 */
public final class FlashAttr {

    /** 성공 알림 (초록) */
    public static final String SUCCESS = "successMessage";

    /** 실패 · 업무 규칙 위반 알림 (빨강) */
    public static final String ERROR = "errorMessage";

    /** 변경 없음 등 단순 안내 (파랑) */
    public static final String INFO = "infoMessage";

    private FlashAttr() {
    }
}

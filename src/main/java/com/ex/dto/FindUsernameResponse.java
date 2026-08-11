package com.ex.dto;

/** 조원 모듈의 응답 이름을 유지하되 username에는 로그인 이메일이 담긴다. */
public record FindUsernameResponse(String username, String message) {
}

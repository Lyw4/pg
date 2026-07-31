package com.ex.controller;

import com.ex.dto.LoginRequest;
import com.ex.dto.MemberResponse;
import com.ex.dto.SignupRequest;
import com.ex.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse signup(@Valid @RequestBody SignupRequest request) {
        return memberService.signup(request);
    }

    @PostMapping("/login")
    public MemberResponse login(@Valid @RequestBody LoginRequest request) {
        return memberService.login(request);
    }
}

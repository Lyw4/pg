package com.ex.controller;

import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ex.entity.ConsultationRequest;
import com.ex.repository.ConsultationRequestRepository;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationRequestRepository consultationRequestRepository;

    public record CreateConsultationRequest(
            @NotBlank @Size(max = 40) String requesterName,
            @NotBlank
            @Pattern(
                    regexp = "^[0-9-]{10,13}$",
                    message = "연락처는 숫자와 하이픈을 포함한 10~13자로 입력해 주세요.")
            String phone,
            @NotBlank @Size(max = 30) String animalType,
            @NotBlank @Size(max = 1000) String message) {
    }

    public record ConsultationResponse(
            Long requestId,
            LocalDateTime requestedAt,
            String message) {
    }

    @PostMapping
    public ConsultationResponse create(
            @Valid @RequestBody CreateConsultationRequest request,
            HttpSession session) {
        ConsultationRequest saved = consultationRequestRepository.save(
                new ConsultationRequest(
                        SessionMemberSupport.memberIdOrNull(session),
                        request.requesterName(),
                        request.phone(),
                        request.animalType(),
                        request.message()));
        return new ConsultationResponse(
                saved.getId(),
                saved.getCreatedAt(),
                "상담 요청이 정상적으로 접수되었습니다.");
    }
}

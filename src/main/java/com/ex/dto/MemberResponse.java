package com.ex.dto;

import com.ex.entity.Member;

import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        String email,
        String name,
        String farmName,
        String phone,
        String businessNumber,
        Integer regularDeliveryDay,
        LocalDateTime createdAt
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getFarmName(),
                member.getPhone(),
                member.getBusinessNumber(),
                member.getRegularDeliveryDay(),
                member.getCreatedAt()
        );
    }
}

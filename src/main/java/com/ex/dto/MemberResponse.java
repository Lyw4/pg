package com.ex.dto;

import com.ex.entity.FarmCustomer;
import com.ex.entity.Member;

import java.time.LocalDateTime;
import java.util.List;

public record MemberResponse(
        Long id,
        String username,
        String email,
        String name,
        String farmName,
        String phone,
        String businessNumber,
        Integer regularDeliveryDay,
        List<AddressResponse> addresses,
        FarmAssignmentResponse farmAssignment,
        FarmModelResponse farmModel,
        LocalDateTime createdAt
) {
    public static MemberResponse from(Member member) {
        return from(member, null);
    }

    public static MemberResponse from(
            Member member,
            FarmCustomer farmCustomer) {
        return from(member, farmCustomer, null);
    }

    public static MemberResponse from(
            Member member,
            FarmCustomer farmCustomer,
            FarmModelResponse farmModel) {
        return new MemberResponse(
                member.getId(),
                member.getUsername(),
                member.getEmail(),
                member.getName(),
                member.getFarmName(),
                member.getPhone(),
                member.getBusinessNumber(),
                member.getRegularDeliveryDay(),
                member.getAddresses().stream()
                        .map(address -> new AddressResponse(
                                address.getAddressType().name(),
                                address.getRecipientName(),
                                address.getPhone(),
                                address.getPostalCode(),
                                address.getBaseAddress(),
                                address.getDetailAddress(),
                                address.getUnloadingLocation(),
                                address.isDefaultAddress()))
                        .toList(),
                farmCustomer == null
                        ? null
                        : new FarmAssignmentResponse(
                                farmCustomer.getFarmCustomerId(),
                                farmCustomer.getFarmCode(),
                                farmCustomer.getAssignedWarehouse().getCode(),
                                farmCustomer.getAssignedWarehouse().getName(),
                                farmCustomer.getDistanceKm(),
                                farmCustomer.getNotes()),
                farmModel,
                member.getCreatedAt()
        );
    }

    public record FarmAssignmentResponse(
            Long farmCustomerId,
            String farmCode,
            String warehouseCode,
            String warehouseName,
            double distanceKm,
            String assignmentBasis
    ) {
    }

    public record AddressResponse(
            String addressType,
            String recipientName,
            String phone,
            String postalCode,
            String baseAddress,
            String detailAddress,
            String unloadingLocation,
            boolean defaultAddress
    ) {
    }
}

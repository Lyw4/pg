package com.ex.service;

import com.ex.dto.LoginRequest;
import com.ex.dto.MemberResponse;
import com.ex.dto.SignupRequest;
import com.ex.entity.DeliveryAddress;
import com.ex.entity.Member;
import com.ex.entity.MemberRole;
import com.ex.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        Member member = Member.builder()
                .email(request.email().trim().toLowerCase())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .farmName(request.farmName())
                .phone(request.phone())
                .businessNumber(request.businessNumber())
                .regularDeliveryDay(request.regularDeliveryDay())
                .role(MemberRole.CUSTOMER)
                .active(true)
                .build();

        addAddress(member, request.homeAddress());
        addAddress(member, request.farmAddress());

        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional(readOnly = true)
    public MemberResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email().trim().toLowerCase())
                .filter(Member::isActive)
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }
        return MemberResponse.from(member);
    }

    private void addAddress(Member member, SignupRequest.AddressRequest request) {
        DeliveryAddress address = DeliveryAddress.builder()
                .member(member)
                .addressType(request.addressType())
                .recipientName(request.recipientName())
                .phone(request.phone())
                .postalCode(request.postalCode())
                .baseAddress(request.baseAddress())
                .detailAddress(request.detailAddress())
                .unloadingLocation(request.unloadingLocation())
                .defaultAddress(request.defaultAddress())
                .build();
        member.getAddresses().add(address);
    }
}

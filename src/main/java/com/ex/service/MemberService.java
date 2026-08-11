package com.ex.service;

import com.ex.dto.LoginRequest;
import com.ex.dto.FindUsernameRequest;
import com.ex.dto.FindUsernameResponse;
import com.ex.dto.MemberResponse;
import com.ex.dto.MemberUpdateRequest;
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
    private final FarmCustomerRegistrationService farmRegistrationService;

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String username = request.username() == null || request.username().isBlank()
                ? createAvailableUsername(email)
                : normalizeUsername(request.username());
        if (!username.matches("^[a-z][a-z0-9_]{4,19}$")) {
            throw new IllegalArgumentException(
                    "아이디는 영문으로 시작하는 5~20자의 영문, 숫자, 밑줄만 사용할 수 있습니다.");
        }
        if (memberRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        Member member = Member.builder()
                .username(username)
                .email(email)
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

        Member savedMember = memberRepository.save(member);
        return MemberResponse.from(
                savedMember,
                farmRegistrationService.register(savedMember, request));
    }

    @Transactional(readOnly = true)
    public MemberResponse login(LoginRequest request) {
        String identifier = request.identifier().trim();
        Member member = (identifier.contains("@")
                ? memberRepository.findByEmail(normalizeEmail(identifier))
                : memberRepository.findByUsernameIgnoreCase(normalizeUsername(identifier)))
                .filter(Member::isActive)
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }
        return MemberResponse.from(
                member,
                farmRegistrationService.findByMemberId(member.getId())
                        .orElse(null));
    }

    @Transactional(readOnly = true)
    public FindUsernameResponse findUsername(FindUsernameRequest request) {
        Member member = memberRepository
                .findByNameIgnoreCaseAndEmailIgnoreCase(
                        request.name().trim(),
                        normalizeEmail(request.email()))
                .filter(Member::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "입력한 정보와 일치하는 회원을 찾을 수 없습니다."));
        return new FindUsernameResponse(
                member.getUsername(),
                "가입한 아이디를 찾았습니다.");
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        String normalized = normalizeUsername(username);
        if (!normalized.matches("^[a-z][a-z0-9_]{4,19}$")) {
            throw new IllegalArgumentException(
                    "아이디는 영문으로 시작하는 5~20자의 영문, 숫자, 밑줄만 사용할 수 있습니다.");
        }
        return !memberRepository.existsByUsernameIgnoreCase(normalized);
    }

    @Transactional(readOnly = true)
    public MemberResponse findById(Long memberId) {
        return MemberResponse.from(
                requireActiveMember(memberId),
                farmRegistrationService.findByMemberId(memberId).orElse(null));
    }

    @Transactional
    public MemberResponse update(Long memberId, MemberUpdateRequest request) {
        Member member = requireActiveMember(memberId);
        member.setName(request.name().trim());
        member.setFarmName(request.farmName().trim());
        member.setPhone(request.phone().trim());
        member.setBusinessNumber(blankToNull(request.businessNumber()));
        member.setRegularDeliveryDay(request.regularDeliveryDay());

        updateAddress(member, com.ex.entity.AddressType.HOME,
                request.homeAddress(), request.homeDetailAddress(), "");
        updateAddress(member, com.ex.entity.AddressType.FARM,
                request.farmAddress(), "", request.unloadingLocation());
        return MemberResponse.from(
                member,
                farmRegistrationService.findByMemberId(memberId).orElse(null));
    }

    private Member requireActiveMember(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return memberRepository.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }

    private void updateAddress(
            Member member,
            com.ex.entity.AddressType addressType,
            String baseAddress,
            String detailAddress,
            String unloadingLocation) {
        DeliveryAddress address = member.getAddresses().stream()
                .filter(item -> item.getAddressType() == addressType)
                .findFirst()
                .orElseGet(() -> {
                    DeliveryAddress created = DeliveryAddress.builder()
                            .member(member)
                            .addressType(addressType)
                            .recipientName(member.getName())
                            .phone(member.getPhone())
                            .baseAddress(baseAddress)
                            .defaultAddress(addressType == com.ex.entity.AddressType.HOME)
                            .build();
                    member.getAddresses().add(created);
                    return created;
                });
        address.setRecipientName(member.getName());
        address.setPhone(member.getPhone());
        address.setBaseAddress(baseAddress.trim());
        address.setDetailAddress(blankToNull(detailAddress));
        address.setUnloadingLocation(blankToNull(unloadingLocation));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String createAvailableUsername(String email) {
        String localPart = email.contains("@")
                ? email.substring(0, email.indexOf('@'))
                : "member";
        String base = localPart.toLowerCase()
                .replaceAll("[^a-z0-9_]", "_");
        if (base.isBlank() || !Character.isLetter(base.charAt(0))) {
            base = "farm_" + base;
        }
        while (base.length() < 5) {
            base += "_";
        }
        base = base.substring(0, Math.min(base.length(), 16));
        String candidate = base;
        int suffix = 1;
        while (memberRepository.existsByUsernameIgnoreCase(candidate)) {
            String number = String.valueOf(suffix++);
            candidate = base.substring(0, Math.min(base.length(), 20 - number.length())) + number;
        }
        return candidate;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

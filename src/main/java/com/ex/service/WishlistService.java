package com.ex.service;

import com.ex.entity.Member;
import com.ex.entity.Product;
import com.ex.entity.WishlistItem;
import com.ex.repository.MemberRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.WishlistItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistItemRepository wishlistRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Long> findProductIds(Long memberId) {
        requireMemberId(memberId);
        return wishlistRepository.findProductIdsByMemberId(memberId);
    }

    @Transactional
    public void add(Long memberId, Long productId) {
        requireMemberId(memberId);
        if (wishlistRepository.existsByMember_IdAndProduct_ProductId(memberId, productId)) {
            return;
        }
        Member member = memberRepository.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
        Product product = productRepository.findByProductIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("판매 중인 상품을 찾을 수 없습니다."));
        wishlistRepository.save(WishlistItem.builder()
                .member(member)
                .product(product)
                .build());
    }

    @Transactional
    public void remove(Long memberId, Long productId) {
        requireMemberId(memberId);
        wishlistRepository.deleteByMember_IdAndProduct_ProductId(memberId, productId);
    }

    private void requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
    }
}

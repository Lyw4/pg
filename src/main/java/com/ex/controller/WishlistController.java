package com.ex.controller;

import com.ex.service.WishlistService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public List<Long> findProductIds(HttpSession session) {
        return wishlistService.findProductIds(memberId(session));
    }

    @PostMapping("/{productId}")
    @ResponseStatus(HttpStatus.CREATED)
    public void add(@PathVariable("productId") Long productId, HttpSession session) {
        wishlistService.add(memberId(session), productId);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable("productId") Long productId, HttpSession session) {
        wishlistService.remove(memberId(session), productId);
    }

    private Long memberId(HttpSession session) {
        return SessionMemberSupport.requireMemberId(session);
    }
}

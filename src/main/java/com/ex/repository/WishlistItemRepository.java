package com.ex.repository;

import com.ex.entity.WishlistItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    @Query("""
            select item.product.productId
              from WishlistItem item
             where item.member.id = :memberId
             order by item.id desc
            """)
    List<Long> findProductIdsByMemberId(@Param("memberId") Long memberId);

    boolean existsByMember_IdAndProduct_ProductId(Long memberId, Long productId);

    long deleteByMember_IdAndProduct_ProductId(Long memberId, Long productId);
}

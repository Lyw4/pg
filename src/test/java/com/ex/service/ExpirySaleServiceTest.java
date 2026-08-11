package com.ex.service;

import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExpirySaleServiceTest {

    @Autowired
    private ExpirySaleService expirySaleService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductLotRepository lotRepository;

    @Test
    void d10LotIsAutomaticallyDiscountedByThirtyPercent() {
        Product product = productRepository.findAllByActiveTrueOrderByIdAsc()
                .getFirst();
        ProductLot lot = lotRepository
                .findByProductProductIdOrderByExpirationDateAsc(
                        product.getProductId())
                .getFirst();
        lot.updateDetails(
                lot.getLotNo(),
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusDays(10),
                8);

        var offer = expirySaleService.offerFor(product).orElseThrow();

        assertEquals(30, offer.discountRate());
        assertEquals(8, offer.saleStock());
        assertEquals(10, offer.daysRemaining());
        assertEquals(
                product.getPrice().multiply(new java.math.BigDecimal("0.70"))
                        .intValue(),
                offer.salePrice());
    }

    @Test
    void d3LotIsExcludedFromStorefrontSale() {
        Product product = productRepository.findAllByActiveTrueOrderByIdAsc()
                .getFirst();
        ProductLot lot = lotRepository
                .findByProductProductIdOrderByExpirationDateAsc(
                        product.getProductId())
                .getFirst();
        lot.updateDetails(
                lot.getLotNo(),
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusDays(3),
                8);

        assertTrue(expirySaleService.offerFor(product).isEmpty());
    }
}

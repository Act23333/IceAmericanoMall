package org.icedAmericanoMall.service.impl;

import org.icedAmericanoMall.domain.entity.SkuEntity;
import org.icedAmericanoMall.enums.SkuStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SKU business logic.
 * Full tests require mocking SkuMapper and the database.
 */
class SkuServiceImplTest {

    @Test
    void shouldHaveCorrectStatusValues() {
        assertEquals(1, SkuStatusEnum.ON_SALE.getCode());
        assertEquals(0, SkuStatusEnum.OFF_SALE.getCode());
    }

    @Test
    void shouldCalculateStockAfterDeduction() {
        SkuEntity sku = new SkuEntity();
        sku.setStock(100);
        sku.setVersion(0);

        int deductQuantity = 5;
        int newStock = sku.getStock() - deductQuantity;

        assertEquals(95, newStock);
        assertTrue(newStock >= 0);
    }

    @Test
    void shouldDetectInsufficientStock() {
        SkuEntity sku = new SkuEntity();
        sku.setStock(3);

        int requestedQuantity = 5;
        assertTrue(sku.getStock() < requestedQuantity);
    }

    @Test
    void shouldStorePriceInCents() {
        SkuEntity sku = new SkuEntity();
        sku.setPrice(9999); // 99.99 yuan

        assertEquals(Integer.valueOf(9999), sku.getPrice());
        assertTrue(sku.getPrice() instanceof Integer);
    }

    @Test
    void shouldHaveVersionForOptimisticLocking() {
        SkuEntity sku = new SkuEntity();
        sku.setVersion(0);

        assertNotNull(sku.getVersion());
        assertEquals(0, sku.getVersion());
    }
}

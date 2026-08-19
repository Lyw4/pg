package com.ex.service;

import java.util.Locale;

import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.BinPurpose;
import com.ex.entity.WarehouseBin;

/** 모든 입고·복원·이관 작업이 공유하는 축종별 보관 구역 규칙입니다. */
public final class WmsZonePolicy {

    private WmsZonePolicy() {
    }

    public static String zoneFor(String animalType) {
        return switch (animalType == null ? "" : animalType.trim()) {
            case "소" -> "CT";
            case "돼지" -> "PG";
            case "조류", "조류(닭/오리)" -> "PL";
            default -> "COLD";
        };
    }

    public static boolean matches(WarehouseBin bin, Product product) {
        if (bin == null || product == null) return false;
        // 입고 대기·출고 대기 같은 공용 작업 구역은 모든 축종이 잠시 거칠 수 있다.
        // 장기 적재되는 보관 구역에서만 축종 전용 구역을 강제한다.
        if (bin.getPurpose() != BinPurpose.STORAGE) return true;
        String actual = bin.getZone() == null
                ? ""
                : bin.getZone().trim().toUpperCase(Locale.ROOT);
        return actual.equals(zoneFor(product.getAnimalType()));
    }

    public static boolean matches(WarehouseBin bin, ProductLot lot) {
        return lot != null && matches(bin, lot.getProduct());
    }

    public static void requireMatch(WarehouseBin bin, Product product) {
        if (!matches(bin, product)) {
            throw new IllegalArgumentException(
                    product.getAnimalType() + " 사료는 "
                            + zoneFor(product.getAnimalType())
                            + " 전용 구역에만 적재할 수 있습니다.");
        }
    }
}

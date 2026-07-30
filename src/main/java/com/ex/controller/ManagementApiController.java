package com.ex.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ex.service.InventoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ManagementApiController {

	private final InventoryService inventoryService;

	@GetMapping("/inventory/summary")
	public List<Map<String, Object>> summary() {
		return inventoryService.products().stream().map(product -> Map.<String, Object>of(
				"productId", product.getProductId(),
				"name", product.getName(),
				"totalStock", product.getTotalStock(),
				"safetyStock", product.getSafetyStock(),
				"lowStock", product.isLowStock())).toList();
	}
}

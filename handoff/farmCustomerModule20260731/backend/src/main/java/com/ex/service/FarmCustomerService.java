package com.ex.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.FarmCustomer;
import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.Warehouse;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.WarehouseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmCustomerService {

    public record WarehouseFarmSummary(
            Warehouse warehouse,
            long customerCount,
            long activeCustomerCount,
            int livestockCount,
            int monthlyFeedQuantity) {
    }

    private final FarmCustomerRepository farmCustomerRepository;
    private final WarehouseRepository warehouseRepository;

    public List<FarmCustomer> customers() {
        return farmCustomerRepository
                .findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc();
    }

    public long activeCount() {
        return customers().stream()
                .filter(customer ->
                        customer.getStatus() == CustomerStatus.ACTIVE)
                .count();
    }

    public int totalMonthlyFeedQuantity() {
        return customers().stream()
                .filter(customer ->
                        customer.getStatus() == CustomerStatus.ACTIVE)
                .mapToInt(FarmCustomer::getMonthlyFeedQuantity)
                .sum();
    }

    @Transactional
    public void changeStatus(
            Long farmCustomerId,
            CustomerStatus status) {
        FarmCustomer customer = farmCustomerRepository
                .findById(farmCustomerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "농장 고객사를 찾을 수 없습니다."));
        customer.changeStatus(status);
    }

    public Map<String, WarehouseFarmSummary> warehouseSummaries() {
        List<FarmCustomer> customers = customers();
        Map<String, WarehouseFarmSummary> summaries =
                new LinkedHashMap<>();

        warehouseRepository
                .findAllByActiveTrueOrderByDisplayOrderAsc()
                .forEach(warehouse -> {
                    List<FarmCustomer> assigned = customers.stream()
                            .filter(customer ->
                                    customer.getAssignedWarehouse()
                                            .getWarehouseId()
                                            .equals(warehouse.getWarehouseId()))
                            .toList();
                    summaries.put(
                            warehouse.getCode(),
                            new WarehouseFarmSummary(
                                    warehouse,
                                    assigned.size(),
                                    assigned.stream()
                                            .filter(customer ->
                                                    customer.getStatus()
                                                        == CustomerStatus.ACTIVE)
                                            .count(),
                                    assigned.stream()
                                            .mapToInt(
                                                    FarmCustomer::getLivestockCount)
                                            .sum(),
                                    assigned.stream()
                                            .filter(customer ->
                                                    customer.getStatus()
                                                        == CustomerStatus.ACTIVE)
                                            .mapToInt(
                                                    FarmCustomer::getMonthlyFeedQuantity)
                                            .sum()));
                });
        return summaries;
    }
}

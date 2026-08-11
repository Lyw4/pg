package com.ex.controller;

import com.ex.entity.AdminActivityLog;
import com.ex.service.AdminActivityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminActivityController {
    private final AdminActivityService adminActivityService;

    @GetMapping("/activities")
    public List<AdminActivityLog> recent() {
        return adminActivityService.findRecent();
    }
}

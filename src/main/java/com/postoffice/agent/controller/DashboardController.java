package com.postoffice.agent.controller;

import com.postoffice.agent.dto.dashboard.DashboardResponse;
import com.postoffice.agent.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse getDashboardSummary() {
        return dashboardService.getDashboardSummary();
    }
}
package com.finstack.dashboard;

import com.finstack.dashboard.dto.DashboardAnalyticsResponse;
import com.finstack.dashboard.dto.DashboardSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary() {
        return service.summary();
    }

    @GetMapping("/analytics")
    public DashboardAnalyticsResponse analytics() {
        return service.analytics();
    }
}

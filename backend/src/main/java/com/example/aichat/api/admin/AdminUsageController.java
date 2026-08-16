package com.example.aichat.api.admin;

import com.example.aichat.api.admin.AdminDtos.UsageResponse;
import com.example.aichat.application.usage.UsageDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Usage dashboard: requests, tokens, cost, latency and error rate per provider and day.
 */
@RestController
@RequestMapping("/api/admin/usage")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsageController {

    private final UsageDashboardService dashboard;

    public AdminUsageController(UsageDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    public UsageResponse usage(@RequestParam(name = "days", defaultValue = "7") int days,
                               @RequestParam(name = "tz", required = false) String timeZone) {
        return UsageResponse.of(dashboard.dashboard(days, timeZone));
    }
}

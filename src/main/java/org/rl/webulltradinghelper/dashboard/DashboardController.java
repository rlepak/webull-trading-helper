package org.rl.webulltradinghelper.dashboard;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }
    @GetMapping("/api/dashboard")
    public ResponseEntity<DashboardService.View> snapshot() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dashboard.snapshot());
    }
}

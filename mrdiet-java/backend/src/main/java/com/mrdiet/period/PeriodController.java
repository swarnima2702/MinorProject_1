package com.mrdiet.period;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Was period_tracker.php. */
@RestController
@RequestMapping("/api/period")
public class PeriodController {

    private final PeriodService period;

    public PeriodController(PeriodService period) {
        this.period = period;
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@AuthenticationPrincipal AuthUser me,
                                          @RequestParam(value = "month", required = false) String month) {
        return period.logs(me, month);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@AuthenticationPrincipal AuthUser me) {
        return period.summary(me);
    }

    @PostMapping("/logs")
    public Map<String, Object> save(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return period.saveLog(me, body);
    }

    @DeleteMapping("/logs")
    public Map<String, Object> delete(@AuthenticationPrincipal AuthUser me, @RequestParam("log_date") String logDate) {
        return period.deleteLog(me, logDate);
    }

    @PutMapping("/config")
    public Map<String, Object> config(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return period.updateConfig(me, body);
    }
}

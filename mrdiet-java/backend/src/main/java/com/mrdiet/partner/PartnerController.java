package com.mrdiet.partner;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.common.J;
import com.mrdiet.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Was partner.php?action=stats|payout_request. */
@RestController
@RequestMapping("/api/partner")
public class PartnerController {

    private final PartnerService partner;

    public PartnerController(PartnerService partner) {
        this.partner = partner;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@AuthenticationPrincipal AuthUser me) {
        return partner.stats(me);
    }

    @PostMapping("/payouts")
    public Map<String, Object> payout(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return partner.requestPayout(me, J.money(body, "amount"));
    }
}

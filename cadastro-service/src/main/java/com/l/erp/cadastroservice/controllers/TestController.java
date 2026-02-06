package com.l.erp.cadastroservice.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/cadastro/ping")
    public String ping(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Is-Owner", required = false) String isOwner
    ) {
        return "OK - tenantId=" + tenantId + ", isOwner=" + isOwner;
    }
}
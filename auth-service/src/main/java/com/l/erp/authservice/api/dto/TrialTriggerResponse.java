package com.l.erp.authservice.api.dto;

import java.util.List;

public record TrialTriggerResponse(int processed, List<String> tenants) {}
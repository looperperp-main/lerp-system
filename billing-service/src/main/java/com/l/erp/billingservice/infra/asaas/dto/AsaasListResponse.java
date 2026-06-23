package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Envelope paginado padrão das listagens do Asaas ({@code { data: [...], hasMore, totalCount }}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasListResponse<T>(
        List<T> data,
        Boolean hasMore,
        Integer totalCount
) {
}
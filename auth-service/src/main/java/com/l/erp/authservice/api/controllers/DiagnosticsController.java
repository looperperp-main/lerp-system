package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.infra.config.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Painel de saúde dos serviços (ferramenta de diagnóstico admin, #2).
 * Enumera os serviços registrados no Eureka e pinga o /actuator/health de uma instância de cada.
 */
@RestController
@RequestMapping("/auth/diagnostics")
public class DiagnosticsController {

    private final Logger log = LoggerFactory.getLogger(DiagnosticsController.class);

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient = RestClient.create();

    public DiagnosticsController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    public record ServiceHealthDTO(String name, String status, int instances) {}

    @GetMapping("/health")
    @Secured(Roles.APP_OWNER)
    public ResponseEntity<List<ServiceHealthDTO>> health() {
        List<ServiceHealthDTO> result = discoveryClient.getServices().stream()
                .map(this::probe)
                .sorted(Comparator.comparing(ServiceHealthDTO::name))
                .toList();
        return ResponseEntity.ok(result);
    }

    private ServiceHealthDTO probe(String serviceId) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        String status = "DOWN";
        if (!instances.isEmpty()) {
            try {
                // ponytail: pinga só a 1ª instância; se rodar N réplicas, iterar todas.
                Map<?, ?> body = restClient.get()
                        .uri(instances.get(0).getUri() + "/actuator/health")
                        .retrieve()
                        .body(Map.class);
                status = body != null && body.get("status") != null ? body.get("status").toString() : "UNKNOWN";
            } catch (Exception e) {
                log.debug("Health check falhou para {}: {}", serviceId, e.getMessage());
                status = "DOWN";
            }
        }
        return new ServiceHealthDTO(serviceId, status, instances.size());
    }

    // ---- #3 Toggle de log level em runtime (proxy pro /actuator/loggers de cada serviço) ----

    /** Loggers atuais de um serviço: {levels:[...], loggers:{nome:{configuredLevel,effectiveLevel}}}. */
    @GetMapping("/loggers")
    @Secured(Roles.APP_OWNER)
    public ResponseEntity<Map<?, ?>> getLoggers(@RequestParam String service) {
        Map<?, ?> body = restClient.get()
                .uri(baseUri(service) + "/actuator/loggers")
                .retrieve()
                .body(Map.class);
        return ResponseEntity.ok(body);
    }

    /** Sobe/baixa o nível de um pacote em runtime, sem redeploy. level=DEBUG|INFO|... ou vazio p/ resetar. */
    @PostMapping("/loggers")
    @Secured(Roles.APP_OWNER)
    public ResponseEntity<Void> setLevel(@RequestParam String service,
                                         @RequestParam String logger,
                                         @RequestParam(required = false) String level) {
        log.info("Log level em runtime: service={} logger={} level={}", service, logger, level);
        restClient.post()
                .uri(baseUri(service) + "/actuator/loggers/" + logger)
                .header("Content-Type", "application/json")
                .body(Map.of("configuredLevel", level == null ? "" : level))
                .retrieve()
                .toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    /** Resolve a URI base da 1ª instância de um serviço no Eureka (404 se não registrado). */
    private String baseUri(String serviceId) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        if (instances.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não registrado: " + serviceId);
        }
        // ponytail: 1ª instância; réplicas só teriam o nível trocado numa delas.
        return instances.get(0).getUri().toString();
    }
}

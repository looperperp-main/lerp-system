package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.services.TrialScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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

    // #5 Runner de jobs: os schedulers de trial do auth, disparáveis manualmente.
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Map<String, JobInfo> lastRuns = new ConcurrentHashMap<>();

    public DiagnosticsController(DiscoveryClient discoveryClient, TrialScheduler trialScheduler) {
        this.discoveryClient = discoveryClient;
        jobs.put("trial-d10", new Job("Trial D+10 (relatório de engajamento)", trialScheduler::processarD10));
        jobs.put("trial-d15", new Job("Trial D+15 (expiração/suspensão)", trialScheduler::processarD15));
    }

    public record ServiceHealthDTO(String name, String status, int instances, List<String> down) {}

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
        if (instances.isEmpty()) {
            return new ServiceHealthDTO(serviceId, "DOWN", 0, List.of());
        }
        try {
            // ponytail: pinga só a 1ª instância; se rodar N réplicas, iterar todas.
            // exchange (não retrieve): health DOWN devolve 503 — precisamos ler o corpo mesmo assim
            // pra saber QUAL componente caiu (retrieve estouraria e perderíamos o motivo).
            Map<?, ?> body = restClient.get()
                    .uri(instances.get(0).getUri() + "/actuator/health")
                    .exchange((req, res) -> res.bodyTo(Map.class));
            String status = body != null && body.get("status") != null ? body.get("status").toString() : "UNKNOWN";
            return new ServiceHealthDTO(serviceId, status, instances.size(), downComponents(body));
        } catch (Exception e) {
            log.debug("Health check falhou para {}: {}", serviceId, e.getMessage());
            return new ServiceHealthDTO(serviceId, "DOWN", instances.size(), List.of());
        }
    }

    /** Componentes com status != UP no corpo do /actuator/health (exige show-details=always). */
    private List<String> downComponents(Map<?, ?> body) {
        Object components = body == null ? null : body.get("components");
        if (!(components instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<String> down = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> comp) {
                Object st = comp.get("status");
                if (st != null && !"UP".equals(st.toString())) {
                    down.add(entry.getKey().toString());
                }
            }
        }
        return down;
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

    // ---- #5 Runner de jobs agendados (schedulers de trial do auth) ----

    public record JobInfo(String key, String label, String lastRun, String lastStatus, Long lastDurationMs) {}

    private record Job(String label, Runnable action) {}

    @GetMapping("/jobs")
    @Secured(Roles.APP_OWNER)
    public ResponseEntity<List<JobInfo>> listJobs() {
        List<JobInfo> result = jobs.entrySet().stream()
                .map(e -> lastRuns.getOrDefault(e.getKey(),
                        new JobInfo(e.getKey(), e.getValue().label(), null, null, null)))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/jobs/{key}/run")
    @Secured(Roles.APP_OWNER)
    public ResponseEntity<JobInfo> runJob(@PathVariable String key) {
        Job job = jobs.get(key);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job desconhecido: " + key);
        }
        log.info("Disparo manual do job {} (admin)", key);
        lastRuns.put(key, new JobInfo(key, job.label(), Instant.now().toString(), "EXECUTANDO", null));
        // ponytail: assíncrono — o scheduler varre tenants e publica em Kafka; não segura o thread HTTP.
        CompletableFuture.runAsync(() -> {
            Instant start = Instant.now();
            try {
                job.action().run();
                lastRuns.put(key, new JobInfo(key, job.label(), start.toString(), "OK",
                        Duration.between(start, Instant.now()).toMillis()));
            } catch (Exception e) {
                log.error("Job manual {} falhou", key, e);
                lastRuns.put(key, new JobInfo(key, job.label(), start.toString(), "ERRO: " + e.getMessage(),
                        Duration.between(start, Instant.now()).toMillis()));
            }
        });
        return ResponseEntity.accepted().body(lastRuns.get(key));
    }
}

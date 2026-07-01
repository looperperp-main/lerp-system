package com.l.erp.billingservice.integration;

import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.infra.redis.DistributedLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedLockServiceIT extends AbstractIntegrationTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @MockitoBean
    AsaasGateway asaasGateway;

    @Autowired
    DistributedLockService lockService;

    @Test
    void acquire_deveTravarUmaVezEBloquearOutraInstancia() {
        String key = "syax:billing:lock:test:" + UUID.randomUUID();

        assertThat(lockService.acquire(key, "instancia-A", 30)).isTrue();
        // Outra instância (owner diferente) não consegue enquanto o lock está ativo.
        assertThat(lockService.acquire(key, "instancia-B", 30)).isFalse();
    }

    @Test
    void release_donoLiberaEPermiteNovaAquisicao() {
        String key = "syax:billing:lock:test:" + UUID.randomUUID();

        assertThat(lockService.acquire(key, "instancia-A", 30)).isTrue();
        assertThat(lockService.release(key, "instancia-A")).isTrue();
        // Liberado → outra instância consegue travar.
        assertThat(lockService.acquire(key, "instancia-B", 30)).isTrue();
    }

    @Test
    void release_naoDonoNaoLibera() {
        String key = "syax:billing:lock:test:" + UUID.randomUUID();

        assertThat(lockService.acquire(key, "instancia-A", 30)).isTrue();
        // Quem não é dono não libera o lock alheio.
        assertThat(lockService.release(key, "instancia-B")).isFalse();
        assertThat(lockService.acquire(key, "instancia-B", 30)).isFalse();
    }

    @Test
    void acquire_expiraAposTtlELiberaAutomaticamente() throws InterruptedException {
        String key = "syax:billing:lock:test:" + UUID.randomUUID();

        assertThat(lockService.acquire(key, "instancia-A", 1)).isTrue();
        Thread.sleep(1500); // TTL de 1s expira → Redis remove a chave sozinho
        assertThat(lockService.acquire(key, "instancia-B", 30)).isTrue();
    }
}

# Kafka — Tópicos do Syax (verificado no código, 2026-09-03)

Levantamento direto dos `@KafkaListener` e `kafkaTemplate.send(...)` nos serviços
(`auth`, `partner`, `billing`, `cadastro`, `operacoes`). Acompanha o diagrama `onboarding-payment-state-machine.svg`.

> **Partições:** não há `NewTopic`/`@Bean NewTopic` nem `KAFKA_CREATE_TOPICS` no `compose.yaml` —
> os tópicos são **auto-criados com o default do broker** (provável `num.partitions=1`). O `compose.yaml`
> só define replication factor = 1. A **chave** de cada mensagem é o que garante ordem por entidade
> (mesma chave → mesma partição). Para escalar consumo, definir partições explicitamente.
>
> **DLT:** todos os consumers usam `DeadLetterPublishingRecoverer` (nos `KafkaConsumerConfig` de auth,
> partner e billing) → em falha, a mensagem vai para `<topic>.DLT`.

## Tabela de tópicos

| Tópico | Key | Produtor(es) | Consumidor(es) | groupId |
|---|---|---|---|---|
| `billing.subscription.activated` | tenantId | billing · `KafkaBillingProducerService` | auth · `SubscriptionActivatedConsumer` · partner · `BillingSubscriptionActivatedConsumer` | auth-service-group / partner-service-group |
| `billing.subscription.suspended` | tenantId | billing · `KafkaBillingProducerService` | auth · `SubscriptionLifecycleConsumer` | auth-service-group |
| `billing.subscription.cancelled` | tenantId | billing · `KafkaBillingProducerService` | auth · `SubscriptionLifecycleConsumer` | auth-service-group |
| `partner.commission.calculated` | partnerId | partner · `KafkaPartnerProducerService` | billing · `PartnerCommissionCalculatedConsumer` | billing-service-group |
| `partner.extrato.request` | partnerId | partner · `BillingClient` (ReplyingKafka) | billing · `ExtratoRequestConsumer` (`@SendTo`) | billing-extrato-group |
| `partner.extrato.reply` | — | billing (reply via `@SendTo`) | partner · container de replies (`ReplyingKafkaTemplate`) | — |
| `partner.invite.requested` | partnerId | partner · `KafkaPartnerProducerService` | auth · `InviteRequestedConsumer` | auth-service-group |
| `partner.invite.processed` | partnerReferralId | auth · `InviteRequestedConsumer` | partner · `InviteTenantCreatedConsumer` | partner-service-group |
| `partner.approved` | partnerId | partner · `KafkaPartnerProducerService` | auth · `PartnerApprovedConsumer` | auth-service-group |
| `partner.tenant.activated` | referralId | auth · `AuthService` | partner · `TenantActivatedConsumer` | partner-service-group |
| `partner.email.notification` | email / cnpj | partner · `KafkaPartnerProducerService` · auth · `SyaxQueueService`/`TrialScheduler` | auth · `EmailConsumerService` | auth-service-group |
| `user-welcome-email-topic` | email | auth · `EmailNotificationService` | auth · `EmailConsumerService` | auth-service-group |
| `trial.login` | tenantId | auth · `AuthService` | partner · `LoginAuditConsumer` | partner-service-group |
| `trial.feature.used` | (n/d) | cadastro · `EngagementController` | auth · `TrialEngagementConsumer` · partner · `FeatureAccessConsumer` | auth-service-group / partner-service-group |
| `venda.pedido.confirmado` | pedidoId | operacoes · `PedidoEventProducer` | — (nenhum consumidor ainda) | — |
| `venda.pedido.faturado` | pedidoId | operacoes · `PedidoEventProducer` | — (nenhum consumidor ainda) | — |
| `venda.pedido.cancelado` | pedidoId | operacoes · `PedidoEventProducer` | — (nenhum consumidor ainda) | — |
| `audit.events` | actorId (billing.sendAuditEvent usa targetId; operacoes usa pedidoId) | partner/billing · `AuditProducerService`, billing · `KafkaBillingProducerService`, operacoes · `PedidoEventProducer` | auth · `AuditConsumer` | auth-service-group |
| `<topic>.DLT` | herda | `DeadLetterPublishingRecoverer` (auth/partner/billing) | — (inspeção manual) | — |

## Fluxos principais

**Onboarding (Fluxo A — com parceiro):**
`partner.invite.requested` → auth cria tenant CONVIDADO → `partner.invite.processed` → partner registra referral →
(cliente ativa: `POST /auth/ativar`) → `partner.tenant.activated` → partner marca ativação/trial.

**Conversão / pagamento:**
webhook `PAYMENT_RECEIVED` (billing) → `billing.subscription.activated` → auth ATIVO + partner;
billing tb dispara cálculo de comissão: `partner.commission.calculated` → billing grava `commission` PENDENTE.

**Dunning / cancelamento (Fase 7):**
DunningJob (billing) → `billing.subscription.suspended` / `billing.subscription.cancelled` → auth `SubscriptionLifecycleConsumer`
seta `tenant.status` (fecha o gap da Fase 5).

**Extrato de comissões (request-reply):**
partner `BillingClient` → `partner.extrato.request` → billing `ExtratoRequestConsumer` responde em `partner.extrato.reply`.

> ⚠️ Não verificado em runtime: contagem real de partições por tópico (depende do broker) e a string exata
> da key do cache Redis. Tudo o mais nesta tabela foi conferido no código-fonte.

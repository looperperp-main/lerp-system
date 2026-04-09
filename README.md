# Auth Service

Microsserviço responsável pelo gerenciamento de identidade, autenticação, autorização e controle de acesso baseado em pagamentos/assinaturas do ERP.

## 📋 Funcionalidades Principais

*   **Autenticação e Autorização:** Login de usuários padrão e usuários vinculados a *Tenants* (inquilinos), emissão e validação de JWT (JSON Web Tokens).
*   **Gerenciamento de Refresh Tokens:** Mecanismo seguro para renovação de sessões sem a necessidade de reautenticação manual.
*   **Controle de Acesso Baseado em Pagamento:** Integração com Stripe para liberação de acesso e provisionamento de recursos após a confirmação do pagamento.
*   **Gestão de Inquilinos (Tenants):** Criação e gerenciamento de contas isoladas para diferentes clientes do ERP.
*   **Gestão de Usuários, Roles e Permissões:** Controle granular do que cada usuário pode acessar dentro de cada Tenant.
*   **Auditoria:** Registro de eventos de segurança (login, logout, tentativas falhas, criação de usuários) via publicação de mensagens no Apache Kafka.
*   **Proteção contra Força Bruta:** Bloqueio temporário de contas após múltiplas tentativas de login malsucedidas.

## 🛠️ Tecnologias Utilizadas

*   **Java 25**
*   **Spring Boot 3.4.x / Spring Security**
*   **PostgreSQL** (Armazenamento de dados de usuários e tenants)
*   **Stripe Java SDK** (Integração de pagamentos)
*   **Apache Kafka** (Mensageria para trilhas de auditoria)
*   **Auth0 Java JWT** (Geração e validação de tokens)
*   **Passay** (Validação de políticas de senha)
*   **OWASP HTML Sanitizer** (Prevenção contra ataques XSS)

## 🚀 Como Executar

Este serviço depende do PostgreSQL e do Kafka. Certifique-se de que a infraestrutura esteja rodando via Docker Compose na raiz do projeto:

```bash
docker-compose up -d postgres zookeeper kafka
```

```bash
./mvnw spring-boot:run
```


# Cadastro Service

Microsserviço responsável pelo gerenciamento dos cadastros básicos e essenciais do ERP (Master Data). Ele fornece as entidades fundamentais que serão consumidas e referenciadas por outros módulos do sistema.

## 📋 Funcionalidades Principais

*   **Gestão de Condições de Pagamento:** Cadastro, listagem, atualização e exclusão das condições de pagamento disponíveis para uso em transações financeiras e comerciais.
*   **Gestão de Depósitos:** Controle de locais de armazenamento e estoques.
*   **Gestão de Grupos de Clientes:** Categorização e agrupamento de clientes para aplicação de regras de negócio específicas (ex: descontos, tabelas de preços).
*   **Auditoria e Rastreabilidade:** Registro das alterações e criações de cadastros importantes via eventos enviados ao serviço central de auditoria.
*   **Isolamento de Dados:** Filtro e separação de cadastros com base no Tenant autenticado, garantindo que um cliente não veja os dados de outro.

## 🛠️ Tecnologias Utilizadas

*   **Java 25**
*   **Spring Boot 3.4.x** (Web, Data JPA, Validation)
*   **PostgreSQL** (Armazenamento relacional dos dados de cadastro)
*   **Apache Kafka** (Producer para envio de eventos de auditoria)
*   **MapStruct** (Mapeamento entre Entidades e DTOs)
*   **Lombok** (Redução de boilerplate de código)

## 🚀 Como Executar

Este serviço requer o PostgreSQL, o Kafka, e o `auth-service` para validação correta dos acessos (ou o API Gateway configurado).

Inicie a infraestrutura básica na raiz do projeto:
```bash
docker-compose up -d postgres zookeeper kafka
```

Para rodar a aplicação localmente:
```bash
./mvnw spring-boot:run
```

## 📡 Integrações e Comunicação

O `cadastro-service` atua primariamente como uma API RESTful para os front-ends (`erp-front-end-admin` e `erp-front-end-web`).
As requisições devem passar pelo API Gateway, que lida com o roteamento e a injeção dos cabeçalhos do Tenant e do Usuário a partir do JWT gerado pelo `auth-service`.

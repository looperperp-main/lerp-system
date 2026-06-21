# Plano de Migração CI/CD: TeamCity + Qodana → Jenkins + SonarQube

**Motivação:** Qodana Free não roda em CI (requer licença paga). Testcontainers falha no agente TeamCity (Docker Desktop Status 400). Jenkins + SonarQube Community resolve ambos gratuitamente e é o stack enterprise mais maduro.

---

## Estado atual (workarounds ativos)

- Qodana desabilitado (`enabled = false`) — sem licença CI
- `DockerAvailableCondition` nos 4 serviços — ITs pulados graciosamente (SKIP)
- Thresholds temporários: `auth=0.20`, `cadastro/partner/billing=0.03`
- `billing-service` e `partner-service` têm `<parent>` errado (Spring Boot direto, não o monorepo) — bug pré-existente

---

## Fase 1 — Corrigir POMs pré-requisito

`billing-service/pom.xml` e `partner-service/pom.xml` têm `<parent>` apontando para `spring-boot-starter-parent:4.0.6` diretamente. Devem herdar do monorepo:

```xml
<parent>
    <groupId>com.l.erp</groupId>
    <artifactId>erp-vsd</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

Remover `<jacoco.version>` e `<testcontainers.version>` locais (já definidos no pai). Manter `<coverage.minimum>` por ora.

---

## Fase 2 — compose.yaml: adicionar SonarQube + Jenkins

### SonarQube Community

```yaml
  sonarqube-db-init:
    image: postgres:17-alpine
    container_name: sonarqube-db-init
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      PGPASSWORD: ${POSTGRES_PASSWORD}
    command: >
      sh -c "psql -h postgres -U ${POSTGRES_USER:-admin-user} -tc \"SELECT 1 FROM pg_database WHERE datname='sonarqube'\" | grep -q 1 || psql -h postgres -U ${POSTGRES_USER:-admin-user} -c 'CREATE DATABASE sonarqube;'"
    networks: [loop-erp-network]
    restart: "no"

  sonarqube:
    image: sonarqube:community
    container_name: erp-sonarqube
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      SONAR_JDBC_URL: jdbc:postgresql://postgres:5432/sonarqube
      SONAR_JDBC_USERNAME: ${POSTGRES_USER:-admin-user}
      SONAR_JDBC_PASSWORD: ${POSTGRES_PASSWORD}
    ports: ["9000:9000"]
    volumes:
      - sonarqube_data:/opt/sonarqube/data
      - sonarqube_extensions:/opt/sonarqube/extensions
      - sonarqube_logs:/opt/sonarqube/logs
    deploy:
      resources:
        limits:
          memory: 1536m
    ulimits:
      nofile: { soft: 65536, hard: 65536 }
    networks: [loop-erp-network]
```

### Jenkins LTS

```yaml
  jenkins:
    image: jenkins/jenkins:lts-jdk21
    container_name: erp-jenkins
    privileged: true
    user: root
    ports: ["8082:8080", "50000:50000"]
    volumes:
      - jenkins_home:/var/jenkins_home
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      - JAVA_OPTS=-Djenkins.install.runSetupWizard=false
      - TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal
      - DOCKER_HOST=unix:///var/run/docker.sock
    networks: [loop-erp-network]
```

Volumes a adicionar: `sonarqube_data`, `sonarqube_extensions`, `sonarqube_logs`, `jenkins_home`, `maven_cache`.

O `maven_cache` deve ser montado em `/root/.m2` no Jenkins para evitar baixar todas as dependências a cada build.

---

## Fase 3 — sonar-maven-plugin no POM raiz

Propriedades em `pom.xml` raiz:

```xml
<sonar.projectKey>erp-vsd</sonar.projectKey>
<sonar.projectName>ERP VSD</sonar.projectName>
<sonar.host.url>http://sonarqube:9000</sonar.host.url>
<sonar.java.source>25</sonar.java.source>
<sonar.exclusions>**/dto/**,**/dominio/**,**/domain/**,**/mappers/**,**/infra/config/**,**/*Application.java</sonar.exclusions>
<sonar.coverage.jacoco.xmlReportPaths>${project.basedir}/target/site/jacoco/jacoco.xml</sonar.coverage.jacoco.xmlReportPaths>
```

Plugin no `<pluginManagement>`:

```xml
<plugin>
    <groupId>org.sonarsource.scanner.maven</groupId>
    <artifactId>sonar-maven-plugin</artifactId>
    <version>5.1.0.4751</version>
</plugin>
```

JaCoCo `report-aggregate` precisa gerar também XML — adicionar `<format>XML</format>`.

---

## Fase 4 — Resolver Testcontainers no Jenkins

O Jenkins monta o socket do host via DooD (Docker-out-of-Docker). A variável crítica:

```
TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal
```

**Docker Desktop:** `host.docker.internal` é resolvido nativamente pelo Docker Desktop no Windows — nenhuma configuração extra é necessária. O socket `/var/run/docker.sock` também é exposto automaticamente pelo Docker Desktop, então o volume `- /var/run/docker.sock:/var/run/docker.sock` no Jenkins funciona sem ajuste.

Após o **primeiro build verde** confirmado: **remover** `@ExtendWith(DockerAvailableCondition.class)` dos 4 `AbstractIntegrationTest.java` e deletar as classes `DockerAvailableCondition.java`. Isso corresponde ao passo 11 da ordem de execução — não antes.

---

## Fase 5 — Jenkinsfile completo na raiz

O pipeline roda **exclusivamente na branch `main`**. O Job do Jenkins deve ser configurado com `Branch Sources` apontando só para `main` (ou via `Branches to build: */main` se for Pipeline job simples). Toda análise SonarQube vai para o projeto padrão (SonarQube Community não suporta branch analysis — comportamento correto para este fluxo).

```groovy
pipeline {
    agent any

    tools {
        maven 'Maven 3.9'
        jdk 'Temurin-25'
    }

    environment {
        DOCKER_HUB_CREDS                      = credentials('docker-hub-creds')
        SONAR_TOKEN                           = credentials('sonarqube-token')
        DOCKER_HOST                           = 'unix:///var/run/docker.sock'
        TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE = '/var/run/docker.sock'
        TESTCONTAINERS_HOST_OVERRIDE          = 'host.docker.internal'
        DOCKER_REGISTRY                       = 'vitorff1234'
        IMAGE_TAG                             = "${env.BUILD_NUMBER}"
        SONAR_HOST_URL                        = 'http://sonarqube:9000'
    }

    options {
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh './mvnw clean verify -pl auth-service,cadastro-service,partner-service,billing-service -am --batch-mode --no-transfer-progress'
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh './mvnw sonar:sonar -pl auth-service,cadastro-service,partner-service,billing-service -am -Dsonar.token=${SONAR_TOKEN} --batch-mode --no-transfer-progress'
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                sh '''
                    for svc in auth-service cadastro-service partner-service billing-service; do
                        docker build -t ${DOCKER_REGISTRY}/${svc}:${IMAGE_TAG} -t ${DOCKER_REGISTRY}/${svc}:latest -f ${svc}/Dockerfile ${svc}/
                    done
                    echo "${DOCKER_HUB_CREDS_PSW}" | docker login -u "${DOCKER_HUB_CREDS_USR}" --password-stdin
                    for svc in auth-service cadastro-service partner-service billing-service; do
                        docker push ${DOCKER_REGISTRY}/${svc}:${IMAGE_TAG}
                        docker push ${DOCKER_REGISTRY}/${svc}:latest
                    done
                '''
            }
            post { always { sh 'docker logout || true' } }
        }
    }

    post {
        cleanup {
            sh 'for svc in auth-service cadastro-service partner-service billing-service; do docker rmi ${DOCKER_REGISTRY}/${svc}:${IMAGE_TAG} || true; done'
            cleanWs()
        }
    }
}
```

---

## Fase 6 — Dockerfiles dos serviços que faltam

Apenas `auth-service` tem Dockerfile. Criar para `cadastro-service`, `partner-service`, `billing-service`:

```dockerfile
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

---

## Fase 7 — Configuração pós-instalação do Jenkins

Acessar `http://localhost:8082`.

**Plugins** (Manage Jenkins > Plugins):
- SonarQube Scanner
- Docker Pipeline
- HTML Publisher
- Credentials Binding

**Tools** (Manage Jenkins > Tools):
- JDK: `Temurin-25` via Adoptium installer
- Maven: `Maven 3.9` via Apache installer

**SonarQube Server** (Manage Jenkins > System):
- Name: `SonarQube` (exato — usado no Jenkinsfile)
- URL: `http://sonarqube:9000`

**Credenciais** (Manage Jenkins > Credentials):
- `docker-hub-creds`: Username/Password (vitorff1234 + senha Docker Hub)
- `sonarqube-token`: Secret text (token gerado em SonarQube > My Account > Security)

**Webhook SonarQube → Jenkins** (para `waitForQualityGate` funcionar):
SonarQube > Administration > Webhooks > Create: URL = `http://jenkins:8080/sonarqube-webhook/`

**Pipeline Job:** New Item > Pipeline > SCM > Git > Script Path: `Jenkinsfile` > Branches to build: `*/main`

**Quality Gate customizado** (antes do primeiro build — obrigatório):
SonarQube > Quality Gates > Create: nome `erp-vsd-gate`
- Remover a condição padrão de "Coverage on New Code ≥ 80%" (incompatível com thresholds atuais)
- Adicionar: Reliability Rating ≤ C, Security Rating ≤ C
- Em Projects: associar o projeto `erp-vsd` a este gate

Sem isso, o stage `Quality Gate` reprova imediatamente no primeiro build.

---

## Fase 8 — Elevar thresholds JaCoCo

Após primeiro build verde com ITs funcionando, elevar gradualmente:

```
0.03 → 0.15 → 0.30 → 0.40
```

Ao atingir 0.40 em todos: remover `<coverage.minimum>` locais dos módulos e herdar do pai (já está 0.40).

---

## Fase 9 — Remover TeamCity

Após Jenkins estável por alguns dias:

1. Remover serviços `teamcity-server` e `teamcity-agent` do `compose.yaml`
2. Remover volumes `teamcity_server_data`, `teamcity_server_logs`, `teamcity_agent_conf`
3. Deletar `teamcity-agent.Dockerfile`, pasta `.teamcity/`, `qodana.yaml`

> `DockerAvailableCondition.java` é removida na Fase 4, após o primeiro build verde — não aqui.

---

## Ordem de execução

| # | Etapa | Fase |
|---|---|---|
| 0 | `git config core.hooksPath .githooks` nos clones ativos | Hook |
| 1 | Garantir `chmod +x mvnw` commitado: `git update-index --chmod=+x mvnw` | pré-req |
| 2 | Corrigir POMs billing/partner → `./mvnw clean install -DskipTests` local | Fase 1 |
| 3 | Adicionar sonar-maven-plugin + XML ao JaCoCo no POM raiz | Fase 3 |
| 4 | Criar Dockerfiles dos 3 serviços restantes | Fase 6 |
| 5 | Atualizar compose.yaml (adicionar sonar + jenkins, manter teamcity) | Fase 2 |
| 6 | `docker compose up -d sonarqube-db-init sonarqube jenkins` | Fase 2 |
| 7 | Configurar Jenkins: plugins, tools, credenciais, sonar server, webhook | Fase 7 |
| 8 | Criar Quality Gate `erp-vsd-gate` no SonarQube e associar ao projeto | Fase 7 |
| 9 | Criar Jenkinsfile na raiz | Fase 5 |
| 10 | Criar Pipeline Job no Jenkins (branch: `*/main`) | Fase 7 |
| 11 | Primeiro build manual → validar todos os stages | — |
| 12 | Confirmar ITs rodando → remover `DockerAvailableCondition` dos 4 módulos | Fase 4 |
| 13 | Elevar thresholds JaCoCo progressivamente | Fase 8 |
| 14 | Remover TeamCity + limpeza final | Fase 9 |

---

## Hook local pre-push (gate antes da main)

O pipeline Jenkins roda só na `main`. O hook local previne que commits ruins cheguem lá, rodando `verify` apenas nos serviços afetados pelo push — sem custo de rodar tudo.

### Ativar (uma vez por clone)

```bash
git config core.hooksPath .githooks
```

O arquivo `.githooks/pre-push` já está no repositório — versiona junto com o código.

### Comportamento

| Situação | Ação |
|---|---|
| Só `Angular/`, `spec/`, infra | Skip — nenhum verify Java |
| `common/` alterado | Verify em **todos** os 4 serviços (`-am`) |
| Serviço(s) específico(s) alterado(s) | Verify só neles (`-am` inclui dependências) |
| Branch nova sem upstream | Diff contra `origin/main` |
| Branch sendo deletada | Skip |

### Lógica de diff

O hook lê os SHAs que o git passa via stdin (`local_sha` e `remote_sha`). Para branches novas (remote_sha = zeros), compara contra `origin/main`. O `-am` no Maven garante que dependências locais do módulo (como `common`) também sejam compiladas.

### Bypass explícito (emergência)

```bash
git push --no-verify
```

---

## Caminhos de falha e diagnóstico

### SonarQube não sobe — loop de reinicialização

**Sintoma:** `docker compose logs sonarqube` mostra `max virtual memory areas vm.max_map_count [65530] is too low`.

**Causa:** Elasticsearch interno do SonarQube exige `vm.max_map_count=262144`. No Docker Desktop para Windows, isso é configurado no WSL2.

**Fix:**
```bash
# No terminal WSL2 (wsl -d docker-desktop) ou via .wslconfig
wsl -d docker-desktop sysctl -w vm.max_map_count=262144
```

Para persistir, adicionar em `%USERPROFILE%\.wslconfig`:
```ini
[wsl2]
kernelCommandLine=sysctl.vm.max_map_count=262144
```

---

### Fase 1 — billing/partner quebram após corrigir parent

**Sintoma:** `./mvnw clean install -DskipTests` falha com erros de versão de dependência após trocar o `<parent>`.

**Causa:** Dependências que antes eram herdadas do `spring-boot-starter-parent` agora passam a vir do POM raiz do monorepo — se o POM raiz não tiver alguma versão declarada, Maven resolve diferente.

**Diagnóstico:** `./mvnw dependency:tree -pl billing-service` antes e depois da mudança. Comparar o que mudou.

**Fix:** Declarar explicitamente no `<dependencyManagement>` do módulo afetado a versão que quebrou, ou adicionar no POM raiz se for compartilhada.

---

### Jenkins não encontra `/var/run/docker.sock`

**Sintoma:** Stage `Docker Build & Push` falha com `Cannot connect to the Docker daemon at unix:///var/run/docker.sock`.

**Causa:** Docker Desktop no Windows expõe o socket via named pipe (`//./pipe/docker_engine`), mas o volume `- /var/run/docker.sock:/var/run/docker.sock` no compose.yaml mapeia o socket Unix. O Docker Desktop cria um symlink para o socket Unix automaticamente — mas pode não estar habilitado.

**Fix:** Em Docker Desktop > Settings > General > marcar **"Expose daemon on tcp://localhost:2375 without TLS"** e adicionar ao Jenkins no compose.yaml:
```yaml
environment:
  - DOCKER_HOST=tcp://host.docker.internal:2375
```
E remover o volume do socket.

---

### Testcontainers não conecta no postgres/kafka dentro do Jenkins

**Sintoma:** ITs falham com `Connection refused` ao tentar conectar no banco ou broker durante o build no Jenkins.

**Causa:** `host.docker.internal` resolve para o IP do host Windows, mas os containers do postgres/kafka precisam estar acessíveis nesse IP. Se o Docker Desktop não estiver expondo as portas no host (sem `-p` no compose), a conexão não chega.

**Diagnóstico:**
```bash
# Dentro do container Jenkins
docker exec -it erp-jenkins bash
curl -v http://host.docker.internal:5432  # deve recusar na camada TCP (não timeout)
```

**Fix:** Garantir que postgres e kafka têm `ports` declaradas no compose.yaml (já devem ter). Se ainda assim falhar, verificar o IP real: `docker exec erp-jenkins getent hosts host.docker.internal`.

---

### `waitForQualityGate` trava e aborta após 5 minutos

**Sintoma:** Stage `Quality Gate` fica aguardando e depois falha com `Quality gate check timed out`.

**Causa:** O webhook do SonarQube para o Jenkins não está configurado ou a URL está errada — o SonarQube nunca notifica o Jenkins que a análise terminou.

**Diagnóstico:** SonarQube > Administration > Webhooks — verificar se há entradas com falha no histórico de disparos do webhook.

**Fix:** URL correta do webhook: `http://erp-jenkins:8080/sonarqube-webhook/` (usar o nome do container, não `localhost`, pois ambos estão na mesma rede Docker).

---

### JDK 25 não disponível no Adoptium installer do Jenkins

**Sintoma:** Build falha em `Tools` com `Could not find JDK Temurin-25`.

**Causa:** O plugin Adoptium do Jenkins pode não listar JDK 25 se a versão do plugin for antiga ou se o JDK 25 não tiver release estável no Adoptium ainda.

**Fix:** Instalar o JDK 25 manualmente no container Jenkins e declarar como instalação local:
```bash
docker exec -it erp-jenkins bash
apt-get update && apt-get install -y wget
# baixar temurin-25 do adoptium.net e extrair em /opt/java/25
```
Depois em Manage Jenkins > Tools > JDK > Add JDK: desmarcar "Install automatically", apontar `JAVA_HOME` para `/opt/java/25`.

---

### Primeiro build lento — Maven baixando tudo do zero

**Sintoma:** Stage `Build & Test` demora 15–30 minutos no primeiro run.

**Causa:** O container Jenkins não tem cache Maven local — baixa todas as dependências do repositório central.

**Não é um erro** — acontece uma vez. Para evitar nas próximas vezes, montar o cache Maven como volume no compose.yaml:
```yaml
volumes:
  - jenkins_home:/var/jenkins_home
  - /var/run/docker.sock:/var/run/docker.sock
  - maven_cache:/root/.m2
```
Adicionar `maven_cache` nos volumes globais do compose.yaml.

---

## Riscos

| Risco | Mitigação |
|---|---|
| `billing-service` e `partner-service` com parent errado podem quebrar o build do monorepo | Fase 1 corrige isso antes de qualquer outra mudança — testar `./mvnw clean install -DskipTests` localmente antes de commitar |
| SonarQube Community não suporta branch analysis | Comportamento desejado: todo push em `main` analisa o projeto padrão. Não passar `sonar.branch.name` |
| Memória — SonarQube precisa ~2GB, mas TeamCity já consome ~3.5GB | Trocar TeamCity por Jenkins libera memória; é seguro subir ambos temporariamente durante a migração, depois remover o TeamCity na Fase 9 |
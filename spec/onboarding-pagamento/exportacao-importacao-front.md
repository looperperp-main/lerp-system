# Exportação e Importação de Dados — Experiência no Front-end (portal web)

**Status:** Draft técnico
**Autor:** Vitor
**Última atualização:** 12 de outubro de 2026
**Documentos relacionados:** `exportacao-dados-syax.md`, `migracao-dados-syax.md`
**Escopo:** o portal do tenant (`erp-front-end-web`) é a tela do cliente. O portal admin (`erp-front-end-admin`) tem uma tela de **suporte ao export** (abaixo); o parceiro não tem tela. A fila do concierge (Trilha 3) e o painel de SLA têm tela no admin (`/admin/importacoes`).

**Admin — suporte ao export (`/admin/exportacoes`):** lista paginada de exports de **qualquer tenant**, com filtro por tenant e status, e um detalhe com estado, tentativas, mensagem de falha, linhas por tabela e a linha do tempo da auditoria (`DOWNLOAD_DENIED` em destaque). API: `GET /migracao/admin/exports{,/{id}}`, restrita a `ROLE_APP_OWNER`. **Só metadados:** a API não devolve `download_token` nem caminho de arquivo, e não existe download pelo admin (o dado é do cliente, LGPD; export §5.3). Saúde do serviço: o `migracao-service` aparece sozinho no painel de saúde (a lista vem do Eureka).

Os dois docs de back-end definem estados, endpoints e regras, mas nenhum diz **o que o usuário vê**. Este doc fecha isso. A inspiração é o Oracle Fusion (File Import and Export / Scheduled Processes): o usuário **submete** um trabalho, recebe um **ID**, acompanha numa **tabela de processos** com status e baixa o **resultado** quando termina. Ninguém fica preso numa tela de espera.

---

## 1. Princípios

1. **Trabalho assíncrono é um objeto visível, não um spinner.** Todo export/import vira uma linha numa tabela de histórico (ID, status, quem pediu, quando). O usuário pode sair da tela e voltar; o e-mail de "pronto" (export §7) leva de volta a ela.
2. **Sem etapa manual no export (LGPD).** A tela nunca mostra "aguardando aprovação". O botão "Solicitar exportação" já inicia a extração.
3. **Ação indisponível por regra some, não fica desabilitada** (diretiva do CLAUDE.md). Ex.: "Baixar" só aparece em `READY`/`DOWNLOADED`.
4. **Erro sempre acionável, em PT-BR.** Nunca stacktrace nem código técnico. O texto vem de `failure_reason` / `message` do contrato de erro (import §9).
5. **Design system `jb-*`/PrimeNG**, um único `<p-toast>` global (o de `app.html`), nunca `[innerHTML]` com dado do usuário, estados de loading/vazio/erro em toda lista.

## 2. Navegação

Novo grupo no menu lateral (`web-layout.ts`), depois de "Segurança":

```
Dados & Migração            (pi pi-database)
  ├─ Exportar Dados         /web/dados/exportar
  └─ Importar Dados         /web/dados/importar
```

Rotas lazy como as demais (`loadComponent`), com breadcrumb `Dados & Migração › Exportar Dados`. Pastas: `pages/dados/exportar/`, `pages/dados/importar/`, mais `dados.service.ts` e `dados.model.ts` (mesmo padrão de `pages/estoque/`). Permissão: as authorities de export e import são decididas na fatia 1; sem a authority o item **não aparece** no menu.

## 3. Tela "Exportar Dados"

Duas áreas na mesma página, como o painel de tarefas + resultados do Fusion.

### 3.1 Solicitar exportação (card no topo)

- **Seleção de dados por grupo** (checkbox em árvore, tudo marcado por padrão):
  - Cadastros (19 tabelas), Compras (12), Estoque, Vendas, Emissão Fiscal.
  - Cada grupo expande para as tabelas; o rótulo é o nome de negócio ("Clientes", "Pedidos de Compra"), não o nome da tabela.
  - Não aparecem: `certificado_digital`, `idempotency_key`, `outbox_evento` (nunca exportados).
- **Período** (data inicial/final) — só vale para tabelas transacionais; cadastros sempre saem completos (o texto de ajuda diz isso).
- **Formato:** XLSX (padrão) ou CSV.
- **Motivo** (select opcional: "Backup / auditoria", "Migração para outro sistema", "Cancelamento do contrato"). `Cancelamento` envia `reason=CANCELLATION` e **não** muda nada na tela: nenhuma confirmação extra, nenhum aviso de retenção (export §10).
- Botão **"Solicitar exportação"**. Ao clicar: toast "Exportação solicitada. Avisaremos por e-mail quando estiver pronta." e a linha nova aparece no histórico.
- **Dedupe (export §5.2):** se já existe um export `READY` com o mesmo escopo, a API devolve o existente. A tela mostra o toast "Você já tem uma exportação pronta com esses dados" e destaca a linha, em vez de criar duplicata.

### 3.2 Histórico de exportações (tabela)

Componente `table` compartilhado. Colunas:

| Coluna | Conteúdo |
|---|---|
| ID | `batch_id` curto, copiável |
| Escopo | resumo ("5 grupos, 38 tabelas") com tooltip da lista |
| Solicitado por | usuário e data/hora |
| Status | chip colorido (abaixo) |
| Pronto em / Expira em | `ready_at` e `expires_at`; destaque âmbar quando faltar < 24h |
| Ações | ver abaixo |

**Status → chip → ações visíveis**

| Estado | Chip | Ações |
|---|---|---|
| `REQUESTED` | cinza "Na fila" | — |
| `PROCESSING` | azul "Gerando…" com barra indeterminada | — |
| `READY` | verde "Pronto" | **Baixar**, Detalhes |
| `DOWNLOADED` | verde-claro "Baixado" | **Baixar novamente** (enquanto não expirar), Detalhes |
| `FAILED` (tentativas < 3) | âmbar "Tentando novamente" | Detalhes |
| `FAILED` (3 tentativas) | vermelho "Falhou" + texto "Não foi possível gerar. Já avisamos nosso time." | Detalhes, **Solicitar novamente** |
| `EXPIRED` | cinza "Expirado" | **Solicitar novamente** (reaproveita o escopo) |

- **Polling:** enquanto houver linha em `REQUESTED`/`PROCESSING`, a tabela atualiza a cada 5 s via `GET /exports/{id}/status` (ou a lista); para sozinha quando não houver mais nenhuma. O token de download **não** vem no polling, só no clique em "Baixar" (export §5.3).
- **Baixar** abre `GET /exports/download/{token}` autenticado. Erro de download (token de outro tenant, expirado) mostra "Este arquivo não está mais disponível. Solicite uma nova exportação." — a API responde 404 por design, a tela traduz.
- **Detalhes** (painel lateral, estilo Fusion "Process Details"): escopo completo com contagem de linhas por tabela, formato, tamanho, e a **linha do tempo do `export_audit_log`** (Solicitado → Gerado → Baixado por Fulano em dd/mm). Só leitura.
- **Vazio:** ilustração + "Você ainda não fez nenhuma exportação. Seus dados são seus: exporte quando quiser." **Erro de carga:** mensagem + botão "Tentar novamente".

### 3.3 Aviso fixo (não bloqueante)

Texto curto abaixo do card: o que **não** é exportado (dashboards prontos, regras de automação, integrações ativas — tabela do export §9) e quanto tempo o arquivo fica disponível (7 dias). É a "comunicação com antecedência" que o §9 exige.

### 3.4 Download de posse (tenant em encerramento)

Página **fora** do `web-layout`, rota pública `/dados/posse/:token` (par do `PUBLIC_PREFIXES` do gateway): logo, "Seus dados estão prontos para download", nome do arquivo, tamanho, validade e **um botão**. Sem menu, sem login. Token inválido/expirado: "Este link não é mais válido." sem revelar o motivo. É a única tela do fluxo sem sessão.

## 4. Tela "Importar Dados"

Mesmo desenho: topo com ação, abaixo o histórico de lotes. A criação é um **assistente em etapas** (stepper PrimeNG), no estilo das "Import Activities" do Fusion.

### 4.1 Histórico de importações

Colunas: ID, Entidade ("Clientes", "Produtos"), Arquivo, Trilha (Template / Mapeamento / Concierge), Linhas (total · válidas · com erro), Status, Data. Status (import §5):

| Estado | Chip | Ação principal |
|---|---|---|
| `UPLOADED`/`VALIDATING` | azul "Validando…" | — |
| `AWAITING_FIX` | âmbar "Com erros" | **Corrigir** (abre etapa 4) |
| `READY` | verde "Pronto para importar" | **Revisar e importar** |
| `PROMOTING` | azul "Importando…" | — |
| `DONE` | verde "Concluído" | Ver resumo |
| `FAILED` | vermelho "Falhou" + `failure_reason` | **Enviar novo arquivo** |

Mesmo polling de 5 s enquanto houver lote em estado transitório. Botão **"Nova importação"** abre o assistente.

### 4.2 Assistente "Nova importação"

**Etapa 1 — O que importar.** Cards de entidade: **Clientes** e **Produtos** (as únicas com destino no MVP; plano de contas, saldo de abertura e lançamentos aparecem como "Em breve", sem ação). Mostra a ordem de dependência (import §11) quando relevante. Link **"Baixar modelo"** (template XLSX com dropdowns e máscaras) e "Ver campos" (lista do `canonical_schema_field`: nome, tipo, obrigatório, ajuda).

**Etapa 2 — Enviar arquivo.** Área de arrastar/soltar (XLSX/CSV, máx. 50 MB, limite exibido). Validação no cliente de extensão e tamanho antes do POST. Botão secundário **"Não consigo montar meu arquivo, preciso de ajuda"** → Trilha 3: confirma, cria o lote concierge e mostra "Recebemos seu pedido. Retornamos em até 48 horas com uma prévia para você conferir." O sistema **nunca** empurra o usuário para esse botão automaticamente (import §3.1).

**Etapa 3 — Mapear colunas** *(só aparece quando o cabeçalho não bateu com o modelo — Trilha 2, automática)*. Duas colunas: "Coluna do seu arquivo" (com 3 valores de exemplo) → select do campo SYAX (de `canonical_schema_field`). Campos obrigatórios sem mapa ficam em vermelho e travam o "Continuar". Mapeamento salvo do tenant vem pré-preenchido, com aviso "Reaproveitamos o mapeamento da sua última importação".

**Etapa 4 — Revisar.** Cards de resumo (Total · Válidas · Com erro). Se houver erro: tabela **linha / campo / mensagem / valor enviado** (contrato import §9), filtro por campo, paginação e botão **"Baixar relatório de erros"**. Dois caminhos de reenvio (import §9.1): **"Reenviar só as linhas corrigidas"** (incremental, reusa o mapeamento) e **"Enviar arquivo completo corrigido"**. Sem erro: mensagem verde e botão ativo.

**Etapa 5 — Confirmar.** Resumo ("Serão importados 498 clientes. 12 já existentes serão atualizados pelo CNPJ/CPF"). Botão **"Importar"**. A promoção é transacional (tudo ou nada); a tela mostra "Importando…" e o lote vira `DONE` ou `FAILED` com a razão. Pré-requisito não satisfeito (`READY → AWAITING_FIX`) volta à etapa 4 com a mensagem da dependência.

O assistente é **retomável**: sair no meio não perde o lote; ele continua na tabela com o status certo e o botão leva à etapa correspondente.

## 5. Estados e feedback (checklist para as duas telas)

- Loading de lista: skeleton da tabela (não spinner de página).
- Vazio: mensagem com próximo passo.
- Erro de rede/5xx: mensagem genérica em PT-BR + "Tentar novamente"; o `correlationId` aparece só num "Detalhes técnicos" recolhido para o suporte.
- 4xx de regra de negócio: a `message` da API no toast.
- Responsivo: tabelas viram cards abaixo de 768 px; stepper vira lista vertical.
- Acessibilidade: chips com texto (não só cor), foco visível, `aria-live` na atualização de status.
- Datas em `dd/MM/yyyy HH:mm` no fuso do navegador.

## 6. Contrato que o front exige do back-end

Além dos endpoints já nos docs, o front precisa de:

| Endpoint | Para quê | Fatia |
|---|---|---|
| `GET /migracao/exports/catalogo` | lista de grupos/tabelas exportáveis com rótulo PT-BR (a árvore da §3.1 não pode ser hardcoded no front) | 1 |
| `GET /migracao/exports` | histórico paginado do tenant | 1 |
| `GET /migracao/exports/{id}` | detalhes + linha do tempo do audit log | 1 |
| `GET /migracao/imports` e `GET /migracao/imports/{id}` | histórico e resumo do lote | 4 |
| `GET /migracao/imports/{id}/errors` | erros paginados (a tabela da etapa 4) | 4 |
| `GET /migracao/imports/modelo/{entity}` | baixar o template | 4 |
| `GET /migracao/schema/{entity}` | campos canônicos (etapas 1 e 3) | 4 |

## 7. Entrega

Seguem as fatias dos docs de back-end; o front de cada uma entra **depois** que a API da fatia estiver revisada:

1. Export: tela "Exportar Dados" completa (§3.1–3.3) junto com a fatia 1/2.
2. Download de posse (§3.4) junto com a fatia de encerramento.
3. Import: histórico + assistente (§4) a partir da Trilha 1, com a etapa 3 só quando a Trilha 2 existir e o botão de concierge só com a Trilha 3.

Telas do portal web (`pages/dados/exportar` e `importar`) e do admin (`/admin/importacoes`) escritas em 12 de outubro de 2026 (fatia 8), **não testadas**. Fora do que está aqui: tela pública de posse (§3.4), reenvio incremental (§4.2 etapa 4) e visibilidade do menu por authority.

---
description: Aplica o redesign visual dark "jb-*" (o mesmo feito em Pessoas) numa tela de CRUD do erp-front-end-web, dado o caminho da pasta.
argument-hint: <caminho-da-pasta-do-crud, ex: Angular/erp-front-end-web/src/app/pages/cadastros/cliente>
---

Aplique o redesign visual dark "jb-*" na pasta de CRUD indicada em `$ARGUMENTS`, seguindo o mesmo processo já feito em `Angular/erp-front-end-web/src/app/pages/cadastros/pessoas`.

## 0. Validação (obrigatória, antes de qualquer edição)

1. Confirme que `$ARGUMENTS` existe e é uma pasta.
2. Procure dentro dela uma subpasta cujo nome contenha `form` (ex.: `cliente-form`, `pessoa-form`, `xyz-form`).
   - **Se existir**: continue o processo normalmente (ela é o formulário/modal de criação-edição).
   - **Se NÃO existir**: PARE. Não edite nada. Liste os arquivos/subpastas encontrados e pergunte ao usuário como proceder (ex.: não há modal de form, é só listagem? tratar diferente?).

## 1. Referências a consultar antes de estilizar

- Ler a memória `feedback_primeng_css_gotchas` (armadilhas de CSS/PrimeNG já mapeadas) e `project_pessoas_redesign` (estado/decisões já tomadas) se disponíveis — evita repetir erros de nome de classe já corrigidos.
- Usar como gabarito os arquivos já finalizados de Pessoas:
  - `pessoas.html` / `pessoas.scss` / `pessoas.ts` (listagem)
  - `pessoa-form/pessoa-form.html` / `pessoa-form.scss` (modal)

## 2. Checklist de estilização (listagem — arquivo `<entidade>s.html`/`.scss`)

- Página/painel no padrão `jb-page-head`, `jb-panel`, `jb-panel-head`, `jb-panel-actions` (reaproveitar classes já existentes no design system, não recriar).
- Botões "Novo <Entidade>" e "Exportar" com a classe `.jb-btn-paginator-style` (gradiente laranja `--brand-grad`, `border-radius: 8px`, mesmo visual do número de página selecionado).
- Tabela (`:host ::ng-deep`): fundo transparente, header em JetBrains Mono maiúsculo, linhas com hover sutil, botões de ação da linha quadrados com borda de vidro (`.p-button-rounded` restilizado, `border-radius: 10px !important`).
- Remover `showCurrentPageReport`/`currentPageReportTemplate` da `p-table` e substituir por um `<footer class="jb-list-footer">` com copyright + total de registros (JetBrains Mono).
- Paginador alinhado à direita (`justify-content: flex-end !important`), página selecionada com a classe real `.p-paginator-page-selected` (não `.p-highlight`) em laranja/`--brand-grad`, quadrado (`border-radius: 8px`).
- Seção de Filtros (`.jb-filters-panel` + `.jb-filters-row`) acima da tabela, com input + botão "Filtrar" (`.jb-btn-paginator-style`) — só se o usuário pedir explicitamente; caso a tela já não tenha isso, pergunte se quer adicionar (não assumir).

## 3. Checklist de estilização (modal — `<entidade>-form.html`/`.scss`)

- `p-dialog` deve permanecer **arrastável**: `[draggable]="true"` (ou simplesmente omitir o atributo — esse é o default do PrimeNG). Nunca colocar `[draggable]="false"`.
- `p-dialog-mask` transparente (sem escurecer o resto da página) — só o painel do modal tem vidro.
- `.p-dialog` com glass: `rgba(40, 40, 50, 0.32)` + `backdrop-filter: blur(35px) saturate(180%)` + borda `rgba(255,255,255,0.16)`.
- Botão de fechar: classe real `.p-dialog-close-button`, `border-radius: 8px !important` (vem `rounded: true` por padrão do PrimeNG).
- Todo `p-select`/`p-datepicker` dentro do modal recebe `appendTo="body"` (evita overlay preso atrás dos campos seguintes por causa do `backdrop-filter`).
- Estilo dos overlays (`p-select-overlay`, `p-datepicker-panel`) já é global em `src/styles.scss` — não duplicar por componente; só usar se precisar de algo específico dessa tela.
- Botão "Finalizar"/"Salvar" sem `severity="success"` (verde) — manter o botão no estilo padrão laranja do app.

## 4. Fonte e cores globais

- Fonte já é `"Inter Tight", Inter, -apple-system, BlinkMacSystemFont, sans-serif"` globalmente (seletor `*` em `styles.scss`) — não reaplicar por componente.
- Não usar `!important` em wildcard tipo `X *`/`.p-dialog *` — quebra os ícones `.pi` (empate de especificidade). Se precisar de um seletor curinga, usar `:not(.pi)`.
- Cor de destaque/seleção do app é laranja (`--syax` / `--brand-grad`), nunca o verde/esmeralda padrão do tema Aura.

## 5. Ao final

- Não rodar nenhum comando de build/serve (`ng serve`, `npm run build`, etc.) — o usuário testa manualmente.
- Propor mensagem de commit no formato do `CLAUDE.md` do projeto.
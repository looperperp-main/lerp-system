# -*- coding: utf-8 -*-
"""
Gera docs/security-audit/relatorio-auditoria-seguranca.pdf a partir de findings_data.py.

Uso:
    docs/security-audit/.venv/Scripts/python.exe docs/security-audit/generate_report.py

Reinstala o venv isolado se precisar:
    python -m venv docs/security-audit/.venv
    docs/security-audit/.venv/Scripts/python.exe -m pip install reportlab matplotlib
"""
import io
import os
from xml.sax.saxutils import escape as xml_escape

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import cm
from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_LEFT, TA_CENTER
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, Table, TableStyle,
    Image, NextPageTemplate, PageBreak, KeepTogether, HRFlowable,
)

from findings_data import (
    PROJECT_NAME, AUDIT_DATE, STACK_SUMMARY, METHODOLOGY, CATEGORY_NAMES,
    SEVERITY_COLORS, SEVERITY_LABELS, FINDINGS, STRENGTHS,
)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_PDF = os.path.join(HERE, "relatorio-auditoria-seguranca.pdf")

INK = HexColor("#111827")
MUTED = HexColor("#6B7280")
LINE = HexColor("#E5E7EB")
PAGE_BG = HexColor("#FFFFFF")

styles = getSampleStyleSheet()
styles.add(ParagraphStyle("H1", fontName="Helvetica-Bold", fontSize=22, leading=26, textColor=INK, spaceAfter=6))
styles.add(ParagraphStyle("H2", fontName="Helvetica-Bold", fontSize=15, leading=18, textColor=INK, spaceBefore=14, spaceAfter=8))
styles.add(ParagraphStyle("H3", fontName="Helvetica-Bold", fontSize=11.5, leading=14, textColor=INK, spaceBefore=8, spaceAfter=4))
styles.add(ParagraphStyle("Body", fontName="Helvetica", fontSize=9.3, leading=13, textColor=INK, alignment=TA_LEFT))
styles.add(ParagraphStyle("BodyMuted", parent=styles["Body"], textColor=MUTED))
styles.add(ParagraphStyle("Cover", fontName="Helvetica", fontSize=11, leading=16, textColor=INK, alignment=TA_CENTER))
styles.add(ParagraphStyle("Mono", fontName="Courier", fontSize=7.6, leading=10.2, textColor=INK, backColor=HexColor("#F3F4F6")))
styles.add(ParagraphStyle("Small", fontName="Helvetica", fontSize=8, leading=11, textColor=MUTED))

REPORT_TITLE = f"Relatório de Auditoria de Segurança — {PROJECT_NAME}"


def _wrap_mono(text, width=100):
    text = xml_escape(text)
    lines = []
    for raw_line in text.split("\n"):
        while len(raw_line) > width:
            lines.append(raw_line[:width])
            raw_line = raw_line[width:]
        lines.append(raw_line)
    return "<br/>".join(l if l else "&nbsp;" for l in lines)


def header_footer(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(PAGE_BG)
    canvas.rect(0, 0, A4[0], A4[1], fill=1, stroke=0)
    if doc.page > 1:
        canvas.setFont("Helvetica", 8)
        canvas.setFillColor(MUTED)
        canvas.drawString(2 * cm, A4[1] - 1.3 * cm, REPORT_TITLE)
        canvas.drawRightString(A4[0] - 2 * cm, A4[1] - 1.3 * cm, PROJECT_NAME)
        canvas.setStrokeColor(LINE)
        canvas.line(2 * cm, A4[1] - 1.5 * cm, A4[0] - 2 * cm, A4[1] - 1.5 * cm)
        canvas.line(2 * cm, 1.5 * cm, A4[0] - 2 * cm, 1.5 * cm)
        canvas.drawCentredString(A4[0] / 2, 1.1 * cm, f"Página {doc.page}")
    canvas.restoreState()


def build_doc():
    doc = BaseDocTemplate(OUT_PDF, pagesize=A4,
                           leftMargin=2 * cm, rightMargin=2 * cm,
                           topMargin=2 * cm, bottomMargin=2 * cm)
    cover_frame = Frame(2 * cm, 2 * cm, A4[0] - 4 * cm, A4[1] - 4 * cm, id="cover")
    body_frame = Frame(2 * cm, 1.8 * cm, A4[0] - 4 * cm, A4[1] - 3.8 * cm, id="body")
    doc.addPageTemplates([
        PageTemplate(id="Cover", frames=[cover_frame], onPage=header_footer),
        PageTemplate(id="Body", frames=[body_frame], onPage=header_footer),
    ])
    return doc


# ---------------------------------------------------------------------------
# Charts
# ---------------------------------------------------------------------------
def chart_donut_severity():
    order = ["critica", "alta", "media", "baixa", "informativa"]
    counts = {s: 0 for s in order}
    for f in FINDINGS:
        counts[f["severidade"]] += 1
    order = [s for s in order if counts[s] > 0]
    vals = [counts[s] for s in order]
    colors = [SEVERITY_COLORS[s] for s in order]
    labels = [f"{SEVERITY_LABELS[s]} ({counts[s]})" for s in order]

    fig, ax = plt.subplots(figsize=(4.6, 3.4), dpi=200)
    wedges, _ = ax.pie(vals, colors=colors, startangle=90, wedgeprops=dict(width=0.42, edgecolor="white", linewidth=2))
    ax.legend(wedges, labels, loc="center left", bbox_to_anchor=(1.0, 0.5), frameon=False, fontsize=9)
    ax.text(0, 0, str(sum(vals)), ha="center", va="center", fontsize=20, fontweight="bold", color="#111827")
    ax.text(0, -0.28, "achados", ha="center", va="center", fontsize=8.5, color="#6B7280")
    ax.set_aspect("equal")
    fig.tight_layout()
    buf = io.BytesIO()
    fig.savefig(buf, format="png", transparent=True, bbox_inches="tight")
    plt.close(fig)
    buf.seek(0)
    return buf


def chart_bar_category():
    cat_counts = {c: 0 for c in CATEGORY_NAMES}
    for f in FINDINGS:
        for c in f["categoria"]:
            cat_counts[c] += 1
    cats = sorted(cat_counts.keys())
    vals = [cat_counts[c] for c in cats]
    labels = [f"Cat. {c}" for c in cats]

    fig, ax = plt.subplots(figsize=(6.6, 3.2), dpi=200)
    bars = ax.bar(labels, vals, color="#2563EB", width=0.55)
    for b, v in zip(bars, vals):
        ax.text(b.get_x() + b.get_width() / 2, v + 0.05, str(v), ha="center", fontsize=9, color="#111827")
    ax.set_ylim(0, max(vals + [1]) + 1)
    ax.spines[["top", "right", "left"]].set_visible(False)
    ax.get_yaxis().set_visible(False)
    ax.tick_params(axis="x", labelsize=8.5, colors="#374151")
    fig.tight_layout()
    buf = io.BytesIO()
    fig.savefig(buf, format="png", transparent=True, bbox_inches="tight")
    plt.close(fig)
    buf.seek(0)
    return buf


def severity_chip(sev):
    color = SEVERITY_COLORS[sev]
    label = SEVERITY_LABELS.get(sev, sev)
    t = Table([[label]], colWidths=[2.4 * cm])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), HexColor(color)),
        ("TEXTCOLOR", (0, 0), (-1, -1), HexColor("#FFFFFF")),
        ("FONTNAME", (0, 0), (-1, -1), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), 7.5),
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ]))
    return t


# ---------------------------------------------------------------------------
# Story
# ---------------------------------------------------------------------------
def build_story():
    story = []

    # ---- Capa -------------------------------------------------------------
    story.append(Spacer(1, 3.5 * cm))
    story.append(Paragraph(REPORT_TITLE, ParagraphStyle("CoverTitle", parent=styles["H1"], fontSize=24, alignment=TA_CENTER, leading=29)))
    story.append(Spacer(1, 0.4 * cm))
    story.append(Paragraph(f"Data: {AUDIT_DATE}", styles["Cover"]))
    story.append(Spacer(1, 1.2 * cm))
    story.append(HRFlowable(width="60%", thickness=1, color=LINE, hAlign="CENTER"))
    story.append(Spacer(1, 1 * cm))
    story.append(Paragraph("Escopo", styles["H3"]))
    story.append(Paragraph(STACK_SUMMARY, styles["Body"]))
    story.append(Spacer(1, 0.5 * cm))
    story.append(Paragraph("Metodologia", styles["H3"]))
    story.append(Paragraph(METHODOLOGY, styles["Body"]))
    story.append(Spacer(1, 0.5 * cm))
    story.append(Paragraph("Categorias avaliadas", styles["H3"]))
    cat_rows = [[str(k), v] for k, v in CATEGORY_NAMES.items()]
    cat_table = Table([["#", "Categoria"]] + cat_rows, colWidths=[1 * cm, 15 * cm])
    cat_table.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTNAME", (0, 1), (-1, -1), "Helvetica"),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("TEXTCOLOR", (0, 0), (-1, -1), INK),
        ("LINEBELOW", (0, 0), (-1, 0), 0.75, LINE),
        ("LINEBELOW", (0, 1), (-1, -1), 0.4, LINE),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    story.append(cat_table)
    story.append(NextPageTemplate("Body"))
    story.append(PageBreak())

    # ---- Resumo executivo --------------------------------------------------
    story.append(Paragraph("Resumo executivo", styles["H1"]))
    sev_counts = {s: 0 for s in SEVERITY_LABELS}
    for f in FINDINGS:
        sev_counts[f["severidade"]] += 1
    total = len(FINDINGS)
    total_strengths = len(STRENGTHS)
    story.append(Paragraph(
        f"{total} achados verificados em código real ({sev_counts['critica']} crítico, "
        f"{sev_counts['alta']} alto, {sev_counts['media']} médio, {sev_counts['baixa']} baixo, "
        f"{sev_counts['informativa']} informativo) e {total_strengths} pontos fortes confirmados "
        f"com evidência, cobrindo os 5 serviços de aplicação (auth, cadastro, partner, billing, "
        f"fiscal), o gateway e os 3 frontends Angular.",
        styles["Body"]))
    story.append(Spacer(1, 0.4 * cm))

    donut_buf = chart_donut_severity()
    bar_buf = chart_bar_category()
    chart_row = Table([[
        Image(donut_buf, width=8.6 * cm, height=6.4 * cm),
        Image(bar_buf, width=8.2 * cm, height=4 * cm),
    ]], colWidths=[8.8 * cm, 8.4 * cm])
    chart_row.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "MIDDLE")]))
    story.append(Paragraph("Achados por severidade", styles["H3"]))
    story.append(Paragraph("Achados por categoria", styles["H3"]))
    story.append(chart_row)
    story.append(Spacer(1, 0.3 * cm))

    story.append(Paragraph("Principal risco", styles["H2"]))
    critical = [f for f in FINDINGS if f["severidade"] == "critica"]
    if critical:
        f = critical[0]
        story.append(Paragraph(f"<b>{f['id']} — {f['titulo']}</b>", styles["Body"]))
        story.append(Paragraph(f["por_que"], styles["Body"]))
    story.append(PageBreak())

    # ---- Pontos fortes ------------------------------------------------------
    story.append(Paragraph("Pontos fortes (verificados)", styles["H1"]))
    story.append(Paragraph(
        "O que foi conferido em código real e está correto — evidência de cobertura da auditoria, "
        "não apenas ausência de achados.", styles["BodyMuted"]))
    story.append(Spacer(1, 0.2 * cm))
    for s in STRENGTHS:
        block = [
            Paragraph(f"<font color='#059669'>&#9679;</font> <b>{s['titulo']}</b>", styles["Body"]),
            Paragraph(s["evidencia"], styles["Body"]),
            Spacer(1, 0.25 * cm),
        ]
        story.append(KeepTogether(block))
    story.append(PageBreak())

    # ---- Achados detalhados ---------------------------------------------------
    story.append(Paragraph("Achados detalhados", styles["H1"]))
    story.append(Spacer(1, 0.1 * cm))

    for f in FINDINGS:
        cats = ", ".join(CATEGORY_NAMES[c] for c in f["categoria"])
        head = Table([[severity_chip(f["severidade"]), Paragraph(f"<b>{f['id']} — {f['titulo']}</b>", styles["Body"])]],
                     colWidths=[2.6 * cm, 14.4 * cm])
        head.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP")]))
        block = [head, Spacer(1, 0.15 * cm), Paragraph(f"<b>Categoria:</b> {cats}", styles["Small"])]

        rows = [[Paragraph(p, styles["Small"]), Paragraph(l, styles["Small"])] for p, l in f["arquivos"]]
        file_table = Table(rows, colWidths=[12.5 * cm, 4.5 * cm])
        file_table.setStyle(TableStyle([
            ("FONTSIZE", (0, 0), (-1, -1), 7.6),
            ("LINEBELOW", (0, 0), (-1, -1), 0.3, LINE),
            ("TOPPADDING", (0, 0), (-1, -1), 2),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
        ]))
        block += [Spacer(1, 0.15 * cm), file_table, Spacer(1, 0.15 * cm)]
        block.append(Paragraph(_wrap_mono(f["snippet"]), styles["Mono"]))
        block += [Spacer(1, 0.15 * cm), Paragraph("<b>Por que é explorável:</b> " + f["por_que"], styles["Body"])]
        block += [Spacer(1, 0.1 * cm), Paragraph("<b>Condições de exploração:</b> " + f["exploitability"], styles["Body"])]
        block.append(Spacer(1, 0.5 * cm))
        block.append(HRFlowable(width="100%", thickness=0.5, color=LINE))
        block.append(Spacer(1, 0.3 * cm))
        story.append(KeepTogether(block))

    story.append(PageBreak())

    # ---- Tabela consolidada ---------------------------------------------------
    story.append(Paragraph("Tabela consolidada de achados", styles["H1"]))
    table_rows = [["Severidade", "Arquivo:linha", "Descrição"]]
    style_cmds = [
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), 8),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LINEBELOW", (0, 0), (-1, 0), 0.75, LINE),
        ("LINEBELOW", (0, 1), (-1, -1), 0.3, LINE),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]
    for f in FINDINGS:
        first_file = f"{f['arquivos'][0][0].split('/')[-1]}:{f['arquivos'][0][1]}"
        extra = f" (+{len(f['arquivos']) - 1})" if len(f["arquivos"]) > 1 else ""
        table_rows.append([
            severity_chip(f["severidade"]),
            Paragraph(first_file + extra, styles["Small"]),
            Paragraph(f"<b>{f['id']}</b> — {f['titulo']}", styles["Small"]),
        ])
    cons_table = Table(table_rows, colWidths=[2.6 * cm, 3.8 * cm, 10.6 * cm], repeatRows=1)
    cons_table.setStyle(TableStyle(style_cmds))
    story.append(cons_table)
    story.append(PageBreak())

    # ---- Recomendações ---------------------------------------------------
    story.append(Paragraph("Recomendações priorizadas", styles["H1"]))
    priorities = [
        ("P1", "Adicionar @PreAuthorize em todos os métodos administrativos de PartnerController "
                "(findAll, findById, listIndicacoes, origemPorTenant, engajamentoPorTenant, update, "
                "approve, reject, inactivate), restritos a um authority equivalente a PARTNER_MANAGE — "
                "seguindo o padrão já usado em SubscriptionController/RoleController. Referência: F1.",
         "critica"),
        ("P2", "Adicionar guard de tipo/role no route Angular /parceiros/** do erp-front-end-admin "
                "(hoje só checa presença de token) para refletir a restrição de backend assim que ela "
                "existir — defesa em profundidade, não substitui P1.", "media"),
        ("P3", "Sanitizar (ex.: org.springframework.web.util.HtmlUtils.htmlEscape) todo dado de "
                "usuário concatenado no HTML dos e-mails em EmailConsumerService, com atenção "
                "prioritária ao campo message livre do follow-up. Referência: F3.", "media"),
        ("P4", "Remover o default ${REDIS_PASSWORD:-test-password} de compose.yaml e "
                "billing-service/application.yaml — falhar no startup se a env var não estiver "
                "setada, no mesmo padrão de JWT_SECRET/DB_PASS. Referência: F2.", "baixa"),
        ("P5", "Adicionar @PostConstruct de validação de tamanho mínimo do JWT_SECRET no gateway "
                "(mesma guarda que já existe em TokenService do auth-service). Referência: F4.", "baixa"),
        ("P6", "Girar a senha do Grafana em compose.yaml para uma env var sem default, e revisitar o "
                "item de backlog já existente sobre /actuator/loggers sem autenticação. Referências: "
                "F5, F6.", "informativa"),
    ]
    for pid, text, sev in priorities:
        row = Table([[severity_chip(sev), Paragraph(f"<b>{pid}.</b> {text}", styles["Body"])]],
                     colWidths=[2.6 * cm, 14.4 * cm])
        row.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP")]))
        story.append(row)
        story.append(Spacer(1, 0.3 * cm))
    story.append(PageBreak())

    # ---- Issues para o GitHub ---------------------------------------------
    story.append(Paragraph("Issues para o GitHub", styles["H1"]))
    story.append(Paragraph(
        "Texto completo pronto para colar em issues do GitHub. Cada bloco começa em "
        "<b>--- ISSUE n ---</b> e termina em <b>--- FIM ISSUE n ---</b>.", styles["BodyMuted"]))
    story.append(Spacer(1, 0.2 * cm))

    issues = build_github_issues()
    for i, issue_md in enumerate(issues, start=1):
        story.append(Paragraph(f"--- ISSUE {i} ---", styles["Mono"]))
        story.append(Paragraph(_wrap_mono(issue_md, width=95), styles["Mono"]))
        story.append(Paragraph(f"--- FIM ISSUE {i} ---", styles["Mono"]))
        story.append(Spacer(1, 0.4 * cm))

    return story


def build_github_issues():
    """Monta o markdown de cada issue. Achados de baixo impacto/relacionados são agrupados."""
    issues = []

    def evidence_block(f):
        return "\n".join(f"- `{path}:{ln}`" for path, ln in f["arquivos"])

    # Issue 1 — F1 (crítico, sozinho)
    f = next(x for x in FINDINGS if x["id"] == "F1")
    issues.append(f"""# [Segurança] {f['titulo']}

**Labels sugeridas:** security, severity:critical

## Problema
{f['por_que']}

## Evidência
{evidence_block(f)}

```
{f['snippet']}
```

## Condições de exploração
{f['exploitability']}

## Impacto
Qualquer usuário autenticado (qualquer tenant, qualquer tipo de login exceto PARTNER) pode listar, \
ler, editar, aprovar, reprovar e inativar parceiros de toda a plataforma via a API pública do \
partner-service — exposição total de dados de parceiros/tenants e possibilidade de aprovar contas \
fraudulentas ou inativar parceiros legítimos.

## Sugestão de correção
Adicionar `@PreAuthorize` em cada método administrativo de `PartnerController` (findAll, findById, \
listIndicacoes, origemPorTenant, engajamentoPorTenant, update, approve, reject, inactivate) exigindo \
uma authority equivalente a `PARTNER_MANAGE`, seguindo o padrão já usado em \
`SubscriptionController`/`RoleController`. Os endpoints `/me/*` (já seguros, escopados por JWT) e \
`POST /api/v1/partners` + `GET /cnpj/{{cnpj}}` (intencionalmente públicos) não precisam de mudança.

## Critérios de aceite
- [ ] Todo método de `PartnerController` que recebe um `id`/`tenantId` arbitrário e não é `/me/*` \
tem `@PreAuthorize` com uma authority de gestão de parceiros
- [ ] Teste de integração cobrindo 403 para usuário sem a authority em cada um desses endpoints
- [ ] Teste de integração cobrindo 200 para usuário com a authority
- [ ] `SecurityConfig.java` do partner-service documentado (comentário) explicando que a autorização \
é 100% via `@PreAuthorize`, não pelo filtro HTTP
""")

    # Issue 2 — F3 (XSS/HTML injection e-mail)
    f = next(x for x in FINDINGS if x["id"] == "F3")
    issues.append(f"""# [Segurança] {f['titulo']}

**Labels sugeridas:** security, severity:medium

## Problema
{f['por_que']}

## Evidência
{evidence_block(f)}

```
{f['snippet']}
```

## Condições de exploração
{f['exploitability']}

## Impacto
HTML injection no corpo de e-mails transacionais enviados a tenants/clientes — vetor de phishing \
(links falsos, layout falsificado se passando por comunicação oficial da plataforma).

## Sugestão de correção
Aplicar `org.springframework.web.util.HtmlUtils.htmlEscape(...)` (ou equivalente OWASP encoder) em \
todo dado de usuário (`name`, `partnerName`, `clientName`, `clientCnpj`, `message`, itens de \
`features`/`gaps`) antes de concatenar no HTML do e-mail em `EmailConsumerService`.

## Critérios de aceite
- [ ] Todo `String.format`/text block que monta HTML de e-mail em `EmailConsumerService` escapa os \
parâmetros de entrada
- [ ] Teste unitário cobrindo que um `message` contendo `<script>`/`<img onerror=...>` chega \
escapado no corpo final do e-mail
""")

    # Issue 3 — F2 + F4 + F5 (agrupados: hardening de config/secrets, baixo impacto)
    f2 = next(x for x in FINDINGS if x["id"] == "F2")
    f4 = next(x for x in FINDINGS if x["id"] == "F4")
    f5 = next(x for x in FINDINGS if x["id"] == "F5")
    issues.append(f"""# [Segurança] Hardening de configuração: defaults perigosos e validação de secrets

**Labels sugeridas:** security, severity:low

## Problema
Três achados relacionados de hardening de configuração, agrupados por serem baixo impacto e \
mesma categoria (chaves/segredos):

1. **{f2['titulo']}** — {f2['por_que']}
2. **{f4['titulo']}** — {f4['por_que']}
3. **{f5['titulo']}** — {f5['por_que']}

## Evidência
### 1. Redis default
{evidence_block(f2)}
```
{f2['snippet']}
```

### 2. Gateway sem validação de JWT_SECRET
{evidence_block(f4)}
```
{f4['snippet']}
```

### 3. Grafana hardcoded
{evidence_block(f5)}
```
{f5['snippet']}
```

## Impacto
Baixo isoladamente (todos exigem uma pré-condição de rede/config incorreta para serem explorados), \
mas compõem uma superfície de "falha silenciosa" — o sistema sobe funcional mesmo com configuração \
insegura, em vez de falhar rápido.

## Sugestão de correção
- Remover o fallback `:-test-password` do Redis em `compose.yaml` e `billing-service/application.yaml`
- Adicionar `@PostConstruct` de validação de tamanho mínimo do `JWT_SECRET` em \
`gateway/SecurityFilter.java`, espelhando `TokenService.java` do auth-service
- Mover a senha do Grafana em `compose.yaml` para uma env var sem default

## Critérios de aceite
- [ ] Redis e billing-service falham no startup se `REDIS_PASSWORD` não estiver setada
- [ ] Gateway falha no startup se `JWT_SECRET` tiver menos de 32 caracteres
- [ ] `GF_SECURITY_ADMIN_PASSWORD` vem de env var sem default no `compose.yaml`
""")

    return issues


def verify_output(pdf_path):
    """Confere paginação/tamanho básico do PDF gerado, sem dependência externa."""
    with open(pdf_path, "rb") as fh:
        data = fh.read()
    n_pages = data.count(b"/Type /Page") - data.count(b"/Type /Pages")
    size_kb = len(data) / 1024
    print(f"[verify] {pdf_path}: ~{n_pages} páginas de conteúdo, {size_kb:.0f} KB")
    assert size_kb > 20, "PDF suspeito de estar vazio/corrompido"


def main():
    doc = build_doc()
    story = build_story()
    doc.build(story)
    verify_output(OUT_PDF)
    print(f"OK: {OUT_PDF}")


if __name__ == "__main__":
    main()

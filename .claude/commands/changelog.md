---
description: Lê o diff da branch contra a main e propõe (e grava) a entrada do changelog do cliente final em changelog.ts.
---

Gere a entrada de changelog do cliente final para a branch atual.

Passos:
1. Rode em paralelo: `git log origin/main..HEAD --oneline` e `git diff origin/main...HEAD --stat`; leia os diffs o suficiente para entender o que muda **para o usuário final**.
2. Decida se vale entrada. Só entra o que o cliente percebe (tela/fluxo novo, comportamento novo, bug corrigido que ele via). Refactor, testes, infra, docs, specs e mudança interna NÃO entram — se for só isso, diga "sem entrada de changelog" e pare.
3. Escreva a entrada em PT-BR, sem jargão técnico (nada de nome de classe, endpoint, "serviço", Kafka), título curto e descrição de 1–2 frases dizendo o benefício. Tipo: `novo`, `melhoria` ou `correcao`.
4. Adicione ao **topo** do array `CHANGELOG` em `Angular/erp-front-end-web/src/app/pages/novidades/changelog.ts`, com `data` = hoje (yyyy-MM-dd). Se já houver entrada desta mesma mudança, ajuste em vez de duplicar.
5. NÃO rode git add/commit/push nem build. Mostre a entrada gravada para o usuário revisar.

---
description: Analisa toda a working tree (staged + unstaged + untracked) e gera uma mensagem de commit consolidada, pronta pra copiar e colar.
---

Analise o estado atual da working tree e gere UMA mensagem de commit consolidada cobrindo TODAS as alterações pendentes (staged, unstaged e untracked).

Passos:
1. Rode em paralelo: `git status`, `git diff`, `git diff --staged`, e `git log -5 --oneline` (para seguir o estilo de mensagens já usado no repo).
2. Leia os diffs o suficiente para entender a natureza de cada mudança (fix/feat/refactor/chore/docs) — não assuma pelo nome do arquivo.
3. Agrupe por área/arquivo e escreva a mensagem seguindo o formato definido no CLAUDE.md do projeto:

```
<type>: <short description>

- bullet resumindo cada arquivo/área alterada
```

Use `type` = fix (bug/segurança), feat (funcionalidade nova), refactor, chore (config/tooling) ou docs, escolhendo o que predominar. Se houver múltiplos tipos de mudança sem um claramente dominante, diga isso e sugira quebrar em mais de um commit, mas ainda assim entregue a mensagem consolidada.

4. NÃO rode `git add`, `git commit`, nem nenhum comando que altere o estado do repo — isso é só para gerar o texto.
5. Ao final, apresente a mensagem dentro de um bloco de código (markdown fence), pronta para copiar e colar, e nada mais precisa ser feito depois disso.
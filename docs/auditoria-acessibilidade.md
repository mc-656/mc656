# Auditoria de Acessibilidade — WCAG 2.1 Nível AA (RNF-07, #140)

Este documento registra os critérios de acessibilidade implementados e verificados automaticamente na interface web do sistema **Decisão Coletiva**.

## Critérios atendidos

1. **Idioma e título de página (SC 3.1.1 / SC 2.4.2):**
   - Todos os templates herdam `layout.html` com `<html lang="pt-BR">` e `<title>` descritivo único via `layout:title-pattern="$CONTENT_TITLE · Decisão Coletiva"`.
2. **Ignorar blocos e foco por teclado (SC 2.4.1 / SC 2.4.7 / SC 2.1.1):**
   - `layout.html` inclui o link de salto `<a class="pular-conteudo" href="#conteudo-principal">Ir para o conteúdo principal</a>`, visível ao receber foco de teclado.
   - Todo `<main layout:fragment="conteudo">` define `id="conteudo-principal"` e `tabindex="-1"`, evitando que a substituição de fragmento do Thymeleaf Layout Dialect quebre o alvo do link de salto.
   - `app.css` define `:focus-visible` explícito (`outline: 3px solid var(--foco); outline-offset: 2px;`) para links, botões, campos e controles interativos.
3. **Contraste de cores (SC 1.4.3):**
   - Variáveis de cor em `app.css`: texto principal `#1f2328` sobre `#ffffff`/`#f5f6f8` (> 14:1), texto secundário `--suave` ajustado para `#4d5762` (> 6,5:1), ação primária `#0b5cad` com texto branco (> 6,7:1) e estados de erro `#b42318` sobre `#fdecea`.
4. **Estrutura semântica e hierarquia de cabeçalhos (SC 1.3.1 / SC 2.4.6):**
   - Landmarks `<header>`, `<nav aria-label="...">`, `<main id="conteudo-principal">` e `<footer>`.
   - Apenas um `<h1>` por página e progressão contínua de cabeçalhos sem saltos de nível (`h1 -> h2 -> h3`).
   - Tabelas utilizam `<caption class="sr-only">` e atributos `scope="col"` / `scope="row"`.
5. **Formulários, rótulos e mensagens de erro (SC 1.3.1 / SC 3.3.1 / SC 3.3.2):**
   - Campos possuem `<label for="...">` correspondente ou `aria-label` explícito.
   - Erros de validação exibem `role="alert"` e associação via `aria-invalid` e `aria-describedby` nos formulários de cadastro, criação de votação e configuração de regras.

## Verificação automatizada

A suíte `AcessibilidadeViewsTest` valida a renderização HTML das páginas públicas (`/login`, `/cadastro`, `/eleicoes-publicas`, `/eleicoes-publicas/{id}`, `/eleicoes-publicas/candidatos/{id}`, `/eleicoes-publicas/{id}/comparar`, `/eleicoes-publicas/{id}/conteudos`, `/eleicoes-publicas/cargos`, `/eleicoes-publicas/tipos-voto`) e autenticadas (`/`, `/votacoes`, `/votacoes/nova`, `/comprovantes/verificar`), garantindo que nenhuma regressão quebre os requisitos básicos de WCAG 2.1 AA.

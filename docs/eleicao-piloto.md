# Planejamento da Eleição Piloto com Parceiro Institucional (M6 — #141)

Este documento consolida o planejamento operacional, técnico e jurídico-administrativo para a execução da eleição piloto da Plataforma de Decisão Coletiva junto a um parceiro institucional real (SPECS §2, §9 — **M6**, issue `#141`), além de disponibilizar o modelo de relatório pós-piloto para retroalimentação do backlog (`#142`).

> **Dependências de execução:** o piloto em ambiente real pressupõe o ciclo completo de eleição privada até `Homologada` (`M2` concluído via PRs `#174`, `#179` e `#180`) e a disponibilização da aplicação em ambiente acessível aos eleitores (`#139`).

---

## 1. Contexto, Parceiro-Alvo e Termo de Acordo

Conforme a seção §2 e §9 da `SPECS.md`, o módulo **Eleições Privadas** tem como parceiros institucionais prioritários **Centros Acadêmicos (CAs)** e **condomínios (síndicos/administradoras)**.

### 1.1 Perfil recomendado

- **Perfil primário recomendado — Centro Acadêmico (CA) / Entidade Estudantil:**
  - *Cenário típico:* eleição de chapa para diretoria anual ou deliberação em assembleia geral estudantil.
  - *Por que priorizar:* público habituado à autenticação web, ciclo curto de mobilização (48h–72h), quórum verificável e baixo atrito operacional para coleta de feedback estruturado (`#142`).
- **Perfil alternativo — Assembleia de Condomínio:**
  - *Cenário típico:* escolha de síndico/conselho fiscal ou aprovação de obra/rateio por fração ideal (`votoPonderado = true`).
  - *Cuidado adicional:* exige pré-cadastro integral das unidades com os respectivos pesos antes da publicação e acompanhamento mais próximo para moradores com menor familiaridade digital.

### 1.2 Quadro de confirmação do parceiro (preenchimento na assinatura do piloto)

| Campo | Registro acordado |
|---|---|
| Instituição parceira | `[Nome do Centro Acadêmico ou Condomínio]` |
| Tipo de organização | `[ ] Centro Acadêmico` / `[ ] Condomínio` / `[ ] Outro` |
| Responsável institucional (nome e cargo) | `[Nome — Síndico / Presidente / Comissão Eleitoral]` |
| E-mail e telefone de plantão | `[contato@parceiro.org / +55 (...)]` |
| Tipo de votação (`TipoVotacao`) | `ELEICAO_PRIVADA` (ou `ORCAMENTO_PARTICIPATIVO`, se aplicável) |
| Modelo ou conjunto de regras escolhido | `[ELEICAO_CHAPA / ASSEMBLEIA / DECISAO_SIM_NAO / Customizado]` |
| Universo estimado de eleitores aptos | `[N eleitores]` |
| Janela acordada (início e fim UTC-3) | `[DD/MM/AAAA HH:mm — DD/MM/AAAA HH:mm]` |
| Canal de confirmação por escrito | `[Ata / E-mail institucional anexado na issue #141]` |

---

## 2. Governança, Privacidade (LGPD), Sigilo e Auditoria

### 2.1 Minimização e proteção de dados pessoais (`Usuario`)

- A plataforma armazena em `Usuario` apenas os dados estritamente necessários para autenticação única, elegibilidade e auditoria cadastral: `nome`, `cpf` (identificador único com 11 dígitos), `email`, `senhaHash` (hash BCrypt; senhas em texto plano nunca são persistidas) e `dataNascimento`.
- Nenhum dado pessoal é exibido no resultado público da apuração ou associado à escolha de voto.
- O acesso à lista nominal de eleitores aptos (`GET /api/votacoes/{votacaoId}/eleitores` e `/votacoes/{votacaoId}/eleitores`) é restrito exclusivamente ao `ADMIN_VOTACAO` daquela votação (`EleitorController`).

### 2.2 Isolamento do sigilo do voto (`RNF-02`)

- **Separação física no modelo de dados:**
  - `Participacao` registra apenas que um `Usuario` exerceu o direito de voto em uma `Votacao` (chave única por usuário + votação, impedindo voto duplicado — RF-14). Não guarda a opção votada.
  - `Cedula` registra apenas a opção escolhida e o peso aplicado, **sem FK para `Usuario` ou `Participacao`**, sem ordem sequencial compartilhada e sem timestamp correlacionável.
- Durante `EmVotacao`, inclusive o `ADMIN_VOTACAO` acompanha apenas métricas agregadas de comparecimento e status do quórum (RF-40), nunca contagens parciais por chapa/opção.
- **Risco residual aceito (`SPECS.md` §4.1):** em votações com `votoPonderado = true`, se um único eleitor possuir um peso exclusivo que não se repete no colégio eleitoral, a cédula pode ser inferida pelo campo de peso. Para condomínios com frações ideais únicas, recomenda-se informar explicitamente essa limitação estatutária no edital de convocação ou optar por voto unitário (`votoPonderado = false`) quando o regimento permitir.

### 2.3 Comprovante anônimo verificável (`RNF-01`)

- Conforme documentado em [`comprovante-anonimo.md`](comprovante-anonimo.md), imediatamente após registrar o voto o eleitor recebe um código criptografado anônimo (entregue uma única vez na interface via flash attribute, sem reemissão ao atualizar a página).
- O eleitor pode conferir a autenticidade do registro em `/comprovantes/verificar` (ou `POST /api/comprovantes/verificacao`). A verificação retorna apenas `votacaoId`, `emitidoEm` e `situacaoApuracao`, sem jamais revelar candidato, opção ou ID interno da cédula.

### 2.4 Rastreabilidade e auditoria de transições (`RNF-03`)

- Toda mudança de estado da `Votacao` passa exclusivamente por `VotacaoStateService` e é registrada na tabela `transicoes_votacao`, contendo estado de origem, estado de destino, origem (`USUARIO` ou `SISTEMA`), autor (nulo quando automática por data), data/hora e motivo.
- É proibido alterar estados diretamente no banco de dados (`runbook-contingencia.md` §4), sob pena de invalidar a auditoria e contornar as guardas de domínio.

---

## 3. Mapeamento das Regras Reais para `ModeloVotacao` e `RegraVotacao`

### 3.1 Tabela de mapeamento (RF-11, RF-16)

| Cenário real do parceiro | Modelo sugerido (`ModeloVotacao`) | Configuração em `RegraVotacao` | Observações operacionais |
|---|---|---|---|
| Eleição de Diretoria de CA (vence a chapa mais votada) | `ELEICAO_CHAPA` | `tipoMaioria = MAIORIA_SIMPLES`, `percentualQualificado = null`, `segundoTurno = false`, `quorumMinimoPercentual = null`, `votoPonderado = false`, `tipoDesempate = NENHUM` (ou critério do estatuto) | Opções cadastradas manualmente pelo admin em `RascunhoVotacao`; regras podem ser ajustadas após criação do modelo (ex.: incluir `quorumMinimoPercentual`). |
| Eleição de CA ou Síndico com exigência de > 50% e eventual 2º turno | Customizado (a partir de rascunho ou editando modelo) | `tipoMaioria = MAIORIA_ABSOLUTA`, `percentualQualificado = null`, `segundoTurno = true`, `quorumMinimoPercentual` conforme estatuto, `votoPonderado = false`, `tipoDesempate` conforme estatuto | Se nenhuma opção superar 50% dos votos válidos ponderados, o sistema origina nova `Votacao` derivada em `RascunhoVotacao` com os 2 finalistas, mesmos eleitores/pesos e `segundoTurno = false`. |
| Assembleia Estudantil ou Condominial com quórum mínimo de 50% | `ASSEMBLEIA` | `tipoMaioria = MAIORIA_SIMPLES`, `percentualQualificado = null`, `segundoTurno = false`, `quorumMinimoPercentual = 50`, `votoPonderado = false`, `tipoDesempate = NENHUM` | O quórum mínimo é calculado por **eleitor único** (e não pela soma de pesos), conforme `SPECS.md` §4.1. Se não atingir 50% ao fim da janela, `Apuracao → Anulada` ocorre automaticamente. |
| Reforma estatutária / destituição com maioria qualificada (ex.: 2/3) | Customizado | `tipoMaioria = MAIORIA_QUALIFICADA`, `percentualQualificado = 66.67` (entre `> 50` e `<= 100`), `segundoTurno = false/true`, `quorumMinimoPercentual` conforme estatuto | Regras de `RegraVotacao` exigem `percentualQualificado` estritamente maior que `50` e no máximo `100`. |
| Consulta rápida de pauta única ("Aprova prestação de contas?") | `DECISAO_SIM_NAO` | `tipoMaioria = MAIORIA_SIMPLES`, opções padrão `["Sim", "Não"]`, `quorumMinimoPercentual = null`, `votoPonderado = false` | Pronto para publicar após definir janela e eleitores. |
| Assembleia Condominial por fração ideal | Customizado | `tipoMaioria = MAIORIA_SIMPLES` ou `MAIORIA_QUALIFICADA`, `votoPonderado = true`, `quorumMinimoPercentual` conforme convenção | **Regra crítica:** a entrada por link de convite (`RF-17`) atribui peso `1`; quando `votoPonderado = true`, o administrador **deve cadastrar os eleitores com seus pesos individuais** em `RascunhoVotacao` (`/votacoes/{id}/eleitores` ou `POST /api/votacoes/{votacaoId}/eleitores`). |

> **Nota sobre `TipoDesempate`:** os valores suportados atualmente em `RegraVotacao` são `NENHUM`, `ORDEM_ALFABETICA`, `IDADE` e `SORTEIO`. Caso o estatuto do parceiro adote critério baseado em idade ou antiguidade e o cadastro de chapas/candidatos (`CandidatoOuChapa`, campos `nome` e `descricao`) não possua atributo estruturado para tal, configure `ORDEM_ALFABETICA` ou `SORTEIO` (ou mantenha `NENHUM` para deliberação em assembleia presencial/novo pleito), registrando a lacuna no backlog.

### 3.2 Lacunas identificadas no levantamento e candidatas a Issues de backlog

Caso o parceiro escolhido exija algum dos pontos abaixo no seu estatuto, registrar issue vinculada antes da publicação:

1. **Importação em lote (CSV) de eleitores e pesos em `RascunhoVotacao`:** condomínios com dezenas/centenas de unidades ou CAs com lista oficial de matrícula demandam importação em lote em vez de inclusão unitária por formulário/API (`POST /api/votacoes/{votacaoId}/eleitores`).
2. **Distinção nativa de votos Brancos/Nulos:** estatutos que excluem brancos/nulos do cálculo de votos válidos hoje precisam usar uma opção explícita cadastrada em `CandidatoOuChapa` (ex.: `"Branco / Abstenção"`), que entra no denominador da apuração.
3. **Credencial estática para coleta no Prometheus (`/actuator/prometheus`):** hoje o scrape usa JWT com expiração configurada em `jwt.expiresIn` (120 minutos por padrão), exigindo job periódico de renovação (`runbook-contingencia.md` §1).
4. **Exportação formal de ata / boletim de urna em PDF:** contendo o resumo da `Apuracao` homologada e a trilha completa de `transicoes_votacao` assinável pela comissão eleitoral.

---

## 4. Estratégia de Convite e Habilitação de Eleitores (RF-02, RF-17)

1. **Colégio fechado com lista nominal conhecida (recomendado para CAs e condomínios):**
   - Utilizar **`ModoConvite.RESTRITO`**: somente usuários autenticados cujo e-mail conste na lista de convite conseguem ingressar como eleitor.
   - Gerar/alternar o convite via `PUT /api/votacoes/{votacaoId}/convite` e enviar convites via `POST /api/votacoes/{votacaoId}/convite/emails`.
   - Alternativamente, pré-cadastrar todos os eleitores (obrigatório quando houver pesos diferentes de `1`) ainda em `RascunhoVotacao` em `/votacoes/{votacaoId}/eleitores`.
2. **Adesão aberta controlada por divulgação interna:**
   - Utilizar **`ModoConvite.ABERTO`** apenas quando qualquer usuário autenticado que receber o link `GET /convites/{token}` (ou ler o QR Code gerado por `GET /api/votacoes/{votacaoId}/convite/qrcode`, servido com `Cache-Control: no-store`) puder se habilitar como eleitor.
3. **Janela de validade do convite (`AceitaConvites`):**
   - O ingresso por link/QR Code/API (`GET /convites/{token}` e `POST /api/convites/{token}/aceite`) funciona **exclusivamente** enquanto a votação estiver em `Publicada` ou `EmVotacao`.
   - Em estados posteriores (`Suspensa`, `Apuracao`, `Encerrada`, `Homologada`, `Anulada`, `Cancelada`), nenhum novo eleitor entra pelo link, preservando o denominador do quórum apurado.

---

## 5. Cronograma Operacional (`T-7d` a `T+2d`)

| Marco | Prazo | Responsáveis | Atividades e evidências |
|---|---|---|---|
| **D-7 (Acordo e Regras)** | `T-7 dias` | Equipe + Parceiro | Assinar acordo do piloto (§1.2), mapear estatuto para `RegraVotacao`, confirmar infraestrutura (`#139`), definir e-mail de serviço em `METRICAS_EMAILS` (`observabilidade.metricas.emails-autorizados`). |
| **D-5 (Configuração)** | `T-5 dias` | `ADMIN_VOTACAO` + Equipe | Criar votação em `RascunhoVotacao` (`/votacoes/nova`), configurar `RegraVotacao` (`/votacoes/{id}/regras`), cadastrar chapas/opções (`/votacoes/{id}/candidatos`), conferir pesos/eleitores se aplicável e realizar ensaio em ambiente de homologação. |
| **D-3 (Publicação e Convites)** | `T-3 dias` | `ADMIN_VOTACAO` | Executar transição `RascunhoVotacao → Publicada` (`POST /votacoes/{id}/publicar`), configurar `ModoConvite` (`PUT /api/votacoes/{votacaoId}/convite`), disparar links/QR Code e distribuir o Guia do Eleitor (§7.2). |
| **D-0 (Janela `EmVotacao`)** | `T-0` | Equipe (plantão) + `ADMIN_VOTACAO` | Validar abertura automática (`Publicada → EmVotacao`). Monitorar comparecimento/quórum via painel RF-40 e saúde via `/actuator/health/readiness` e `/actuator/prometheus`. Manter material de contingência física em sobreaviso (§6.2). |
| **D+0 fim (Apuração)** | Término da janela | Sistema + `ADMIN_VOTACAO` | Aguardar transição automática `EmVotacao → Apuracao`, validar quórum e resultado (`Apuracao → Encerrada` ou `Anulada` por falta de quórum). Caso haja 2º turno, publicar e divulgar a votação derivada gerada em `RascunhoVotacao`. |
| **D+1 (Homologação)** | `T+1 dia` | `ADMIN_VOTACAO` + Comissão | Analisar eventuais contestações usando comprovantes verificados (`/comprovantes/verificar`) e histórico `transicoes_votacao`; homologar (`Encerrada → Homologada`). |
| **D+2 (Retrospectiva)** | `T+2 dias` | Product Owner + Parceiro | Coletar formulário dos eleitores e entrevista com o administrador, preencher o Relatório de Execução (§8) e converter feedback em issues priorizadas (`#142`). |

---

## 6. Plano de Contingência Técnica e Protocolo de Votação em Papel (RNF-06)

### 6.1 Monitoramento e resposta a incidentes eletrônicos

O procedimento técnico completo segue o [`runbook-contingencia.md`](runbook-contingencia.md):

1. **Verificações contínuas durante `EmVotacao`:**
   - Saúde da instância e conexão com PostgreSQL: `GET /actuator/health/liveness` e `GET /actuator/health/readiness`.
   - Métricas autenticadas (`GET /actuator/prometheus` com conta autorizada em `METRICAS_EMAILS`): `votacao_em_votacao`, `votacao_votos_registrados_total`, `votacao_votos_falhas_total`, `votacao_transicoes_falhas_total` e saturação do pool Hikari.
   - Logs estruturados (`SPRING_PROFILES_ACTIVE=log-json`): correlacionar erros reportados por eleitores através do header `X-Correlation-Id`.
2. **Quando suspender (`EmVotacao → Suspensa`):**
   - `/actuator/health/readiness` com status `DOWN` por mais de 5 minutos durante janela ativa;
   - taxa persistente de erros 5xx ou falhas inesperadas no registro de voto;
   - suspeita de violação de integridade/sigilo ou erro material de regra constatado pela comissão.
   - Executar via API pelo `ADMIN_VOTACAO`:
     `POST /api/votacoes/{id}/transicoes` com `{"destino": "Suspensa", "motivo": "<descrição objetiva>"}`.
3. **Decisão pós-suspensão:**
   - **Retomar (`Suspensa → EmVotacao`):** quando a falha foi corrigida sem perda ou corrupção de cédulas; informar `novoFimEm` posterior e futuro quando necessário compensar tempo parado.
   - **Cancelar (`Suspensa → Cancelada`):** decisão irreversível adotada se houver dúvida sobre integridade dos votos, indisponibilidade prolongada irremediável ou acionamento da votação em papel.

### 6.2 Protocolo de contingência em cédula de papel

Se a indisponibilidade ultrapassar **30 minutos** em janela presencial/síncrona ou impedir a retomada segura antes do fim previsto pelo estatuto do parceiro, a comissão eleitoral deve acionar a votação em papel observando as 5 regras abaixo para evitar **voto duplo híbrido**:

1. **Bloqueio prévio da votação eletrônica:** suspender imediatamente (`Suspensa`) e, confirmada a migração para papel, transicionar para `Cancelada` com justificativa explícita em `transicoes_votacao`. **Nunca somar cédulas de papel a cédulas eletrônicas da mesma `Votacao`:** como a `Cedula` é anônima (`RNF-02`), embora `Participacao` liste quem votou, a transição para `Cancelada` garante anulação integral e limpa da sessão digital abortada (ou, alternativamente, a lista nominal de `Participacao` apenas pode servir para auditoria de comparecimento caso o estatuto permita complementação auditada — no piloto recomenda-se **descarte integral da sessão digital cancelada** e votação em urna única em papel).
2. **Caderno impresso prévio:** antes da abertura (`T-0`), o administrador deve imprimir/exportar a lista de eleitores aptos em `RascunhoVotacao`/`Publicada` para uso exclusivo da mesa em caso de emergência.
3. **Material de contingência pré-preparado:** manter disponíveis cédulas padronizadas com espaço de rubrica de 2 membros da comissão/mesários, urna opaca lacrada na presença de 2 testemunhas e atas de abertura/fechamento em papel.
4. **Identificação na mesa:** cada eleitor assina o caderno impresso com documento oficial com foto antes de depositar a cédula rubricada na urna.
5. **Comunicação e registro:** enviar aviso imediato pelos canais oficiais do parceiro informando o motivo do cancelamento eletrônico e o local/horário da urna física, registrando o incidente na seção §8 deste documento.

---

## 7. Guias de Orientação Rápida

### 7.1 Guia do Administrador da Votação (`ADMIN_VOTACAO`)

1. **Cadastro e criação:** acesse a plataforma, crie sua conta e entre em `/votacoes/nova` para escolher um modelo pronto (`ELEICAO_CHAPA`, `ASSEMBLEIA`, `DECISAO_SIM_NAO`) ou criar votação customizada com janela de início e fim.
2. **Regras e opções (enquanto em `RascunhoVotacao`):**
   - Revise maioria, segundo turno, quórum mínimo, voto ponderado e desempate em `/votacoes/{id}/regras`.
   - Cadastre todas as chapas, candidatos ou propostas em `/votacoes/{id}/candidatos` (ou `/votacoes/{id}/propostas` no Orçamento Participativo).
   - Se usar `votoPonderado = true` ou lista fechada pré-autorizada, adicione os eleitores (e pesos) em `/votacoes/{id}/eleitores`.
3. **Publicação e convites:**
   - Confira datas, regras e caderno impresso de contingência antes de clicar em **Publicar** (`POST /votacoes/{id}/publicar`).
   - Gere o link/QR Code de convite em modo `RESTRITO` (recomendado, enviando a lista de e-mails permitidos) ou `ABERTO`.
4. **Acompanhamento durante `EmVotacao`:**
   - Acompanhe o painel da votação (`/votacoes/{id}`) para monitorar total de participações, eleitores aptos e alcance do quórum (`RF-40`).
   - Em caso de falha grave, suspenda conforme §6 e avise a equipe técnica informando o `X-Correlation-Id`.
5. **Apuração e homologação:**
   - Após o encerramento automático (`EmVotacao → Apuracao`), verifique o resultado apurado (ou motivo de anulação por falta de quórum), publique para ciência da comunidade e execute a homologação (`Homologada`).

### 7.2 Guia do Eleitor

1. **Conta na plataforma:** cadastre-se com seu nome completo, CPF válido, data de nascimento e e-mail institucional (use exatamente o mesmo e-mail cadastrado pelo organizador, caso a votação use convite `RESTRITO`).
2. **Aceitar o convite:** clique no link de convite (`/convites/{token}`) ou aponte a câmera para o QR Code divulgado pela organização. Se ainda não estiver logado, faça o login; a plataforma concluirá sua habilitação automaticamente após retornar ao convite.
3. **Registrar seu voto:**
   - Dentro da janela `EmVotacao`, acesse `/votacoes` e clique em **Votar** na votação ativa (`/votacoes/{id}/votar`).
   - Escolha sua opção (ou distribua seus pontos dentro do saldo, no Orçamento Participativo) e confirme.
4. **Guardar seu comprovante anônimo:**
   - Ao concluir, será exibido o **código do seu comprovante criptografado**. **Copie ou anote esse código imediatamente:** ele aparece uma única vez e não revela sua escolha para ninguém.
5. **Conferir a inclusão na apuração:**
   - A qualquer momento (e após a apuração), acesse `/comprovantes/verificar` autenticado e cole seu código para confirmar que sua cédula foi registrada e contabilizada na votação.

---

## 8. Template de Relatório de Execução do Piloto e Conversão em Backlog (`#142`)

> Preencher após a conclusão da eleição piloto (`Homologada` ou `Anulada`/`Cancelada`) e anexar o resultado na issue `#142`.

### 8.1 Resumo executivo da execução

- **ID / Título da votação piloto:** `[UUID — Nome]`
- **Parceiro institucional:** `[Nome]`
- **Estado final alcançado:** `[ ] Homologada` / `[ ] Anulada (motivo: ...)` / `[ ] Cancelada (motivo: ...)`
- **Total de eleitores aptos:** `[N]`
- **Participações registradas (`Participacao`):** `[K]` (**Comparecimento:** `[K/N %]`)
- **Quórum mínimo exigido vs. atingido:** `[X %] / [Y %]`
- **Disponibilidade observada durante `EmVotacao`:** `[100% ou detalhar janelas de degradação]`
- **Incidentes e `X-Correlation-Id` registrados:** `[Nenhum / Listar ocorrências, duração e desfecho]`

### 8.2 Roteiro de feedback (eleitores e administrador)

**Formulário curto para eleitores (escala 1–5 + campo aberto):**
1. Facilidade para criar conta, aceitar o convite e acessar a cédula (`1` muito difícil – `5` muito fácil).
2. Clareza das regras exibidas e confiança no sigilo do voto (`1` – `5`).
3. Experiência com o comprovante anônimo e verificação em `/comprovantes/verificar` (`1` – `5`).
4. O que poderia ser mais simples ou mais claro na próxima eleição?

**Roteiro de conversa pós-piloto com o `ADMIN_VOTACAO`:**
1. O processo de configuração (`RascunhoVotacao`), cadastro de opções e disparo de convites atendeu ao estatuto sem retrabalho?
2. Quais dados faltaram no painel de comparecimento (`RF-40`) ou na página de resultado homologado?
3. Algum eleitor relatou bloqueio indevido de elegibilidade, demora ou dúvida crítica?

### 8.3 Tabela de conversão do feedback em backlog (Product Owner)

| ID do achado | Origem (`Admin` / `Eleitor` / `Ops`) | Descrição da melhoria ou defeito | Impacto para o parceiro (`Alto/Médio/Baixo`) | Issue criada no GitHub | Milestone / Prioridade |
|---|---|---|---|---|---|
| `F-01` | `[Origem]` | `[Descrição objetiva]` | `[Alto/Médio/Baixo]` | `#...` | `[M... / P...]` |
| `F-02` | `[Origem]` | `[Descrição objetiva]` | `[Alto/Médio/Baixo]` | `#...` | `[M... / P...]` |

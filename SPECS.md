# SPECS.md — Plataforma de Decisão Coletiva

> Disciplina MC426/MC656 — Engenharia de Software
> Documento vivo: atualizar a cada milestone concluído. Baseado em `Atividade_1.pdf` (Definição do Processo de Desenvolvimento) e na arquitetura padrão Spring Boot.

## 1. Visão do Produto

Plataforma web que ajuda eleitores a compreender e acompanhar o processo eleitoral e, ao mesmo tempo, permite que qualquer usuário crie e conduza suas próprias votações, com regras configuráveis.

O sistema cobre três domínios de decisão:

| Módulo | Descrição | Exemplo de uso |
|---|---|---|
| **Eleições Públicas** | Conteúdo informativo/didático sobre eleições brasileiras reais (candidatos, propostas, agenda de aparições, histórico, explicação do sistema eleitoral). Não apura votos oficiais. | Eleição presidencial, eleição de deputados |
| **Eleições Privadas** | Criação de votações próprias com regras configuráveis: maioria simples, segundo turno, voto ponderado, quórum mínimo. | Assembleia de condomínio, eleição de Centro Acadêmico |
| **Orçamento Participativo** | Distribuição de pontos/orçamento entre propostas, com validação de gastos e apuração por cotas. | Priorização de investimentos coletivos |

### 1.1 Problema a resolver

Eleitores frequentemente não entendem as regras do processo eleitoral (ex.: um deputado pode se eleger com menos votos que outro não eleito, por causa do quociente eleitoral). O produto reduz essa distância com conteúdo didático e, adicionalmente, generaliza o mecanismo de "votação com regras" para uso privado.

## 2. Stakeholders

Partes interessadas no projeto, além dos atores que operam o sistema diretamente (seção 3).

| Stakeholder | Papel | Interesse principal |
|---|---|---|
| **Equipe de desenvolvimento** (Miguel Pereira Ramos, Caio Maia Moreira Santos, Vinícius Maciel de Sousa, Fernando Nagano Foschiera, Lucas Gugel Maciel) | Implementa, testa, mantém o produto | Entregar incrementos funcionais a cada sprint; nota da disciplina MC656 |
| **Product Owner** (papel rotativo dentro da equipe) | Prioriza backlog, decide escopo de cada sprint | Priorização guiada por valor gerado ao cliente (seção 7/2.3 do doc base) |
| **Parceiros institucionais** (síndicos/administradoras de condomínio, Centros Acadêmicos) | Usuários-piloto do módulo Eleições Privadas em ambiente real | Validar que o sistema atende necessidades reais de votação (BizDev, M6) |
| **Eleitores finais** (moradores, alunos, eleitor público) | Usam o sistema para votar ou se informar | Confiabilidade, sigilo do voto, clareza das regras |
| **Administrador da Votação** | Cria e configura votações privadas | Flexibilidade de regras, confiança no resultado apurado |
| **Docente/avaliador da disciplina MC426/MC656** | Avalia processo e entregas | Aderência a boas práticas de Engenharia de Software e Requisitos |
| **Mantenedores da infraestrutura** (GitHub Actions, hospedagem) | Operam CI/CD e ambiente de produção | Pipeline estável, DevSecOps aplicado (seção 2.2 do doc base) |

Conflitos de interesse a observar: parceiros institucionais pressionam por regras de votação muito específicas (ex.: pesos customizados) que podem inflar escopo — priorização do Product Owner deve equilibrar isso contra o cronograma de milestones (seção 9).

## 3. Atores

Derivados do Diagrama de Casos de Uso (Figura 1 do documento base):

- **Administrador da Votação** — cria votações privadas, configura pesos e quórum.
- **Morador do Condomínio** — perfil de eleitor privado (caso de uso: assembleia).
- **Aluno / Membro do CA** — perfil de eleitor privado (caso de uso: eleição de Centro Acadêmico).
- **Eleitor Público** — consulta informações e notícias sobre eleições públicas; não vota oficialmente no sistema.

Casos de uso centrais compartilhados entre perfis: `Validar Elegibilidade e Login`, `Votar em Candidatos/Chapas`, `Distribuir Pontos no Orçamento Participativo`, `Acompanhar Resultados da Apuração`.

## 4. Modelo de Domínio

### 4.1 Entidades principais

- **Usuario** — identidade, credenciais, papéis (`ADMIN_VOTACAO`, `ELEITOR`).
- **Votacao** — agregado raiz de uma eleição privada ou orçamento participativo. Contém tipo (`ELEICAO_PRIVADA` | `ORCAMENTO_PARTICIPATIVO`), regras, datas de início/fim, estado.
- **RegraVotacao** — value object: tipo de maioria (simples/qualificada), segundo turno (sim/não), pesos por eleitor, quórum mínimo, orçamento total (quando aplicável).
- **CandidatoOuChapa** — opção votável em eleição privada.
- **Proposta** — item elegível a receber pontos em orçamento participativo.
- **Voto** — registro imutável de participação de um `Usuario` em uma `Votacao` (voto único ou distribuição de pontos), com comprovante criptografado.
- **Apuracao** — snapshot do resultado computado (pesos, cotas, quórum atingido/não atingido).
- **CandidatoPublico / EleicaoPublica** — dados informativos (propostas, agenda, notícias) do módulo público, sem lógica de apuração.

### 4.2 Máquina de estados de `Votacao`

Baseada na Figura 2 (diagrama de estados):

```
RascunhoVotacao
  --[Administrador finaliza regras]--> Publicada
  <--[Revisão ou ajuste necessário]--

Publicada
  --[Data de início atingida]--> EmVotacao
  --[Administrador cancela antes do início]--> Cancelada

EmVotacao (composto)
  RecebendoVotos
    --[Orçamento Participativo, voto processado]--> ValidandoCotas --> RecebendoVotos
    --[Condomínio/regra ponderada, voto processado]--> ComputandoPesos --> RecebendoVotos
  --[Intervenção administrativa/técnica]--> Suspensa --[Votação retomada]--> EmVotacao
  --[Cancelamento forçado]--> Cancelada
  --[Data de fim atingida]--> Apuracao

Apuracao
  --[Regras validadas]--> Encerrada --[Resultado publicado e aceito]--> Homologada (final)
  --[Falta de quórum / irregularidade]--> Anulada (final)

Cancelada (final)
```

Implementação sugerida: enum `EstadoVotacao` + padrão State (ou guard clauses no service) para impedir transições inválidas. Toda transição deve ser auditável (quem, quando, motivo).

### 4.3 Fluxo de atividade (login → voto)

Baseado na Figura 3: login → seleção de intenção (`Participar de Votação` | `Criar Votação` | `Acompanhar Eleições Públicas`) → checagem de elegibilidade → carregamento de regras → ramificação por tipo de regra (orçamento participativo vs. voto padrão/ponderado) → validação (saldo de pontos / quórum) → geração de comprovante criptografado do voto.

## 5. Requisitos Funcionais

Identificadores `RF-xx`, agrupados por módulo. Cada um deve virar Issue no GitHub Projects vinculada a um Milestone.

### 5.1 Conta e Acesso
- **RF-01** Cadastro e autenticação de usuário.
- **RF-02** Validação de elegibilidade de um usuário para uma `Votacao` específica (ex.: pertence ao condomínio, é aluno matriculado).
- **RF-03** Perfis/papéis: eleitor, administrador de votação.

### 5.2 Eleições Privadas
- **RF-10** Criar votação personalizada (nome, descrição, datas, elegíveis).
- **RF-11** Configurar regras: maioria simples/qualificada, segundo turno, quórum mínimo, pesos por eleitor.
- **RF-12** Cadastrar candidatos/chapas.
- **RF-13** Registrar voto único por eleitor elegível, com comprovante.
- **RF-14** Impedir voto duplicado e voto fora da janela `EmVotacao`.
- **RF-15** Apurar resultado respeitando regra configurada (incl. segundo turno quando necessário).

### 5.3 Orçamento Participativo
- **RF-20** Cadastrar propostas e definir orçamento total (limite de pontos).
- **RF-21** Permitir que o eleitor distribua pontos entre propostas até o limite do seu saldo.
- **RF-22** Validar que a soma alocada não excede o saldo do eleitor (bloqueio + alerta).
- **RF-23** Apurar por cotas: ranquear propostas até o limite do orçamento total disponível.

### 5.4 Eleições Públicas (informativo)
- **RF-30** Listar eleições públicas ativas/futuras (ex.: presidencial, legislativo).
- **RF-31** Exibir perfil de candidato: propostas, agenda de aparições, histórico.
- **RF-32** Conteúdo didático sobre o sistema eleitoral (ex.: quociente eleitoral).
- **RF-33** Consultar notícias/resumos de debates relacionados.

### 5.5 Apuração e Acompanhamento
- **RF-40** Acompanhar resultado da apuração em tempo real (ou near-real-time) durante `EmVotacao`.
- **RF-41** Publicar e homologar resultado final.
- **RF-42** Anular votação por falta de quórum ou irregularidade, com justificativa registrada.

## 6. Requisitos Não Funcionais

| ID | Requisito | Racional |
|---|---|---|
| RNF-01 | Comprovante de voto criptografado e verificável | Integridade e confiança no processo, especialmente em votações sensíveis (CA, condomínio) |
| RNF-02 | Sigilo do voto individual, mesmo para o Administrador da Votação | Evitar coerção; administrador vê apenas agregados |
| RNF-03 | Toda transição de estado de `Votacao` deve ser auditável | Rastreabilidade e defesa contra contestação de resultado |
| RNF-04 | Pipeline de CI/CD com verificação de vulnerabilidades, linting e análise estática em cada PR | Cultura DevSecOps definida no processo (seção 2.2 do doc base) |
| RNF-05 | Testes automatizados cobrindo regras de apuração (maioria, segundo turno, quórum, cotas) | Lógica de negócio crítica e propensa a erro sutil |
| RNF-06 | Disponibilidade do sistema durante janelas de votação ativa | Falha durante `EmVotacao` invalida o processo |
| RNF-07 | Interface didática e acessível para o módulo de Eleições Públicas | Público-alvo inclui eleitores leigos no sistema eleitoral |
| RNF-08 | Migrações de banco versionadas e reprodutíveis | Suporta `Gerenciamento de Configuração` (Tabela 1 do doc base) |

## 7. Arquitetura

### 7.1 Stack

- **Backend:** Java + Spring Boot (Spring Web, Spring Data JPA, Spring Security, Bean Validation).
- **Banco de dados:** relacional (PostgreSQL em produção); migrações via Flyway (`src/main/resources/db/migration`, já presente no repositório).
- **View:** Thymeleaf server-side (`src/main/resources/templates`) para MVP; possibilidade de expor API REST para um front-end separado em fase posterior.
- **CI/CD:** GitHub Actions (build, testes, lint, scan de segurança, deploy).
- **Gerenciamento de projeto:** GitHub Projects (Kanban) + Issues + Milestones.

### 7.2 Organização de pacotes (arquitetura em camadas padrão Spring Boot)

```
com.unicamp.engsoft.eleicao
├── config          # Spring Security, beans, configurações gerais
├── votacao
│   ├── controller   # REST/MVC controllers
│   ├── service      # regras de negócio, máquina de estados
│   ├── repository   # Spring Data JPA
│   ├── domain        # entidades JPA, enums (EstadoVotacao), value objects
│   └── dto           # request/response
├── usuario
│   ├── controller
│   ├── service
│   ├── repository
│   └── domain
├── eleicaopublica
│   ├── controller
│   ├── service
│   ├── repository
│   └── domain
└── shared
    ├── exception      # exceptions e handler global (@ControllerAdvice)
    └── security        # criptografia de comprovante, utilitários
```

Pacote por *feature* (votacao, usuario, eleicaopublica), camadas dentro de cada feature — evita pacotes gigantes tipo `controllers/`, `services/` compartilhados entre domínios não relacionados.

Pacote base: `com.unicamp.engsoft.eleicao`, conforme o projeto inicializado no repositório.

### 7.3 Decisões arquiteturais chave

- **Máquina de estados isolada no `service`**: transições de `EstadoVotacao` centralizadas em um único ponto (ex.: `VotacaoStateService`), nunca alteradas diretamente pelo controller ou repository.
- **Separação apuração pública vs. privada**: `eleicaopublica` é somente leitura/conteúdo, nunca compartilha tabelas de voto com `votacao` (dados públicos não sofrem as mesmas exigências de sigilo/apuração).
- **Voto como registro append-only**: sem update/delete de `Voto`; correção de erro é nova regra de negócio explícita, não edição de dado histórico.

## 8. Processo de Desenvolvimento

Definido no documento base (seção 2) — resumo operacional:

- **Sprint semanal** com *weekly* de alinhamento.
- **Kanban** (Backlog → Ready → In Progress → In Review → Done) via GitHub Projects.
- Tarefas = **Issues**; agrupamento de metas amplas = **Milestones**.
- Cada PR passa por: testes automatizados, lint, análise estática, scan de segurança (GitHub Actions) → code review por pares → merge → deploy automatizado.
- **BizDev em paralelo**: validação do módulo "Eleições Privadas" com parceiros reais (condomínios, Centros Acadêmicos) alimenta priorização do backlog (Product Owner usa feedback como insumo principal).

## 9. Milestones

Roadmap de alto nível. Cada milestone deve ser criado no GitHub com Issues vinculadas.

### M0 — Fundação do projeto
- Setup Spring Boot, estrutura de pacotes, Flyway configurado, pipeline CI básico (build + testes).
- Modelo de domínio inicial (`Usuario`, `Votacao`, `EstadoVotacao`).
- Configuração do repositório e Integração Contínua (checklists abaixo).
- **Critério de saída:** aplicação sobe localmente, migração inicial roda, CI verde, repositório com regras de proteção ativas.

#### Configuração do repositório

- Adicionar como colaboradores: professor responsável (`cafeo-unicamp`) e o PED da disciplina (`Andre-Satorres`), com acesso suficiente para consultar código, Pull Requests e execuções do pipeline, e para criar branches/PRs de teste quando necessário.
- Configurar regras de proteção — via **GitHub Rulesets** ou mecanismo equivalente — para as branches **main** e **develop**, exigindo no mínimo:
  - Pull Request obrigatório para integração de mudanças;
  - pelo menos uma aprovação de outro integrante da equipe;
  - resolução das discussões de revisão antes do merge;
  - conclusão bem-sucedida dos checks obrigatórios do pipeline antes do merge;
  - regras de proteção não puladas nem por usuários com permissão de administração;
  - force push bloqueado nas branches protegidas;
  - exclusão bloqueada das branches protegidas.

#### Integração Contínua

Pipeline de CI (GitHub Actions), executado automaticamente no fluxo de desenvolvimento, contendo no mínimo:

- **Build**
  - processo automatizado de construção/preparação do projeto (ferramenta conforme stack — Maven, no caso deste projeto);
  - instalação/resolução de dependências;
  - scripts/arquivos de configuração versionados no repositório;
  - projeto buildável a partir de um clone limpo do repositório.
- **Análise estática / lint**
  - ferramenta automatizada de análise estática/lint (ex.: Checkstyle, SpotBugs, ou equivalente para Java/Spring Boot);
  - configuração versionada no repositório;
  - execução automatizada no pipeline;
  - violações configuradas como erro devem falhar o pipeline (bloqueiam merge via regra de proteção da 9.1).

### M1 — Autenticação e cadastro de votação privada
- RF-01, RF-02, RF-03, RF-10, RF-11, RF-12.
- Máquina de estados `RascunhoVotacao → Publicada → EmVotacao` implementada e testada.
- **Critério de saída:** administrador consegue criar e publicar uma votação com regras básicas.

### M2 — MVP de Eleição Privada (voto e apuração)
- RF-13, RF-14, RF-15, RF-40, RF-41, RF-42.
- Comprovante criptografado (RNF-01), sigilo do voto (RNF-02).
- **Critério de saída:** ciclo completo de uma eleição de condomínio/CA, do rascunho à homologação.

### M3 — Orçamento Participativo
- RF-20, RF-21, RF-22, RF-23.
- **Critério de saída:** votação de orçamento participativo completa, com validação de saldo e apuração por cotas.

### M4 — Módulo Eleições Públicas (informativo)
- RF-30, RF-31, RF-32, RF-33.
- **Critério de saída:** conteúdo didático e perfis de candidatos públicos navegáveis.

### M5 — Hardening, DevSecOps e observabilidade
- RNF-04 a RNF-08 completos: scan de segurança e análise estática obrigatórios no pipeline, auditoria de transições, testes de regras de apuração com cobertura alta.
- **Critério de saída:** nenhum PR mescla sem passar por todas as verificações automatizadas.

### M6 — Piloto com parceiro real
- Validação de mercado (seção 2.3 do doc base): rodar uma eleição real de condomínio ou Centro Acadêmico na plataforma.
- **Critério de saída:** feedback do parceiro institucional coletado e transformado em itens de backlog priorizados.

## 10. Riscos e Mitigações

| Risco | Impacto | Mitigação |
|---|---|---|
| Regra de apuração implementada incorretamente (ex.: segundo turno, cotas) | Resultado de votação inválido, perda de confiança | Testes automatizados por regra + revisão por pares obrigatória em PRs que tocam `service` de votação |
| Falha do sistema durante `EmVotacao` | Votação pode precisar ser anulada | Monitoramento + estado `Suspensa` já modelado como transição de contingência |
| Voto duplicado ou fora de janela | Quebra de integridade do processo | Validação no `service`, não só na UI; constraint de unicidade no banco (usuário + votação) |
| Escopo amplo (3 módulos) atrasar entrega do core | Atraso geral do projeto | Milestones priorizam Eleições Privadas (M1-M2) antes de Orçamento Participativo (M3) e módulo Público (M4) |

## 11. Definition of Done (por Issue)

- Código implementado seguindo a organização de pacotes da seção 7.2.
- Testes automatizados cobrindo o caso de uso e casos de borda relevantes.
- PR aprovado em code review por pares.
- Pipeline CI (build, testes, lint, scan de segurança) verde.
- Migração de banco (se aplicável) versionada em `db/migration`.
- Issue vinculada ao Milestone correspondente, movida para `Done` no Kanban.

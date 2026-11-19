# Integração da apuração — issue #50

Ao transitar uma eleição privada de `EmVotacao` para `Apuracao`, o
`VotacaoStateService` publica um evento síncrono. O `ApuracaoService` participa da
mesma transação: carrega todas as cédulas, as opções (incluindo as sem votos), as
regras, a quantidade de participações e a quantidade de eleitores aptos, executa o
`MotorApuracao` e persiste o snapshot em `apuracoes`.

O snapshot contém a regra aplicada, o resultado completo (ranking, maioria,
pesos, comparecimento e quórum), o número de cédulas processadas, a data de
conclusão e, quando houver, o identificador do segundo turno. Não contém usuários
nem vínculo entre eleitor e cédula. A entidade é imutável no JPA e o repositório
não oferece operações de remoção. A publicação/homologação e a consulta pública
do resultado ficam para a #52.

## Atomicidade e repetição

- O lock exclusivo da votação espera votos em andamento, que usam lock
  compartilhado. Nenhum novo voto é aceito após a transição.
- Todas as cédulas da votação são processadas, sem filtros. Divergência entre
  quantidade de participações e cédulas, ou uma opção de outra votação, aborta a
  operação.
- A PK de `apuracoes.votacao_id` permite um snapshot por votação. Chamadas internas
  repetidas a `ApuracaoService.apurar(id)` retornam o snapshot existente, inclusive
  depois da anulação; não sorteiam novamente nem criam outro segundo turno.
- Estado, auditoria, snapshot, segundo turno e anulação fazem commit juntos. Se
  qualquer etapa falhar, tudo é revertido. Na falha da transição automática, a
  votação continua em `EmVotacao` com prazo vencido: o serviço de voto continua
  bloqueando votos pelo prazo, e o job pode tentar novamente.
- O método interno `apurar(id)` também permite processar eleições que já estavam
  em `Apuracao` antes desta integração. Não há endpoint de execução manual aberto
  aos usuários.

## Quórum e segundo turno

Quórum insuficiente preserva o snapshot e chama `AnulacaoVotacaoService`, que
transita para `Anulada` com origem `SISTEMA`, autor nulo e motivo
`quórum não atingido`. A auditoria registra a entrada em apuração e a anulação.

Quando o motor indica segundo turno, é criada uma nova votação:

- `votacao_origem_id` aponta para a original e é único;
- estado `RascunhoVotacao`, inicialmente sem datas;
- os dois finalistas são copiados com novos IDs, nome, descrição e nascimento;
- todos os papéis (administradores e eleitores) e pesos são copiados;
- regras iguais às originais, mas `segundoTurno = false`;
- nenhuma participação ou cédula é copiada.

O administrador define o calendário por
`PUT /api/votacoes/{id-do-segundo-turno}/periodo`:

```json
{
  "inicioEm": "2027-01-10T09:00:00Z",
  "fimEm": "2027-01-11T09:00:00Z"
}
```

A operação exige `ADMIN_VOTACAO`, só é permitida em rascunho e exige fim posterior
ao início. Também serve para ajustar o calendário de outros rascunhos. A publicação
usa o endpoint de transições existente e é bloqueada se as datas não estiverem
preenchidas corretamente. Sem quórum insuficiente, a votação original permanece
em `Apuracao`; as transições para `Encerrada` e `Homologada` pertencem à #52.

## Desempate

A integração seleciona a estratégia configurada: nenhum, ordem alfabética, idade
ou sorteio. Para idade, o cadastro de candidatos aceita o campo opcional
`dataNascimento` (`YYYY-MM-DD`), que deve ser uma data passada. Se a regra usar
idade, todas as opções precisam ter nascimento válido antes da publicação. O
comparador recebe a idade em dias na data de encerramento para distinguir pessoas
nascidas no mesmo ano. Não é inferida idade a partir de um usuário eleitor.

O sorteio acontece uma vez ao produzir o snapshot. Reconsultas usam o resultado
persistido. Orçamento participativo não executa esse motor: sua apuração por cotas
continua no escopo da #130.

## Integração posterior do comprovante (#83)

Um snapshot concluído é evidência de que **todas** as cédulas daquela votação
foram processadas, sob as invariantes acima. A #83 pode combinar a autenticação
do comprovante, a existência da cédula na votação e a existência desse snapshot
para confirmar inclusão. Não deve inferi-la apenas pelo estado da votação.

Uma cédula processada numa votação anulada não significa resultado válido ou
homologado. A verificação deve continuar informando a anulação. O comprovante e
sua resposta não devem expor opção, peso individual ou ID da cédula.

## Migração e validação

A migração `V20__integra_apuracao_e_segundo_turno.sql` acrescenta o snapshot, o
vínculo de origem e o nascimento da opção. Permite datas nulas somente para
votação derivada em rascunho. Foi usada uma nova versão após V19, em vez da reserva
V9 antiga da issue, para preservar bancos que já aplicaram a cadeia existente.

Os testes cobrem maiorias simples/absoluta/qualificada com votos reais, pesos,
opções com zero votos, quórum e auditoria, segundo turno, definição de período,
chamadas concorrentes, repetição, falha com rollback e recusa de dados inconsistentes.

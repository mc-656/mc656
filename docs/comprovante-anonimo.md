# Comprovante anônimo — issue #83

O voto devolve um código criptografado, sem identificador do usuário ou opção
votada. A verificação autentica o código e confirma a existência da cédula na
votação correspondente. Não devolve o identificador da cédula nem sua escolha.

## Contrato atual

`POST /api/comprovantes/verificacao`, com `{"comprovante": "<codigo>"}`, retorna:

```json
{
  "votacaoId": "<uuid>",
  "emitidoEm": "2026-11-16T12:00:00Z",
  "situacaoApuracao": "NAO_CONFIRMADA"
}
```

Uma resposta de sucesso confirma o registro da cédula. `situacaoApuracao` pode ser:

| Valor | Significado |
| --- | --- |
| `NAO_CONFIRMADA` | Não há confirmação de inclusão da cédula na apuração. |
| `INCLUIDA_NA_APURACAO` | A cédula é imutável e há snapshot persistido em `apuracoes` confirmando sua contagem. |
| `VOTACAO_ANULADA` | O registro continua verificável, mas a votação foi anulada. |
| `VOTACAO_CANCELADA` | O registro continua verificável, mas a votação foi cancelada. |

Códigos malformados, alterados, emitidos com outra chave ou referentes a cédulas
inexistentes retornam HTTP 400 com `detail: "Comprovante não encontrado."`.
A página `/comprovantes/verificar` apresenta a mesma mensagem. Campos vazios
continuam sendo tratados como erro de preenchimento.

O eleitor recebe o código imediatamente após votar. Na interface, o código é
entregue por flash attribute; atualizar a página não emite uma segunda via.
O eleitor deve guardar o código. A verificação continua exigindo autenticação.

## Garantia de inclusão na apuração

A apuração (`ApuracaoService`) roda de forma síncrona na mesma transação em que
a votação entra em `Apuracao`, lê **todas** as cédulas da votação sob lock de
escrita e persiste o snapshot em `apuracoes`:

1. Como as cédulas são imutáveis e a entrada de novos votos é encerrada antes
   da apuração, a existência do registro em `apuracoes` para a votação comprova
   que todas as cédulas válidas daquela votação integraram a totalização; caso a
   apuração falhe e sofra rollback, nenhum snapshot é gravado e o comprovante
   permanece em `NAO_CONFIRMADA`.
2. `RegistroVotoService.verificar` consulta essa evidência persistida em vez de
   inferir inclusão pelo estado da votação — estados `Apuracao`, `Encerrada` ou
   `Homologada` sem snapshot em `apuracoes` continuam retornando
   `NAO_CONFIRMADA`.
3. Se a votação for anulada (`Anulada`) ou cancelada (`Cancelada`), esse
   estado prevalece na resposta (`VOTACAO_ANULADA` / `VOTACAO_CANCELADA`), mesmo
   que o snapshot exista (caso típico da anulação automática por falta de
   quórum).
4. O sigilo (RNF-02) permanece preservado: nenhuma associação usuário–cédula,
   opção votada ou identificador interno de cédula é exposta pela verificação.

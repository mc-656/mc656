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
| `VOTACAO_ANULADA` | O registro continua verificável, mas a votação foi anulada. |
| `VOTACAO_CANCELADA` | O registro continua verificável, mas a votação foi cancelada. |

Códigos malformados, alterados, emitidos com outra chave ou referentes a cédulas
inexistentes retornam HTTP 400 com `detail: "Comprovante não encontrado."`.
A página `/comprovantes/verificar` apresenta a mesma mensagem. Campos vazios
continuam sendo tratados como erro de preenchimento.

O eleitor recebe o código imediatamente após votar. Na interface, o código é
entregue por flash attribute; atualizar a página não emite uma segunda via.
O eleitor deve guardar o código. A verificação continua exigindo autenticação.

## Dependência para concluir o aceite de inclusão na apuração

**A issue #83 ainda não atende integralmente o critério de comprovar que o voto
foi contado.** Nesta branch, o motor matemático existe, mas falta sua integração
com execução e persistência da apuração. Essa integração fica fora desta mudança.

Ao implementá-la:

1. Persistir o resultado e a evidência de quais cédulas foram processadas na mesma
   transação. Se todas as cédulas imutáveis forem obrigatoriamente processadas,
   documentar e garantir essa invariante para usar evidência por votação.
2. Consultar essa evidência em `RegistroVotoService.verificar` e adicionar um
   estado explícito de inclusão confirmada ao contrato e à tela.
3. Testar voto → código recebido → registro pendente → apuração efetiva →
   inclusão confirmada, incluindo falha/rollback da apuração e anulação posterior.
4. Preservar o sigilo: nenhuma associação usuário–cédula, opção votada ou ID de
   cédula deve ser exposta pela verificação.

Não inferir inclusão a partir dos estados `Apuracao`, `Encerrada` ou `Homologada`.
Os testes atuais verificam que esses estados isolados retornam `NAO_CONFIRMADA`.

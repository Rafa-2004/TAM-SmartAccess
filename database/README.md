# database

PostgreSQL (servidor da escola, gerido com pgAdmin4). Tudo fica no schema `tam`; as tabelas referem-se sempre como `tam.nome`.

Ordem de execucao no Query Tool do pgAdmin (base da conta de aluno):

1. `schema.sql`: cria o schema `tam`, as tabelas, indices e o trigger que torna `registos_acesso` imutavel. Pode ser executado varias vezes.
2. `functions.sql` e `procedures.sql`: funcoes (`fn_`) e procedimentos (`sp_`) usados pela API. Podem ser executados varias vezes.
3. `seed.sql`: horarios por omissao de cada perfil. Pode ser executado varias vezes.

O administrador nao e criado por SQL: no primeiro arranque da app aparece o ecra "Criar administrador"
(so pode existir um).

## Comecar do zero

`reset.sql` apaga **todos os dados** (pessoas, administrador, cartoes, portas, registos, aulas, alertas...),
mantendo a configuracao e os horarios por perfil. Depois, a app volta a pedir para criar o administrador
e os Arduinos registam-se de novo (aparecem "Por aprovar" em Gestao > Portas).

Nunca commitar dumps com dados reais nem palavras-passe de acesso a base.

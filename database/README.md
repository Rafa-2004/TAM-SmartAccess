# database

PostgreSQL (servidor da escola, gerido com pgAdmin4). Tudo fica no schema `tam`; as tabelas referem-se sempre como `tam.nome`.

Ordem de execucao no Query Tool do pgAdmin (base da conta de aluno):

1. `schema.sql`: cria o schema `tam`, as tabelas, indices e o trigger que torna `registos_acesso` imutavel. Pode ser executado varias vezes.
2. `seed.sql`: dados de teste (administrador, horarios por perfil, cartoes). Pode ser executado varias vezes.
   Mudar a palavra-passe do administrador antes da entrega.

Nunca commitar dumps com dados reais nem palavras-passe de acesso a base.

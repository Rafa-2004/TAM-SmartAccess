-- SmartAccess - APAGA TODOS OS DADOS (nao ha volta atras)
-- Apaga utilizadores (incluindo o administrador), cartoes, portas, registos, aulas, passagens, alertas e comandos.
-- Mantem a configuracao (tam.configuracao) e os horarios por perfil (tam.horarios_perfil).
--
-- Depois disto:
--   - ao abrir a app pela primeira vez aparece "Criar administrador";
--   - os Arduinos ligados recebem 401 (a chave deixou de existir), registam-se de novo
--     e aparecem como "Por aprovar" em Gestao > Portas.
--
-- O TRUNCATE nao passa pelo trigger registos_imutaveis (so trava UPDATE/DELETE linha a linha).

TRUNCATE TABLE
  tam.passagens,
  tam.aulas,
  tam.comandos,
  tam.alertas,
  tam.registos_acesso,
  tam.permissoes,
  tam.cartoes,
  tam.dispositivos,
  tam.utilizadores
RESTART IDENTITY;

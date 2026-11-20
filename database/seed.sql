-- Dados iniciais. Pode ser executado varias vezes.
-- O administrador NAO e criado aqui: e criado na app, no primeiro arranque ("Criar administrador").
-- Os dispositivos (Arduinos) tambem nao: registam-se sozinhos e sao aprovados na app.
-- Pessoas e cartoes criam-se na app (Gestao > Pessoas / Cartoes).

-- Horarios por omissao de cada perfil (o admin entra sempre)
--   docente:     segunda a sexta, 08:00-20:00
--   funcionario: segunda a sabado, 07:00-23:00
INSERT INTO tam.horarios_perfil (perfil, dia_semana, hora_inicio, hora_fim)
SELECT 'docente', dia, '08:00', '20:00' FROM generate_series(1, 5) AS dia
WHERE NOT EXISTS (SELECT 1 FROM tam.horarios_perfil WHERE perfil = 'docente');

INSERT INTO tam.horarios_perfil (perfil, dia_semana, hora_inicio, hora_fim)
SELECT 'funcionario', dia, '07:00', '23:00' FROM generate_series(1, 6) AS dia
WHERE NOT EXISTS (SELECT 1 FROM tam.horarios_perfil WHERE perfil = 'funcionario');

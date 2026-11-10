-- Dados iniciais para testes. MUDAR a palavra-passe do admin antes da entrega!
-- Os dispositivos (Arduinos) nao entram aqui: registam-se sozinhos e sao aprovados na app.
-- Pode ser executado varias vezes.

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- para gerar o hash bcrypt da palavra-passe

-- So o administrador tem conta na app
INSERT INTO tam.utilizadores (nome, email, password_hash, perfil) VALUES
  ('Administrador', 'admin@smartaccess.pt', crypt('admin123', gen_salt('bf', 10)), 'admin')
ON CONFLICT DO NOTHING;

-- Funcionarios e docentes sao so um nome associado ao cartao
INSERT INTO tam.utilizadores (nome, perfil)
SELECT nome, perfil FROM (VALUES ('Funcionario Teste', 'funcionario'), ('Docente Teste', 'docente')) AS n(nome, perfil)
WHERE NOT EXISTS (SELECT 1 FROM tam.utilizadores u WHERE u.nome = n.nome);

-- Horarios por omissao de cada perfil (o admin entra sempre)
--   docente:     segunda a sexta, 08:00-20:00
--   funcionario: segunda a sabado, 07:00-23:00
INSERT INTO tam.horarios_perfil (perfil, dia_semana, hora_inicio, hora_fim)
SELECT 'docente', dia, '08:00', '20:00' FROM generate_series(1, 5) AS dia
WHERE NOT EXISTS (SELECT 1 FROM tam.horarios_perfil WHERE perfil = 'docente');

INSERT INTO tam.horarios_perfil (perfil, dia_semana, hora_inicio, hora_fim)
SELECT 'funcionario', dia, '07:00', '23:00' FROM generate_series(1, 6) AS dia
WHERE NOT EXISTS (SELECT 1 FROM tam.horarios_perfil WHERE perfil = 'funcionario');

-- Cartoes reais do grupo (sem permissoes proprias: usam o horario do perfil)
INSERT INTO tam.cartoes (uid, utilizador_id, estado)
SELECT '8DF36E69', id, 'ativo' FROM tam.utilizadores WHERE email = 'admin@smartaccess.pt'
ON CONFLICT (uid) DO NOTHING;

INSERT INTO tam.cartoes (uid, utilizador_id, estado)
SELECT '337A93AA', id, 'ativo' FROM tam.utilizadores WHERE nome = 'Docente Teste'
ON CONFLICT (uid) DO NOTHING;

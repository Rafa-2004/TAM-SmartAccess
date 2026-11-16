SmartAccess - FUNCTIONS (fn_)  -  consultas que devolvem linhas


-- ---------- Utilizadores ----------
-- POST /api/auth/login
CREATE OR REPLACE FUNCTION tam.fn_utilizador_por_email(p_email text)
RETURNS TABLE (id bigint, nome text, email text, perfil text, password_hash text)
LANGUAGE sql STABLE AS $$
  SELECT u.id, u.nome, u.email, u.perfil, u.password_hash
  FROM tam.utilizadores u WHERE lower(u.email) = lower(p_email);
$$;

-- utilizador_atual()
CREATE OR REPLACE FUNCTION tam.fn_utilizador_por_id(p_id bigint)
RETURNS TABLE (id bigint, nome text, email text, perfil text)
LANGUAGE sql STABLE AS $$
  SELECT u.id, u.nome, u.email, u.perfil FROM tam.utilizadores u WHERE u.id = p_id;
$$;

-- cartoes / utilizadores: verificar se o utilizador existe
CREATE OR REPLACE FUNCTION tam.fn_utilizador_existe(p_id bigint)
RETURNS TABLE (existe int)
LANGUAGE sql STABLE AS $$
  SELECT 1 FROM tam.utilizadores WHERE id = p_id;
$$;

-- GET /api/users
CREATE OR REPLACE FUNCTION tam.fn_listar_utilizadores()
RETURNS TABLE (id bigint, nome text, email text, perfil text)
LANGUAGE sql STABLE AS $$
  SELECT u.id, u.nome, u.email, u.perfil FROM tam.utilizadores u ORDER BY u.nome;
$$;

-- POST /api/users
CREATE OR REPLACE FUNCTION tam.fn_inserir_utilizador(p_nome text, p_email text, p_password_hash text, p_perfil text)
RETURNS TABLE (id bigint, nome text, email text, perfil text)
LANGUAGE sql AS $$
  INSERT INTO tam.utilizadores (nome, email, password_hash, perfil)
  VALUES (p_nome, p_email, p_password_hash, p_perfil)
  RETURNING tam.utilizadores.id, tam.utilizadores.nome, tam.utilizadores.email, tam.utilizadores.perfil;
$$;

-- PATCH /api/users/<id>
CREATE OR REPLACE FUNCTION tam.fn_utilizador_completo(p_id bigint)
RETURNS TABLE (nome text, perfil text, email text, password_hash text)
LANGUAGE sql STABLE AS $$
  SELECT u.nome, u.perfil, u.email, u.password_hash FROM tam.utilizadores u WHERE u.id = p_id;
$$;

CREATE OR REPLACE FUNCTION tam.fn_atualizar_utilizador(p_nome text, p_perfil text, p_email text, p_password_hash text, p_id bigint)
RETURNS TABLE (id bigint, nome text, email text, perfil text)
LANGUAGE sql AS $$
  UPDATE tam.utilizadores SET nome = p_nome, perfil = p_perfil, email = p_email, password_hash = p_password_hash
  WHERE tam.utilizadores.id = p_id
  RETURNING tam.utilizadores.id, tam.utilizadores.nome, tam.utilizadores.email, tam.utilizadores.perfil;
$$;

-- ---------- Dispositivos ----------
-- dispositivo_atual(): identifica o Arduino pela chave e regista o contacto
CREATE OR REPLACE FUNCTION tam.fn_dispositivo_por_chave(p_hash text)
RETURNS TABLE (id bigint, nome text, estado text)
LANGUAGE sql AS $$
  UPDATE tam.dispositivos SET ultimo_contacto = now()
  WHERE api_key_hash = p_hash
  RETURNING tam.dispositivos.id, tam.dispositivos.nome, tam.dispositivos.estado;
$$;

-- POST /api/device/register
CREATE OR REPLACE FUNCTION tam.fn_registar_dispositivo(p_nome text, p_mac text, p_hash text)
RETURNS TABLE (id bigint, nome text, estado text)
LANGUAGE sql AS $$
  INSERT INTO tam.dispositivos (nome, mac, api_key_hash, estado)
  VALUES (p_nome, p_mac, p_hash, 'pendente')
  ON CONFLICT (mac) DO UPDATE
    SET api_key_hash = EXCLUDED.api_key_hash, porta_aberta = FALSE,
        estado = CASE WHEN tam.dispositivos.estado = 'revogado' THEN 'revogado' ELSE 'pendente' END
  RETURNING tam.dispositivos.id, tam.dispositivos.nome, tam.dispositivos.estado;
$$;

-- GET /api/device/config, POST /api/device/events (passagem)
CREATE OR REPLACE FUNCTION tam.fn_pessoas_dentro(p_dispositivo_id bigint)
RETURNS TABLE (pessoas_dentro int)
LANGUAGE sql STABLE AS $$
  SELECT d.pessoas_dentro FROM tam.dispositivos d WHERE d.id = p_dispositivo_id;
$$;

-- POST /api/device/events (passagem): atualiza as pessoas dentro (+1 entrada, -1 saida)
CREATE OR REPLACE FUNCTION tam.fn_atualizar_pessoas_dentro(p_variacao integer, p_dispositivo_id bigint)
RETURNS TABLE (pessoas_dentro int)
LANGUAGE sql AS $$
  UPDATE tam.dispositivos SET pessoas_dentro = greatest(tam.dispositivos.pessoas_dentro + p_variacao, 0)
  WHERE tam.dispositivos.id = p_dispositivo_id
  RETURNING tam.dispositivos.pessoas_dentro;
$$;

-- POST /api/door/unlock (dispositivo indicado)
CREATE OR REPLACE FUNCTION tam.fn_dispositivo_ativo(p_id bigint)
RETURNS TABLE (id bigint, nome text)
LANGUAGE sql STABLE AS $$
  SELECT d.id, d.nome FROM tam.dispositivos d WHERE d.id = p_id AND d.estado = 'ativo';
$$;

-- POST /api/door/unlock (primeiro dispositivo ativo)
CREATE OR REPLACE FUNCTION tam.fn_primeiro_dispositivo_ativo()
RETURNS TABLE (id bigint, nome text)
LANGUAGE sql STABLE AS $$
  SELECT d.id, d.nome FROM tam.dispositivos d WHERE d.estado = 'ativo' ORDER BY d.id LIMIT 1;
$$;

-- GET /api/devices
CREATE OR REPLACE FUNCTION tam.fn_listar_dispositivos(p_offline_apos interval)
RETURNS TABLE (id bigint, nome text, mac text, estado text, porta_aberta boolean, pessoas_dentro int,
               ultimo_contacto timestamptz, criado_em timestamptz, online boolean)
LANGUAGE sql STABLE AS $$
  SELECT d.id, d.nome, d.mac, d.estado, d.porta_aberta, d.pessoas_dentro, d.ultimo_contacto, d.criado_em,
         coalesce(d.ultimo_contacto > now() - p_offline_apos, false) AS online
  FROM tam.dispositivos d
  ORDER BY (d.estado = 'pendente') DESC, d.id;
$$;

-- PATCH /api/devices/<id>
CREATE OR REPLACE FUNCTION tam.fn_dispositivo_nome_estado(p_id bigint)
RETURNS TABLE (nome text, estado text)
LANGUAGE sql STABLE AS $$
  SELECT d.nome, d.estado FROM tam.dispositivos d WHERE d.id = p_id;
$$;

CREATE OR REPLACE FUNCTION tam.fn_atualizar_dispositivo(p_nome text, p_estado text, p_id bigint)
RETURNS TABLE (id bigint, nome text, mac text, estado text, ultimo_contacto timestamptz)
LANGUAGE sql AS $$
  UPDATE tam.dispositivos SET nome = p_nome, estado = p_estado WHERE tam.dispositivos.id = p_id
  RETURNING tam.dispositivos.id, tam.dispositivos.nome, tam.dispositivos.mac,
            tam.dispositivos.estado, tam.dispositivos.ultimo_contacto;
$$;

-- verificar_offline()
CREATE OR REPLACE FUNCTION tam.fn_dispositivos_offline(p_offline_apos interval)
RETURNS TABLE (id bigint, nome text)
LANGUAGE sql STABLE AS $$
  SELECT d.id, d.nome FROM tam.dispositivos d
  WHERE d.estado = 'ativo' AND d.ultimo_contacto < now() - p_offline_apos;
$$;

-- ---------- Configuracao e regras ----------
-- ler_configuracao()
CREATE OR REPLACE FUNCTION tam.fn_ler_configuracao(p_chave text)
RETURNS TABLE (valor text)
LANGUAGE sql STABLE AS $$
  SELECT c.valor FROM tam.configuracao c WHERE c.chave = p_chave;
$$;

-- dentro_do_horario(): usa as permissoes do cartao; se nao tiver nenhuma, o horario do perfil
CREATE OR REPLACE FUNCTION tam.fn_dentro_do_horario(p_cartao_id bigint, p_perfil text, p_dia integer, p_hora time)
RETURNS TABLE (existe int)
LANGUAGE sql STABLE AS $$
  SELECT 1 FROM (
    SELECT pe.dia_semana, pe.hora_inicio, pe.hora_fim FROM tam.permissoes pe WHERE pe.cartao_id = p_cartao_id
    UNION ALL
    SELECT hp.dia_semana, hp.hora_inicio, hp.hora_fim FROM tam.horarios_perfil hp
    WHERE hp.perfil = p_perfil AND NOT EXISTS (SELECT 1 FROM tam.permissoes px WHERE px.cartao_id = p_cartao_id)
  ) h
  WHERE h.dia_semana = p_dia AND h.hora_inicio <= p_hora AND p_hora < h.hora_fim;
$$;

-- verificar_tentativas_negadas()
CREATE OR REPLACE FUNCTION tam.fn_contar_negados(p_dispositivo_id bigint)
RETURNS TABLE (n bigint)
LANGUAGE sql STABLE AS $$
  SELECT count(*) AS n FROM tam.registos_acesso r
  WHERE r.dispositivo_id = p_dispositivo_id AND r.resultado = 'negado'
    AND r.motivo <> 'aula_a_decorrer'   -- cartao valido, so nao pode fechar a aula de outro
    AND r.data_hora > now() - interval '5 minutes'
    AND r.data_hora > coalesce(
      (SELECT max(r2.data_hora) FROM tam.registos_acesso r2
       WHERE r2.dispositivo_id = p_dispositivo_id AND r2.resultado = 'concedido'),
      '-infinity');
$$;

-- ---------- Aulas ----------
-- aula_aberta()
CREATE OR REPLACE FUNCTION tam.fn_aula_aberta(p_dispositivo_id bigint)
RETURNS TABLE (id bigint, dispositivo_id bigint, aberta_por bigint, cartao_uid text, registo_abertura_id bigint,
               inicio timestamptz, fim timestamptz, fechada_por bigint, motivo_fim text,
               entradas int, saidas int, entradas_nao_detetadas int, dono text, dono_perfil text)
LANGUAGE sql STABLE AS $$
  SELECT a.id, a.dispositivo_id, a.aberta_por, a.cartao_uid, a.registo_abertura_id,
         a.inicio, a.fim, a.fechada_por, a.motivo_fim,
         a.entradas, a.saidas, a.entradas_nao_detetadas, u.nome AS dono, u.perfil AS dono_perfil
  FROM tam.aulas a LEFT JOIN tam.utilizadores u ON u.id = a.aberta_por
  WHERE a.dispositivo_id = p_dispositivo_id AND a.fim IS NULL;
$$;

-- POST /api/device/events (passagem): soma a entrada ou a saida na aula
CREATE OR REPLACE FUNCTION tam.fn_atualizar_aula_passagem(p_sentido text, p_nao_detetadas int, p_aula_id bigint)
RETURNS SETOF tam.aulas
LANGUAGE sql AS $$
  UPDATE tam.aulas SET
    entradas = entradas + CASE WHEN p_sentido = 'entrada' THEN 1 ELSE 0 END,
    saidas = saidas + CASE WHEN p_sentido = 'saida' THEN 1 ELSE 0 END,
    entradas_nao_detetadas = entradas_nao_detetadas + p_nao_detetadas
  WHERE id = p_aula_id
  RETURNING *;
$$;

-- GET /api/classes
CREATE OR REPLACE FUNCTION tam.fn_listar_aulas(p_so_minhas boolean, p_eu bigint, p_limite integer)
RETURNS TABLE (id bigint, dispositivo text, dono text, dono_perfil text, cartao_uid text,
               inicio timestamptz, fim timestamptz, fechada_por text, motivo_fim text,
               entradas int, saidas int, entradas_nao_detetadas int)
LANGUAGE sql STABLE AS $$
  SELECT a.id, d.nome AS dispositivo, u.nome AS dono, u.perfil AS dono_perfil, a.cartao_uid,
         a.inicio, a.fim, f.nome AS fechada_por, a.motivo_fim,
         a.entradas, a.saidas, a.entradas_nao_detetadas
  FROM tam.aulas a
  JOIN tam.dispositivos d ON d.id = a.dispositivo_id
  LEFT JOIN tam.utilizadores u ON u.id = a.aberta_por
  LEFT JOIN tam.utilizadores f ON f.id = a.fechada_por
  WHERE NOT p_so_minhas OR a.aberta_por = p_eu
  ORDER BY a.inicio DESC
  LIMIT p_limite;
$$;

-- ---------- Alertas ----------
-- criar_alerta(): ja existe um alerta igual por resolver?
CREATE OR REPLACE FUNCTION tam.fn_alerta_por_resolver(p_tipo text, p_dispositivo_id bigint)
RETURNS TABLE (existe int)
LANGUAGE sql STABLE AS $$
  SELECT 1 FROM tam.alertas WHERE tipo = p_tipo AND dispositivo_id = p_dispositivo_id AND NOT resolvido;
$$;

-- GET /api/alerts
CREATE OR REPLACE FUNCTION tam.fn_listar_alertas(p_resolvido boolean)
RETURNS TABLE (id bigint, tipo text, descricao text, data_hora timestamptz, resolvido boolean, dispositivo text)
LANGUAGE sql STABLE AS $$
  SELECT a.id, a.tipo, a.descricao, a.data_hora, a.resolvido, d.nome AS dispositivo
  FROM tam.alertas a LEFT JOIN tam.dispositivos d ON d.id = a.dispositivo_id
  WHERE p_resolvido IS NULL OR a.resolvido = p_resolvido
  ORDER BY a.resolvido, a.data_hora DESC
  LIMIT 200;
$$;

-- PATCH /api/alerts/<id>
CREATE OR REPLACE FUNCTION tam.fn_atualizar_alerta(p_resolvido boolean, p_id bigint)
RETURNS TABLE (id bigint, tipo text, descricao text, data_hora timestamptz, resolvido boolean)
LANGUAGE sql AS $$
  UPDATE tam.alertas SET resolvido = p_resolvido WHERE tam.alertas.id = p_id
  RETURNING tam.alertas.id, tam.alertas.tipo, tam.alertas.descricao, tam.alertas.data_hora, tam.alertas.resolvido;
$$;

-- ---------- Cartoes ----------
-- obter_cartao()
CREATE OR REPLACE FUNCTION tam.fn_obter_cartao(p_cartao_id bigint)
RETURNS TABLE (id bigint, uid text, estado text, utilizador_id bigint, utilizador text, utilizador_perfil text,
               criado_em timestamptz, permissoes json)
LANGUAGE sql STABLE AS $$
  SELECT c.id, c.uid, c.estado, c.utilizador_id, u.nome AS utilizador, u.perfil AS utilizador_perfil, c.criado_em,
         coalesce((SELECT json_agg(json_build_object(
                       'dia_semana', p.dia_semana,
                       'hora_inicio', to_char(p.hora_inicio, 'HH24:MI'),
                       'hora_fim', to_char(p.hora_fim, 'HH24:MI'))
                     ORDER BY p.dia_semana, p.hora_inicio)
                   FROM tam.permissoes p WHERE p.cartao_id = c.id), '[]') AS permissoes
  FROM tam.cartoes c LEFT JOIN tam.utilizadores u ON u.id = c.utilizador_id
  WHERE c.id = p_cartao_id;
$$;

-- GET /api/cards
CREATE OR REPLACE FUNCTION tam.fn_ids_cartoes(p_estado text)
RETURNS TABLE (id bigint)
LANGUAGE sql STABLE AS $$
  SELECT c.id FROM tam.cartoes c
  WHERE p_estado IS NULL OR c.estado = p_estado
  ORDER BY (c.estado = 'pendente') DESC, c.criado_em DESC;
$$;

-- POST /api/cards (cartao ja existe?)
CREATE OR REPLACE FUNCTION tam.fn_cartao_por_uid(p_uid text)
RETURNS TABLE (id bigint, estado text)
LANGUAGE sql STABLE AS $$
  SELECT c.id, c.estado FROM tam.cartoes c WHERE c.uid = p_uid;
$$;

-- POST /api/cards (cartao novo)
CREATE OR REPLACE FUNCTION tam.fn_inserir_cartao(p_uid text, p_utilizador_id bigint)
RETURNS TABLE (id bigint)
LANGUAGE sql AS $$
  INSERT INTO tam.cartoes (uid, utilizador_id, estado) VALUES (p_uid, p_utilizador_id, 'ativo')
  RETURNING tam.cartoes.id;
$$;

-- POST /api/access: cartao e dono
CREATE OR REPLACE FUNCTION tam.fn_cartao_acesso(p_uid text)
RETURNS TABLE (id bigint, estado text, utilizador_id bigint, nome text, perfil text)
LANGUAGE sql STABLE AS $$
  SELECT c.id, c.estado, c.utilizador_id, u.nome, u.perfil
  FROM tam.cartoes c LEFT JOIN tam.utilizadores u ON u.id = c.utilizador_id
  WHERE c.uid = p_uid;
$$;

-- POST /api/device/events (porta): dono do cartao
CREATE OR REPLACE FUNCTION tam.fn_cartao_utilizador(p_uid text)
RETURNS TABLE (utilizador_id bigint)
LANGUAGE sql STABLE AS $$
  SELECT c.utilizador_id FROM tam.cartoes c WHERE c.uid = p_uid;
$$;

-- GET /api/device/config: cartoes que funcionam sem rede
CREATE OR REPLACE FUNCTION tam.fn_cartoes_offline(p_perfis text[])
RETURNS TABLE (uid text)
LANGUAGE sql STABLE AS $$
  SELECT c.uid FROM tam.cartoes c JOIN tam.utilizadores u ON u.id = c.utilizador_id
  WHERE c.estado = 'ativo' AND u.perfil = ANY(p_perfis)
  ORDER BY c.id LIMIT 15;
$$;

-- ---------- Registos de acesso ----------
-- POST /api/access
CREATE OR REPLACE FUNCTION tam.fn_inserir_registo_cartao(p_dispositivo_id bigint, p_uid text, p_utilizador_id bigint,
                                                          p_resultado text, p_motivo text)
RETURNS TABLE (id bigint)
LANGUAGE sql AS $$
  INSERT INTO tam.registos_acesso (dispositivo_id, cartao_uid, utilizador_id, origem, resultado, motivo)
  VALUES (p_dispositivo_id, p_uid, p_utilizador_id, 'cartao', p_resultado, p_motivo)
  RETURNING tam.registos_acesso.id;
$$;

-- POST /api/device/events (comando_executado)
CREATE OR REPLACE FUNCTION tam.fn_inserir_registo_remoto(p_dispositivo_id bigint, p_utilizador_id bigint)
RETURNS TABLE (id bigint)
LANGUAGE sql AS $$
  INSERT INTO tam.registos_acesso (dispositivo_id, utilizador_id, origem, resultado, motivo)
  VALUES (p_dispositivo_id, p_utilizador_id, 'remoto', 'concedido', 'abertura_remota')
  RETURNING tam.registos_acesso.id;
$$;

-- GET /api/logs
CREATE OR REPLACE FUNCTION tam.fn_listar_registos(p_desde date, p_ate date, p_utilizador bigint,
                                                   p_resultado text, p_limite integer, p_fuso text)
RETURNS TABLE (id bigint, data_hora timestamptz, cartao_uid text, utilizador_id bigint, utilizador text,
               origem text, resultado text, motivo text, dispositivo text, entradas int)
LANGUAGE sql STABLE AS $$
  SELECT r.id, r.data_hora, r.cartao_uid, r.utilizador_id, u.nome AS utilizador,
         r.origem, r.resultado, r.motivo, d.nome AS dispositivo,
         (SELECT count(*) FROM tam.passagens p
          WHERE p.registo_acesso_id = r.id AND p.sentido = 'entrada')::int AS entradas
  FROM tam.registos_acesso r
  JOIN tam.dispositivos d ON d.id = r.dispositivo_id
  LEFT JOIN tam.utilizadores u ON u.id = r.utilizador_id
  WHERE (p_desde IS NULL OR r.data_hora >= p_desde::timestamp AT TIME ZONE p_fuso)
    AND (p_ate IS NULL OR r.data_hora < (p_ate + 1)::timestamp AT TIME ZONE p_fuso)
    AND (p_utilizador IS NULL OR r.utilizador_id = p_utilizador)
    AND (p_resultado IS NULL OR r.resultado = p_resultado)
  ORDER BY r.data_hora DESC
  LIMIT p_limite;
$$;

-- ---------- Comandos ----------
-- GET /api/device/commands
CREATE OR REPLACE FUNCTION tam.fn_comandos_pendentes(p_dispositivo_id bigint)
RETURNS TABLE (id bigint, tipo text)
LANGUAGE sql STABLE AS $$
  SELECT c.id, c.tipo FROM tam.comandos c
  WHERE c.dispositivo_id = p_dispositivo_id AND c.estado = 'pendente' ORDER BY c.id;
$$;

-- POST /api/device/events (comando_executado)
CREATE OR REPLACE FUNCTION tam.fn_executar_comando(p_comando_id bigint, p_dispositivo_id bigint)
RETURNS TABLE (criado_por bigint)
LANGUAGE sql AS $$
  UPDATE tam.comandos SET estado = 'executado', executado_em = now()
  WHERE tam.comandos.id = p_comando_id AND dispositivo_id = p_dispositivo_id AND estado = 'pendente'
  RETURNING tam.comandos.criado_por;
$$;

-- POST /api/door/unlock
CREATE OR REPLACE FUNCTION tam.fn_inserir_comando(p_dispositivo_id bigint, p_criado_por bigint)
RETURNS TABLE (id bigint)
LANGUAGE sql AS $$
  INSERT INTO tam.comandos (dispositivo_id, criado_por) VALUES (p_dispositivo_id, p_criado_por)
  RETURNING tam.comandos.id;
$$;

-- GET /api/door/commands/<id>
CREATE OR REPLACE FUNCTION tam.fn_comando_por_id(p_id bigint)
RETURNS TABLE (id bigint, estado text, criado_em timestamptz, executado_em timestamptz)
LANGUAGE sql STABLE AS $$
  SELECT c.id, c.estado, c.criado_em, c.executado_em FROM tam.comandos c WHERE c.id = p_id;
$$;

-- ---------- Estado do sistema ----------
-- GET /api/status
CREATE OR REPLACE FUNCTION tam.fn_estado_sistema(p_offline_apos interval)
RETURNS TABLE (id bigint, nome text, porta_aberta boolean, pessoas_dentro int, ultimo_contacto timestamptz,
               online boolean, ultimo_acesso json, aula json)
LANGUAGE sql STABLE AS $$
  SELECT d.id, d.nome, d.porta_aberta, d.pessoas_dentro, d.ultimo_contacto,
         coalesce(d.ultimo_contacto > now() - p_offline_apos, false) AS online,
         (SELECT row_to_json(x) FROM (
            SELECT r.data_hora, r.resultado, r.origem, r.motivo, u.nome AS utilizador
            FROM tam.registos_acesso r LEFT JOIN tam.utilizadores u ON u.id = r.utilizador_id
            WHERE r.dispositivo_id = d.id
            ORDER BY r.data_hora DESC LIMIT 1) x) AS ultimo_acesso,
         (SELECT row_to_json(x) FROM (
            SELECT a.id, a.inicio, u.nome AS dono, u.perfil AS dono_perfil, a.entradas, a.saidas
            FROM tam.aulas a LEFT JOIN tam.utilizadores u ON u.id = a.aberta_por
            WHERE a.dispositivo_id = d.id AND a.fim IS NULL) x) AS aula
  FROM tam.dispositivos d WHERE d.estado = 'ativo' ORDER BY d.id;
$$;

-- ---------- Horarios dos perfis ----------
-- GET /api/schedules
CREATE OR REPLACE FUNCTION tam.fn_listar_horarios()
RETURNS TABLE (perfil text, dia_semana smallint, hora_inicio text, hora_fim text)
LANGUAGE sql STABLE AS $$
  SELECT h.perfil, h.dia_semana, to_char(h.hora_inicio, 'HH24:MI') AS hora_inicio,
         to_char(h.hora_fim, 'HH24:MI') AS hora_fim
  FROM tam.horarios_perfil h ORDER BY h.perfil, h.dia_semana, h.hora_inicio;
$$;

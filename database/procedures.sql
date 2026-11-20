--  SmartAccess - PROCEDURES (sp_)  -  accoes sem resultado


-- ---------- Alertas ----------
-- criar_alerta()
CREATE OR REPLACE PROCEDURE tam.sp_inserir_alerta(p_tipo text, p_dispositivo_id bigint, p_descricao text)
LANGUAGE sql AS $$
  INSERT INTO tam.alertas (tipo, dispositivo_id, descricao) VALUES (p_tipo, p_dispositivo_id, p_descricao);
$$;

-- ---------- Aulas e porta ----------
-- POST /api/device/register
CREATE OR REPLACE PROCEDURE tam.sp_fechar_aulas_por_sistema(p_dispositivo_id bigint)
LANGUAGE sql AS $$
  UPDATE tam.aulas SET fim = now(), motivo_fim = 'sistema'
  WHERE dispositivo_id = p_dispositivo_id AND fim IS NULL;
$$;

-- abrir_aula()
CREATE OR REPLACE PROCEDURE tam.sp_inserir_aula(p_dispositivo_id bigint, p_aberta_por bigint, p_cartao_uid text, p_registo_id bigint)
LANGUAGE sql AS $$
  INSERT INTO tam.aulas (dispositivo_id, aberta_por, cartao_uid, registo_abertura_id)
  VALUES (p_dispositivo_id, p_aberta_por, p_cartao_uid, p_registo_id);
$$;

-- abrir_aula()
CREATE OR REPLACE PROCEDURE tam.sp_abrir_porta(p_dispositivo_id bigint)
LANGUAGE sql AS $$
  UPDATE tam.dispositivos SET porta_aberta = TRUE WHERE id = p_dispositivo_id;
$$;

-- fechar_aula()
CREATE OR REPLACE PROCEDURE tam.sp_fechar_aula(p_fechada_por bigint, p_motivo text, p_aula_id bigint)
LANGUAGE sql AS $$
  UPDATE tam.aulas SET fim = now(), fechada_por = p_fechada_por, motivo_fim = p_motivo WHERE id = p_aula_id;
$$;

-- fechar_aula()
CREATE OR REPLACE PROCEDURE tam.sp_fechar_porta(p_dispositivo_id bigint)
LANGUAGE sql AS $$
  UPDATE tam.dispositivos SET porta_aberta = FALSE WHERE id = p_dispositivo_id;
$$;

-- ---------- Cartoes ----------
-- POST /api/access  (cartao novo fica pendente)
CREATE OR REPLACE PROCEDURE tam.sp_inserir_cartao_pendente(p_uid text)
LANGUAGE sql AS $$
  INSERT INTO tam.cartoes (uid) VALUES (p_uid) ON CONFLICT (uid) DO NOTHING;
$$;

-- POST /api/cards  (cartao pendente passa a ativo)
CREATE OR REPLACE PROCEDURE tam.sp_ativar_cartao(p_utilizador_id bigint, p_cartao_id bigint)
LANGUAGE sql AS $$
  UPDATE tam.cartoes SET estado = 'ativo', utilizador_id = p_utilizador_id WHERE id = p_cartao_id;
$$;

-- PATCH /api/cards/<id>
CREATE OR REPLACE PROCEDURE tam.sp_atualizar_cartao(p_estado text, p_utilizador_id bigint, p_cartao_id bigint)
LANGUAGE sql AS $$
  UPDATE tam.cartoes SET estado = p_estado, utilizador_id = p_utilizador_id WHERE id = p_cartao_id;
$$;

-- PUT /api/cards/<id>/permissions
CREATE OR REPLACE PROCEDURE tam.sp_apagar_permissoes(p_cartao_id bigint)
LANGUAGE sql AS $$
  DELETE FROM tam.permissoes WHERE cartao_id = p_cartao_id;
$$;

CREATE OR REPLACE PROCEDURE tam.sp_inserir_permissao(p_cartao_id bigint, p_dia integer, p_inicio time, p_fim time)
LANGUAGE sql AS $$
  INSERT INTO tam.permissoes (cartao_id, dia_semana, hora_inicio, hora_fim) VALUES (p_cartao_id, p_dia, p_inicio, p_fim);
$$;

-- DELETE /api/cards/<id>  (as permissoes proprias sao apagadas em cascata; os registos guardam so o UID)
CREATE OR REPLACE PROCEDURE tam.sp_apagar_cartao(p_cartao_id bigint)
LANGUAGE sql AS $$
  DELETE FROM tam.cartoes WHERE id = p_cartao_id;
$$;

-- ---------- Dispositivos ----------
-- DELETE /api/devices/<id>: apaga a porta e TODO o historico dela (passagens, aulas, comandos, alertas, registos).
-- Os registos sao imutaveis (trigger registos_imutaveis): a variavel tam.apagar_porta abre uma excecao
-- so dentro desta transacao. Se o Arduino continuar ligado, regista-se de novo e aparece como pendente.
CREATE OR REPLACE PROCEDURE tam.sp_apagar_dispositivo(p_dispositivo_id bigint)
LANGUAGE plpgsql AS $$
BEGIN
  PERFORM set_config('tam.apagar_porta', 'sim', true);
  DELETE FROM tam.passagens WHERE dispositivo_id = p_dispositivo_id;
  DELETE FROM tam.aulas WHERE dispositivo_id = p_dispositivo_id;
  DELETE FROM tam.comandos WHERE dispositivo_id = p_dispositivo_id;
  DELETE FROM tam.alertas WHERE dispositivo_id = p_dispositivo_id;
  DELETE FROM tam.registos_acesso WHERE dispositivo_id = p_dispositivo_id;
  DELETE FROM tam.dispositivos WHERE id = p_dispositivo_id;
  PERFORM set_config('tam.apagar_porta', '', true);
END;
$$;

-- ---------- Utilizadores ----------
-- DELETE /api/users/<id>: revoga os cartoes e marca o utilizador como removido (o historico mantem o nome)
CREATE OR REPLACE PROCEDURE tam.sp_remover_utilizador(p_utilizador_id bigint)
LANGUAGE sql AS $$
  UPDATE tam.cartoes SET estado = 'revogado' WHERE utilizador_id = p_utilizador_id AND estado <> 'revogado';
  UPDATE tam.utilizadores SET removido_em = now() WHERE id = p_utilizador_id AND removido_em IS NULL;
$$;

-- ---------- Comandos ----------
-- GET /api/device/commands
CREATE OR REPLACE PROCEDURE tam.sp_expirar_comandos(p_dispositivo_id bigint, p_validade interval)
LANGUAGE sql AS $$
  UPDATE tam.comandos SET estado = 'expirado'
  WHERE dispositivo_id = p_dispositivo_id AND estado = 'pendente' AND criado_em < now() - p_validade;
$$;

-- ---------- Passagens ----------
-- POST /api/device/events (passagem)
CREATE OR REPLACE PROCEDURE tam.sp_inserir_passagem(p_dispositivo_id bigint, p_sentido text, p_registo_id bigint, p_aula_id bigint)
LANGUAGE sql AS $$
  INSERT INTO tam.passagens (dispositivo_id, sentido, registo_acesso_id, aula_id)
  VALUES (p_dispositivo_id, p_sentido, p_registo_id, p_aula_id);
$$;

-- ---------- Horarios dos perfis ----------
-- PUT /api/schedules/<perfil>
CREATE OR REPLACE PROCEDURE tam.sp_apagar_horarios_perfil(p_perfil text)
LANGUAGE sql AS $$
  DELETE FROM tam.horarios_perfil WHERE perfil = p_perfil;
$$;

CREATE OR REPLACE PROCEDURE tam.sp_inserir_horario_perfil(p_perfil text, p_dia integer, p_inicio time, p_fim time)
LANGUAGE sql AS $$
  INSERT INTO tam.horarios_perfil (perfil, dia_semana, hora_inicio, hora_fim) VALUES (p_perfil, p_dia, p_inicio, p_fim);
$$;

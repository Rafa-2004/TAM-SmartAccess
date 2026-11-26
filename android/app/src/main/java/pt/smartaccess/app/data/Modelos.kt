// Modelos de dados: cada classe corresponde a um objeto JSON enviado ou recebido da API.
// O Gson converte JSON <-> Kotlin; @SerializedName liga o nome do JSON (snake_case) ao nome em Kotlin.
// Nos pedidos, os campos a null nao sao enviados (permite alterar so alguns campos num PATCH).
package pt.smartaccess.app.data

import com.google.gson.annotations.SerializedName

// Respostas e pedidos da API (ver api/index.py)

// So o admin tem email (e conta na app); docentes e funcionarios sao so o nome do cartao
// Pessoa do sistema (GET /api/users, GET /api/me e resposta do login).
data class Utilizador(
    val id: Long,
    val nome: String,
    val email: String?,
    val perfil: String,
) {
    // true se for o administrador (decide que separadores aparecem na app)
    val admin: Boolean get() = perfil == "admin"
}

// Corpo do POST /api/auth/login
data class PedidoLogin(val email: String, val password: String)

// Resposta do login (e do POST /api/setup): token JWT + dados do utilizador
data class RespostaLogin(val token: String, val utilizador: Utilizador)

// GET /api/setup: no primeiro arranque ainda nao ha administrador
data class EstadoInicial(@SerializedName("precisa_admin") val precisaAdmin: Boolean)

// POST /api/setup: cria o (unico) administrador e devolve a sessao como o login
data class NovoAdministrador(val nome: String, val email: String, val password: String)

// perfil: admin (docente administrador) | funcionario | docente; email e password so para o admin
data class NovoUtilizador(
    val nome: String,
    val perfil: String,
    val email: String? = null,
    val password: String? = null,
)

// Campos a null nao sao enviados (o Gson omite-os). Passar a admin exige email e password.
data class AlteracaoUtilizador(
    val nome: String? = null,
    val perfil: String? = null,
    val email: String? = null,
    val password: String? = null,
)

// POST /api/cards: cartao adicionado a mao, ja associado a uma pessoa
data class NovoCartao(
    val uid: String,
    @SerializedName("utilizador_id") val utilizadorId: Long,
)

// GET /api/status (so as portas ativas)
data class Dispositivo(
    val id: Long,
    val nome: String,
    @SerializedName("porta_aberta") val portaAberta: Boolean,
    @SerializedName("pessoas_dentro") val pessoasDentro: Int,
    @SerializedName("ultimo_contacto") val ultimoContacto: String?,
    val online: Boolean,
    @SerializedName("ultimo_acesso") val ultimoAcesso: UltimoAcesso?,
    val aula: AulaAtual?,
)

// Aula a decorrer numa porta (dentro de /api/status)
data class AulaAtual(
    val id: Long,
    val inicio: String,
    val dono: String?,
    @SerializedName("dono_perfil") val donoPerfil: String?,
    val entradas: Int,
    val saidas: Int,
)

// GET /api/devices (todas as portas, incluindo as pendentes); estado: pendente | ativo | revogado
data class DispositivoGestao(
    val id: Long,
    val nome: String,
    val mac: String?,
    val estado: String,
    @SerializedName("porta_aberta") val portaAberta: Boolean,
    @SerializedName("pessoas_dentro") val pessoasDentro: Int,
    @SerializedName("ultimo_contacto") val ultimoContacto: String?,
    @SerializedName("criado_em") val criadoEm: String,
    val online: Boolean,
)

// Corpo do PATCH /api/devices/{id}: so os campos preenchidos sao alterados
data class AlteracaoDispositivo(
    val estado: String? = null,
    val nome: String? = null,
)

// GET /api/classes; motivo_fim: dono | admin | funcionario | offline | sistema
data class Aula(
    val id: Long,
    val dispositivo: String,
    val dono: String?,
    @SerializedName("dono_perfil") val donoPerfil: String?,
    @SerializedName("cartao_uid") val cartaoUid: String?,
    val inicio: String,
    val fim: String?,
    @SerializedName("fechada_por") val fechadaPor: String?,
    @SerializedName("motivo_fim") val motivoFim: String?,
    val entradas: Int,
    val saidas: Int,
    @SerializedName("entradas_nao_detetadas") val entradasNaoDetetadas: Int,
) {
    // Sem data de fim = a aula ainda esta a decorrer
    val aDecorrer: Boolean get() = fim == null
}

// GET /api/schedules (o admin entra sempre, nao tem horario)
data class HorariosPerfis(
    val docente: List<Permissao>,
    val funcionario: List<Permissao>,
)

// Ultimo acesso a uma porta (vem dentro de GET /api/status)
data class UltimoAcesso(
    @SerializedName("data_hora") val dataHora: String,
    val resultado: String,
    val origem: String,
    val utilizador: String?,
    val motivo: String?,
)

// Corpo do POST /api/door/unlock: a porta a abrir
data class PedidoAbertura(@SerializedName("dispositivo_id") val dispositivoId: Long?)

// Resposta da abertura remota: id do comando criado (para acompanhar) e nome da porta
data class RespostaAbertura(
    @SerializedName("comando_id") val comandoId: Long,
    val dispositivo: String,
)

// estado: pendente | executado | expirado
data class Comando(val id: Long, val estado: String)

// GET /api/logs
data class Registo(
    val id: Long,
    @SerializedName("data_hora") val dataHora: String,
    @SerializedName("cartao_uid") val cartaoUid: String?,
    @SerializedName("utilizador_id") val utilizadorId: Long?,
    val utilizador: String?,
    val origem: String,
    val resultado: String,
    val motivo: String?,
    val dispositivo: String,
    val entradas: Int,
    // Ex.: sala fechada com pessoas contadas dentro (saidas nao detetadas)
    val aviso: String? = null,
) {
    // true se o acesso foi aceite
    val concedido: Boolean get() = resultado == "concedido"
}

// dia_semana: 1 = segunda ... 7 = domingo; horas no formato "HH:MM"
data class Permissao(
    @SerializedName("dia_semana") val diaSemana: Int,
    @SerializedName("hora_inicio") val horaInicio: String,
    @SerializedName("hora_fim") val horaFim: String,
)

// estado: pendente | ativo | revogado
data class Cartao(
    val id: Long,
    val uid: String,
    val estado: String,
    @SerializedName("utilizador_id") val utilizadorId: Long?,
    val utilizador: String?,
    @SerializedName("utilizador_perfil") val utilizadorPerfil: String?,
    @SerializedName("criado_em") val criadoEm: String,
    val permissoes: List<Permissao>,
)

// Campos a null nao sao enviados (o Gson omite-os)
data class AlteracaoCartao(
    val estado: String? = null,
    @SerializedName("utilizador_id") val utilizadorId: Long? = null,
)

// GET /api/alerts: aviso para o administrador (tipo, descricao, data e porta)
data class Alerta(
    val id: Long,
    val tipo: String,
    val descricao: String,
    @SerializedName("data_hora") val dataHora: String,
    val resolvido: Boolean,
    val dispositivo: String?,
)

// Corpo do PATCH /api/alerts/{id}
data class AlteracaoAlerta(val resolvido: Boolean)

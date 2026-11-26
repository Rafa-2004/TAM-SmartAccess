// Ligacao a API REST (Retrofit + OkHttp + Gson).
// O endereco vem de BuildConfig.API_URL (definido no app/build.gradle.kts ou no local.properties).
package pt.smartaccess.app.data

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import pt.smartaccess.app.BuildConfig
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException

// Interface Retrofit: cada funcao corresponde a um endpoint da API e o Retrofit gera a implementacao.
// As funcoes sao 'suspend': correm dentro de uma coroutine e nao bloqueiam o ecra enquanto esperam.
interface SmartAccessApi {
    // Entrar na app: devolve o token JWT e o utilizador
    @POST("api/auth/login")
    suspend fun login(@Body pedido: PedidoLogin): RespostaLogin

    // Primeiro arranque: diz se ainda e preciso criar o administrador
    @GET("api/setup")
    suspend fun estadoInicial(): EstadoInicial

    // Cria o unico administrador e devolve logo a sessao (como o login)
    @POST("api/setup")
    suspend fun criarAdministrador(@Body novo: NovoAdministrador): RespostaLogin

    // Dados atuais do utilizador autenticado
    @GET("api/me")
    suspend fun eu(): Utilizador

    // Portas ativas com estado, aula a decorrer e ultimo acesso (separador Painel)
    @GET("api/status")
    suspend fun estado(): List<Dispositivo>

    // Pede a abertura remota: a API cria um comando que o Arduino vai buscar
    @POST("api/door/unlock")
    suspend fun abrirPorta(@Body pedido: PedidoAbertura): RespostaAbertura

    // Estado de um comando de abertura: pendente, executado ou expirado
    @GET("api/door/commands/{id}")
    suspend fun comando(@Path("id") id: Long): Comando

    // desde/ate no formato "yyyy-MM-dd"; parametros a null nao sao enviados
    // Registos de acesso com filtros (separador Registos)
    @GET("api/logs")
    suspend fun registos(
        @Query("desde") desde: String? = null,
        @Query("ate") ate: String? = null,
        @Query("utilizador_id") utilizadorId: Long? = null,
        @Query("resultado") resultado: String? = null,
        @Query("limite") limite: Int = 100,
    ): List<Registo>

    // Todos os cartoes (os pendentes primeiro)
    @GET("api/cards")
    suspend fun cartoes(): List<Cartao>

    // Adiciona um cartao pelo UID, ja associado a uma pessoa
    @POST("api/cards")
    suspend fun criarCartao(@Body novo: NovoCartao): Cartao

    // Aprova, revoga, reativa ou troca o dono de um cartao
    @PATCH("api/cards/{id}")
    suspend fun alterarCartao(@Path("id") id: Long, @Body alteracao: AlteracaoCartao): Cartao

    // Apaga o cartao (o historico de acessos guarda so o UID, nao se perde)
    @DELETE("api/cards/{id}")
    suspend fun apagarCartao(@Path("id") id: Long)

    // Substitui o horario proprio do cartao (lista vazia = volta ao horario do perfil)
    @PUT("api/cards/{id}/permissions")
    suspend fun definirPermissoes(@Path("id") id: Long, @Body permissoes: List<Permissao>): List<Permissao>

    // Lista de alertas (os por resolver primeiro)
    @GET("api/alerts")
    suspend fun alertas(): List<Alerta>

    // Marca um alerta como resolvido
    @PATCH("api/alerts/{id}")
    suspend fun alterarAlerta(@Path("id") id: Long, @Body alteracao: AlteracaoAlerta): Alerta

    // Lista de pessoas
    @GET("api/users")
    suspend fun utilizadores(): List<Utilizador>

    // Cria um docente ou funcionario
    @POST("api/users")
    suspend fun criarUtilizador(@Body novo: NovoUtilizador): Utilizador

    // Muda o nome ou o perfil de uma pessoa
    @PATCH("api/users/{id}")
    suspend fun alterarUtilizador(@Path("id") id: Long, @Body alteracao: AlteracaoUtilizador): Utilizador

    // Remove um docente/funcionario: os cartoes sao revogados, o historico mantem o nome. O admin nao pode ser removido.
    @DELETE("api/users/{id}")
    suspend fun removerUtilizador(@Path("id") id: Long)

    // Todas as portas, incluindo pendentes e revogadas (Gestao > Portas)
    @GET("api/devices")
    suspend fun dispositivos(): List<DispositivoGestao>

    // Aprova, muda o nome, revoga ou reativa uma porta
    @PATCH("api/devices/{id}")
    suspend fun alterarDispositivo(@Path("id") id: Long, @Body alteracao: AlteracaoDispositivo): DispositivoGestao

    // Apaga a porta e todo o historico dela (registos, aulas, passagens, alertas)
    @DELETE("api/devices/{id}")
    suspend fun apagarDispositivo(@Path("id") id: Long)

    // Historico das aulas (as mais recentes primeiro)
    @GET("api/classes")
    suspend fun aulas(@Query("limite") limite: Int = 50): List<Aula>

    // Horarios por omissao do docente e do funcionario
    @GET("api/schedules")
    suspend fun horarios(): HorariosPerfis

    // perfil: docente | funcionario
    // Substitui o horario de um perfil
    @PUT("api/schedules/{perfil}")
    suspend fun definirHorario(@Path("perfil") perfil: String, @Body horario: List<Permissao>): List<Permissao>

    // Partilhado por toda a app: token da sessao, reacao ao 401 e o cliente HTTP
    companion object {
        // Token JWT da sessao atual (definido pelo SessaoViewModel)
        @Volatile
        var token: String? = null

        // Chamado quando a API responde 401 a um pedido autenticado (token expirado)
        @Volatile
        var aoExpirarSessao: (() -> Unit)? = null

        // Cliente criado so na primeira utilizacao (by lazy) e reutilizado sempre
        // (o OkHttp reaproveita as ligacoes HTTPS entre pedidos).
        val instancia: SmartAccessApi by lazy {
            val cliente = OkHttpClient.Builder()
                // Interceptor: corre em todos os pedidos. Junta o cabecalho Authorization com o token e,
                // se a API responder 401 a um pedido que levava token, termina a sessao (aoExpirarSessao).
                .addInterceptor { cadeia ->
                    val atual = token
                    val pedido = cadeia.request().newBuilder().apply {
                        if (atual != null) header("Authorization", "Bearer $atual")
                    }.build()
                    val resposta = cadeia.proceed(pedido)
                    if (resposta.code == 401 && atual != null) aoExpirarSessao?.invoke()
                    resposta
                }
                .build()

            Retrofit.Builder()
                .baseUrl(BuildConfig.API_URL)
                .client(cliente)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SmartAccessApi::class.java)
        }
    }
}

// Mensagem legivel a partir de um erro da API ({"detail": "..."}) ou de rede
fun Throwable.mensagemErro(): String = when (this) {
    is HttpException -> runCatching {
        val detalhe = JsonParser.parseString(response()?.errorBody()?.string()).asJsonObject.get("detail")
        if (detalhe.isJsonPrimitive) detalhe.asString else "Dados invalidos"
    }.getOrDefault("Erro ${code()}")
    is IOException -> "Sem ligacao ao servidor"
    else -> message ?: "Erro desconhecido"
}

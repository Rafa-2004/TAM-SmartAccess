// Sessao do utilizador: login, primeiro arranque (criar o administrador), logout e token da API.
package pt.smartaccess.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.smartaccess.app.data.DadosSessao
import pt.smartaccess.app.data.NovoAdministrador
import pt.smartaccess.app.data.PedidoLogin
import pt.smartaccess.app.data.SessaoStore
import pt.smartaccess.app.data.SmartAccessApi
import pt.smartaccess.app.data.mensagemErro

// A ler a sessao guardada, sem sessao (mostra o login) ou com sessao ativa
sealed interface EstadoSessao {
    data object ACarregar : EstadoSessao
    data object SemSessao : EstadoSessao
    data class Ativa(val dados: DadosSessao) : EstadoSessao
}

// AndroidViewModel (e nao ViewModel) porque o DataStore precisa do Context da aplicacao
class SessaoViewModel(aplicacao: Application) : AndroidViewModel(aplicacao) {

    // Onde a sessao fica guardada no telemovel
    private val store = SessaoStore(aplicacao)
    // Cliente da API (o mesmo em toda a app)
    private val api = SmartAccessApi.instancia

    // Estado da sessao calculado a partir do que esta guardado; tambem atualiza o token usado nos pedidos
    val sessao: StateFlow<EstadoSessao> = store.dados
        // Sempre que a sessao guardada muda, converte-a num EstadoSessao
        .map { dados ->
            // Os pedidos seguintes passam a levar este token (null = sem token)
            SmartAccessApi.token = dados?.token
            if (dados == null) EstadoSessao.SemSessao else EstadoSessao.Ativa(dados)
        }
        // Torna-o um StateFlow: comeca ja a ler (Eagerly) e, ate haver resposta, vale ACarregar
        .stateIn(viewModelScope, SharingStarted.Eagerly, EstadoSessao.ACarregar)

    // true enquanto um login ou a criacao do administrador esta a decorrer
    private val _aEntrar = MutableStateFlow(false)
    val aEntrar: StateFlow<Boolean> = _aEntrar

    // Mensagem de erro do login / primeiro arranque (null = sem erro)
    private val _erroLogin = MutableStateFlow<String?>(null)
    val erroLogin: StateFlow<String?> = _erroLogin

    // Primeiro arranque: null = ainda nao se sabe (ou sem rede), true = falta criar o administrador
    private val _precisaAdmin = MutableStateFlow<Boolean?>(null)
    val precisaAdmin: StateFlow<Boolean?> = _precisaAdmin

    // Pergunta a API se ainda falta criar o administrador
    fun verificarPrimeiroArranque() {
        // Coroutine: corre em segundo plano sem congelar o ecra; e cancelada se o ViewModel for destruido
        viewModelScope.launch {
            try {
                // GET /api/setup
                _precisaAdmin.value = api.estadoInicial().precisaAdmin
            // Cancelamento da coroutine (ex.: ecra fechado): deixa passar, nao e um erro para mostrar
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _precisaAdmin.value = null   // sem rede: mostra o login
            }
        }
    }

    // Valida os campos, cria o administrador na API e entra logo (guarda a sessao).
    // Se a API responder 409 (ja existe administrador), volta ao ecra de login.
    fun criarAdministrador(nome: String, email: String, password: String, confirmacao: String) {
        // Valida os campos: a primeira regra que falhar da a mensagem de erro (null = tudo certo)
        _erroLogin.value = when {
            nome.isBlank() -> "Preencha o nome"
            "@" !in email -> "Email invalido"
            password.length < 6 -> "A palavra-passe tem de ter pelo menos 6 caracteres"
            password != confirmacao -> "As palavras-passe nao coincidem"
            else -> null
        }
        // Havia erro: nao faz o pedido
        if (_erroLogin.value != null) return
        // Coroutine: corre em segundo plano sem congelar o ecra; e cancelada se o ViewModel for destruido
        viewModelScope.launch {
            _aEntrar.value = true
            try {
                // POST /api/setup: cria o administrador e devolve token + utilizador
                val resposta = api.criarAdministrador(NovoAdministrador(nome.trim(), email.trim(), password))
                _precisaAdmin.value = false
                // Guarda a sessao: 'sessao' passa a Ativa e o ecra muda sozinho para a app
                store.guardar(DadosSessao(resposta.token, resposta.utilizador))
            // Cancelamento da coroutine (ex.: ecra fechado): deixa passar, nao e um erro para mostrar
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _erroLogin.value = e.mensagemErro()
                // Ja havia administrador (ex.: criado noutro telemovel): volta ao login
                // 409 = ja existe administrador: passa a mostrar o login
                if (e is retrofit2.HttpException && e.code() == 409) _precisaAdmin.value = false
            // Corre sempre (correu bem ou mal): desliga a roda do botao
            } finally {
                _aEntrar.value = false
            }
        }
    }

    // Ao criar: um 401 em qualquer pedido termina a sessao; e o perfil guardado e confirmado na API
    init {
        // Quando a API responder 401 (token expirado), faz logout
        SmartAccessApi.aoExpirarSessao = { sair() }
        atualizarPerfil()
    }

    // O perfil fica guardado desde o login: se um admin o mudar entretanto (ou a base for atualizada),
    // a app mostraria os separadores errados. Pede os dados atuais a API sempre que a app arranca.
    fun atualizarPerfil() {
        // Coroutine: corre em segundo plano sem congelar o ecra; e cancelada se o ViewModel for destruido
        viewModelScope.launch {
            // Espera ate a sessao estar lida; se nao houver sessao ativa, nao faz nada (return@launch)
            val atual = (sessao.first { it != EstadoSessao.ACarregar } as? EstadoSessao.Ativa)?.dados ?: return@launch
            try {
                // GET /api/me: dados atuais do utilizador
                val utilizador = api.eu()
                if (utilizador != atual.utilizador) store.guardar(atual.copy(utilizador = utilizador))
            // Cancelamento da coroutine (ex.: ecra fechado): deixa passar, nao e um erro para mostrar
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // sem rede: fica o perfil guardado (um 401 ja termina a sessao pelo interceptor)
            }
        }
    }

    // Login: valida os campos, pede o token a API e guarda a sessao (o ecra muda sozinho para a app)
    fun entrar(email: String, password: String) {
        // Validacao local, antes de ir a API
        if (email.isBlank() || password.isEmpty()) {
            _erroLogin.value = "Preencha o email e a palavra-passe"
            return
        }
        // Coroutine: corre em segundo plano sem congelar o ecra; e cancelada se o ViewModel for destruido
        viewModelScope.launch {
            _aEntrar.value = true
            _erroLogin.value = null
            try {
                // POST /api/auth/login (a coroutine espera aqui pela resposta, sem congelar o ecra)
                val resposta = api.login(PedidoLogin(email.trim(), password))
                // Guarda a sessao: 'sessao' passa a Ativa e o ecra muda sozinho para a app
                store.guardar(DadosSessao(resposta.token, resposta.utilizador))
            // Cancelamento da coroutine (ex.: ecra fechado): deixa passar, nao e um erro para mostrar
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _erroLogin.value = e.mensagemErro()
            // Corre sempre (correu bem ou mal): desliga a roda do botao
            } finally {
                _aEntrar.value = false
            }
        }
    }

    // Logout: apaga a sessao guardada (o ecra volta ao login)
    fun sair() {
        // Apaga o token e o utilizador guardados -> o ecra volta ao login
        viewModelScope.launch { store.limpar() }
    }

    // O ViewModel vai ser destruido: deixa de reagir aos 401
    override fun onCleared() {
        SmartAccessApi.aoExpirarSessao = null
    }
}

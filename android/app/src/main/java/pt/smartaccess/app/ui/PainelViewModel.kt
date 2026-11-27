// Separador Painel: estado das portas ativas e abertura remota.
package pt.smartaccess.app.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pt.smartaccess.app.data.Dispositivo
import pt.smartaccess.app.data.PedidoAbertura
import pt.smartaccess.app.data.SmartAccessApi
import pt.smartaccess.app.data.mensagemErro

// ViewModel do Painel
class PainelViewModel(
    private val api: SmartAccessApi = SmartAccessApi.instancia,
) : BaseViewModel() {

    // Estado do ecra. _estado (com _) so pode ser alterado aqui dentro; o ecra le 'estado' (so leitura).
    private val _estado = MutableStateFlow<Carregamento<List<Dispositivo>>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<List<Dispositivo>>> = _estado

    // true enquanto decorre uma abertura remota (desativa os botoes de abrir)
    private val _aAbrir = MutableStateFlow(false)
    val aAbrir: StateFlow<Boolean> = _aAbrir

    // Portas ativas (GET /api/status)
    override suspend fun atualizar() {
        // Pede os dados a API e guarda-os no estado (se falhar e ja havia dados, mantem-nos no ecra)
        _estado.carregarDe { api.estado() }
    }

    // Cria o comando e espera que o Arduino o execute (consulta a cada 1 s, ate 1 minuto)
    fun abrirPorta(dispositivo: Dispositivo) {
        // Coroutine: corre em segundo plano sem congelar o ecra; e cancelada se o ViewModel for destruido
        viewModelScope.launch {
            // Desativa os botoes de abrir e mostra a roda
            _aAbrir.value = true
            try {
                // POST /api/door/unlock: cria o comando e devolve o seu id
                val pedido = api.abrirPorta(PedidoAbertura(dispositivo.id))
                // Mensagem imediata, antes de o Arduino responder
                mostrarMensagem("Pedido enviado para ${pedido.dispositivo}")

                var estadoComando = "pendente"
                var tentativas = 0
                // Espera pelo Arduino: pergunta o estado do comando a cada 1 s, ate 60 vezes (1 minuto)
                while (estadoComando == "pendente" && tentativas < 60) {
                    delay(1000)
                    // GET /api/door/commands/{id}
                    estadoComando = api.comando(pedido.comandoId).estado
                    tentativas++
                }
                mostrarMensagem(
                    // Resultado final do comando
                    when (estadoComando) {
                        "executado" -> "Porta aberta"
                        "expirado" -> "O dispositivo nao respondeu a tempo"
                        else -> "Sem confirmacao do dispositivo"
                    }
                )
                atualizar()
            // Cancelamento da coroutine (ex.: ecra fechado): deixa passar, nao e um erro para mostrar
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarMensagem(e.mensagemErro())
            } finally {
                // Volta a ativar os botoes (finally: acontece sempre)
                _aAbrir.value = false
            }
        }
    }
}

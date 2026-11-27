// Separador Alertas.
package pt.smartaccess.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.smartaccess.app.data.Alerta
import pt.smartaccess.app.data.AlteracaoAlerta
import pt.smartaccess.app.data.SmartAccessApi

// ViewModel do separador Alertas
class AlertasViewModel(
    private val api: SmartAccessApi = SmartAccessApi.instancia,
) : BaseViewModel() {

    // Estado do ecra. _estado (com _) so pode ser alterado aqui dentro; o ecra le 'estado' (so leitura).
    private val _estado = MutableStateFlow<Carregamento<List<Alerta>>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<List<Alerta>>> = _estado

    // GET /api/alerts (a API tambem verifica aqui as portas offline)
    override suspend fun atualizar() {
        // Pede os dados a API e guarda-os no estado (se falhar e ja havia dados, mantem-nos no ecra)
        _estado.carregarDe { api.alertas() }
    }

    // Marca o alerta como resolvido
    fun resolver(alerta: Alerta) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("Alerta marcado como resolvido") {
            api.alterarAlerta(alerta.id, AlteracaoAlerta(resolvido = true))
        }
}

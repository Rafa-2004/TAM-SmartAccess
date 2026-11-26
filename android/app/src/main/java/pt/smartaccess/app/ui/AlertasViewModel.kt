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

    private val _estado = MutableStateFlow<Carregamento<List<Alerta>>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<List<Alerta>>> = _estado

    // GET /api/alerts (a API tambem verifica aqui as portas offline)
    override suspend fun atualizar() {
        _estado.carregarDe { api.alertas() }
    }

    // Marca o alerta como resolvido
    fun resolver(alerta: Alerta) =
        executar("Alerta marcado como resolvido") {
            api.alterarAlerta(alerta.id, AlteracaoAlerta(resolvido = true))
        }
}

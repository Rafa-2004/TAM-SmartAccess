// Separador Registos: registos de acesso com filtros.
package pt.smartaccess.app.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pt.smartaccess.app.data.Registo
import pt.smartaccess.app.data.SmartAccessApi
import pt.smartaccess.app.data.Utilizador
import java.time.LocalDate

// dias = quantos dias para tras alem de hoje (null = sem limite)
enum class Periodo(val titulo: String, val dias: Long?) {
    HOJE("Hoje", 0),
    SEMANA("7 dias", 6),
    MES("30 dias", 29),
    TUDO("Tudo", null),
}

// Filtros escolhidos no ecra Registos
data class FiltrosRegistos(
    val periodo: Periodo = Periodo.SEMANA,
    val resultado: String? = null,          // null | "concedido" | "negado"
    val utilizador: Utilizador? = null,     // so para administradores
)

// ViewModel do separador Registos
class RegistosViewModel(
    private val api: SmartAccessApi = SmartAccessApi.instancia,
) : BaseViewModel() {

    // Estado do ecra. _estado (com _) so pode ser alterado aqui dentro; o ecra le 'estado' (so leitura).
    private val _estado = MutableStateFlow<Carregamento<List<Registo>>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<List<Registo>>> = _estado

    private val _filtros = MutableStateFlow(FiltrosRegistos())
    val filtros: StateFlow<FiltrosRegistos> = _filtros

    private val _utilizadores = MutableStateFlow<List<Utilizador>>(emptyList())
    val utilizadores: StateFlow<List<Utilizador>> = _utilizadores

    // Converte os filtros em parametros (desde = hoje menos N dias) e pede os registos
    override suspend fun atualizar() {
        // Filtros atuais
        val f = _filtros.value
        // Pede os dados a API e guarda-os no estado (se falhar e ja havia dados, mantem-nos no ecra)
        _estado.carregarDe {
            api.registos(
                // Data de inicio (hoje menos N dias); 'Tudo' (dias = null) nao envia data
                desde = f.periodo.dias?.let { LocalDate.now().minusDays(it).toString() },
                utilizadorId = f.utilizador?.id,
                resultado = f.resultado,
            )
        }
    }

    // Novo filtro: mostra a roda de carregamento e volta a pedir
    fun alterarFiltros(novos: FiltrosRegistos) {
        _filtros.value = novos
        // Mostra a roda enquanto carrega com os filtros novos
        _estado.value = Carregamento.ACarregar
        recarregar()
    }

    // Lista de pessoas para o filtro (so e carregada uma vez)
    fun carregarUtilizadores() {
        // Ja carregada: nao pede outra vez
        if (_utilizadores.value.isNotEmpty()) return
        // Coroutine: corre em segundo plano sem congelar o ecra; e cancelada se o ViewModel for destruido
        viewModelScope.launch {
            try {
                _utilizadores.value = api.utilizadores()
            // Cancelamento da coroutine (ex.: ecra fechado): deixa passar, nao e um erro para mostrar
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // o filtro por utilizador fica so com "Todos"
            }
        }
    }
}

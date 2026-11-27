// Separador Aulas: historico das aulas.
package pt.smartaccess.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.smartaccess.app.data.Aula
import pt.smartaccess.app.data.SmartAccessApi

// Docentes e funcionarios so recebem as suas aulas (a API filtra)
class AulasViewModel(
    private val api: SmartAccessApi = SmartAccessApi.instancia,
) : BaseViewModel() {

    // Estado do ecra. _estado (com _) so pode ser alterado aqui dentro; o ecra le 'estado' (so leitura).
    private val _estado = MutableStateFlow<Carregamento<List<Aula>>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<List<Aula>>> = _estado

    // Ultimas 100 aulas (GET /api/classes)
    override suspend fun atualizar() {
        // Pede os dados a API e guarda-os no estado (se falhar e ja havia dados, mantem-nos no ecra)
        _estado.carregarDe { api.aulas(limite = 100) }
    }
}

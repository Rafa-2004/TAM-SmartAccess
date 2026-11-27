// Gestao > Horarios: horario por omissao de cada perfil.
package pt.smartaccess.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.smartaccess.app.data.HorariosPerfis
import pt.smartaccess.app.data.Permissao
import pt.smartaccess.app.data.SmartAccessApi

// Horario por omissao de cada perfil (usado pelos cartoes sem horario proprio)
class HorariosViewModel(
    private val api: SmartAccessApi = SmartAccessApi.instancia,
) : BaseViewModel() {

    // Estado do ecra. _estado (com _) so pode ser alterado aqui dentro; o ecra le 'estado' (so leitura).
    private val _estado = MutableStateFlow<Carregamento<HorariosPerfis>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<HorariosPerfis>> = _estado

    // GET /api/schedules
    override suspend fun atualizar() {
        // Pede os dados a API e guarda-os no estado (se falhar e ja havia dados, mantem-nos no ecra)
        _estado.carregarDe { api.horarios() }
    }

    // Substitui o horario do perfil
    fun guardar(perfil: String, horario: List<Permissao>) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("Horario do ${textoPerfil(perfil).lowercase()} guardado") {
            api.definirHorario(perfil, horario)
        }
}

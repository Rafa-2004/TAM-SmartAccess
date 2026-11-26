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

    private val _estado = MutableStateFlow<Carregamento<HorariosPerfis>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<HorariosPerfis>> = _estado

    // GET /api/schedules
    override suspend fun atualizar() {
        _estado.carregarDe { api.horarios() }
    }

    // Substitui o horario do perfil
    fun guardar(perfil: String, horario: List<Permissao>) =
        executar("Horario do ${textoPerfil(perfil).lowercase()} guardado") {
            api.definirHorario(perfil, horario)
        }
}

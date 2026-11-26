// Gestao > Cartoes: associar, revogar, reativar, apagar cartoes e horario proprio.
package pt.smartaccess.app.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.smartaccess.app.data.AlteracaoCartao
import pt.smartaccess.app.data.Cartao
import pt.smartaccess.app.data.Permissao
import pt.smartaccess.app.data.SmartAccessApi
import pt.smartaccess.app.data.Utilizador

// ViewModel de Gestao > Cartoes
class CartoesViewModel(
    private val api: SmartAccessApi = SmartAccessApi.instancia,
) : BaseViewModel() {

    private val _estado = MutableStateFlow<Carregamento<List<Cartao>>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<List<Cartao>>> = _estado

    private val _utilizadores = MutableStateFlow<List<Utilizador>>(emptyList())
    val utilizadores: StateFlow<List<Utilizador>> = _utilizadores

    // Cartoes e pessoas (as pessoas sao precisas no dialogo de associar)
    override suspend fun atualizar() {
        _estado.carregarDe { api.cartoes() }
        try {
            _utilizadores.value = api.utilizadores()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // mantem a lista anterior
        }
    }

    // pendente != null: aprova um cartao lido numa porta; null: adiciona um cartao novo pelo UID
    fun associar(uid: String, escolha: EscolhaPessoa, pendente: Cartao?) =
        executar("Cartao $uid associado a ${escolha.nome}") {
            associarCartao(api, uid, escolha, pendente?.id)
        }

    // O cartao deixa de abrir portas
    fun revogar(cartao: Cartao) =
        executar("Cartao ${cartao.uid} revogado") {
            api.alterarCartao(cartao.id, AlteracaoCartao(estado = "revogado"))
        }

    // O cartao volta a funcionar
    fun reativar(cartao: Cartao) =
        executar("Cartao ${cartao.uid} reativado") {
            api.alterarCartao(cartao.id, AlteracaoCartao(estado = "ativo"))
        }

    // Apaga o cartao (o historico guarda so o UID)
    fun apagar(cartao: Cartao) =
        executar("Cartao ${cartao.uid} apagado") {
            api.apagarCartao(cartao.id)
        }

    // Horario proprio do cartao (substitui o do perfil)
    fun guardarHorarios(cartao: Cartao, permissoes: List<Permissao>) =
        executar("Horario guardado") {
            api.definirPermissoes(cartao.id, permissoes)
        }
}

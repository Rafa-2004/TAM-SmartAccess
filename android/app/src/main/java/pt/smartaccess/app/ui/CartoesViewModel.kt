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

    // Estado do ecra. _estado (com _) so pode ser alterado aqui dentro; o ecra le 'estado' (so leitura).
    private val _estado = MutableStateFlow<Carregamento<List<Cartao>>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<List<Cartao>>> = _estado

    private val _utilizadores = MutableStateFlow<List<Utilizador>>(emptyList())
    val utilizadores: StateFlow<List<Utilizador>> = _utilizadores

    // Cartoes e pessoas (as pessoas sao precisas no dialogo de associar)
    override suspend fun atualizar() {
        // Pede os dados a API e guarda-os no estado (se falhar e ja havia dados, mantem-nos no ecra)
        _estado.carregarDe { api.cartoes() }
        try {
            _utilizadores.value = api.utilizadores()
        // Cancelamento da coroutine (ex.: ecra fechado): deixa passar, nao e um erro para mostrar
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // mantem a lista anterior
        }
    }

    // pendente != null: aprova um cartao lido numa porta; null: adiciona um cartao novo pelo UID
    fun associar(uid: String, escolha: EscolhaPessoa, pendente: Cartao?) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("Cartao $uid associado a ${escolha.nome}") {
            associarCartao(api, uid, escolha, pendente?.id)
        }

    // O cartao deixa de abrir portas
    fun revogar(cartao: Cartao) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("Cartao ${cartao.uid} revogado") {
            api.alterarCartao(cartao.id, AlteracaoCartao(estado = "revogado"))
        }

    // O cartao volta a funcionar
    fun reativar(cartao: Cartao) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("Cartao ${cartao.uid} reativado") {
            api.alterarCartao(cartao.id, AlteracaoCartao(estado = "ativo"))
        }

    // Apaga o cartao (o historico guarda so o UID)
    fun apagar(cartao: Cartao) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("Cartao ${cartao.uid} apagado") {
            api.apagarCartao(cartao.id)
        }

    // Horario proprio do cartao (substitui o do perfil)
    fun guardarHorarios(cartao: Cartao, permissoes: List<Permissao>) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("Horario guardado") {
            api.definirPermissoes(cartao.id, permissoes)
        }
}

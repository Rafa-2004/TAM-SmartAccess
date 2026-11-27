// Gestao > Pessoas: criar, mudar perfil, remover e associar cartoes.
package pt.smartaccess.app.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.smartaccess.app.data.AlteracaoUtilizador
import pt.smartaccess.app.data.Cartao
import pt.smartaccess.app.data.NovoUtilizador
import pt.smartaccess.app.data.SmartAccessApi
import pt.smartaccess.app.data.Utilizador

// Pessoas (docentes, funcionarios e administradores) e os cartoes de cada uma
class UtilizadoresViewModel(
    private val api: SmartAccessApi = SmartAccessApi.instancia,
) : BaseViewModel() {

    // Estado do ecra. _estado (com _) so pode ser alterado aqui dentro; o ecra le 'estado' (so leitura).
    private val _estado = MutableStateFlow<Carregamento<List<Utilizador>>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<List<Utilizador>>> = _estado

    private val _cartoes = MutableStateFlow<List<Cartao>>(emptyList())
    val cartoes: StateFlow<List<Cartao>> = _cartoes

    // Pessoas e cartoes (para mostrar os cartoes de cada pessoa)
    override suspend fun atualizar() {
        // Pede os dados a API e guarda-os no estado (se falhar e ja havia dados, mantem-nos no ecra)
        _estado.carregarDe { api.utilizadores() }
        try {
            _cartoes.value = api.cartoes()
        // Cancelamento da coroutine (ex.: ecra fechado): deixa passar, nao e um erro para mostrar
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // mantem a lista anterior
        }
    }

    // Cria um docente ou funcionario
    fun criar(novo: NovoUtilizador) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("${novo.nome} criado como ${textoPerfil(novo.perfil).lowercase()}") {
            api.criarUtilizador(novo)
        }

    // Docente <-> funcionario
    fun mudarPerfil(utilizador: Utilizador, alteracao: AlteracaoUtilizador) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("${utilizador.nome} passou a ${textoPerfil(alteracao.perfil).lowercase()}") {
            api.alterarUtilizador(utilizador.id, alteracao)
        }

    // Remove a pessoa (cartoes revogados, o historico mantem o nome)
    fun remover(utilizador: Utilizador) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("${utilizador.nome} removido") {
            api.removerUtilizador(utilizador.id)
        }

    // Se ja existir um cartao pendente com este UID (lido numa porta), fica aprovado; senao e criado
    fun associar(uid: String, escolha: EscolhaPessoa) =
        // executar (Comum.kt): corre o bloco numa coroutine; se correr bem mostra esta mensagem
        // e volta a carregar os dados, se falhar mostra o erro da API
        executar("Cartao $uid associado a ${escolha.nome}") {
            val pendente = _cartoes.value.firstOrNull { it.uid == uid && it.estado == "pendente" }
            associarCartao(api, uid, escolha, pendente?.id)
        }
}

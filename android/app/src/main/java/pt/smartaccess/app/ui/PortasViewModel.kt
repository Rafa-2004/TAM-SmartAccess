// Gestao > Portas: aprovar, mudar nome, revogar, reativar e apagar portas (Arduinos).
package pt.smartaccess.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.smartaccess.app.data.AlteracaoDispositivo
import pt.smartaccess.app.data.DispositivoGestao
import pt.smartaccess.app.data.SmartAccessApi

// Arduinos registados: os novos chegam pendentes e so funcionam depois de aprovados
class PortasViewModel(
    private val api: SmartAccessApi = SmartAccessApi.instancia,
) : BaseViewModel() {

    private val _estado = MutableStateFlow<Carregamento<List<DispositivoGestao>>>(Carregamento.ACarregar)
    val estado: StateFlow<Carregamento<List<DispositivoGestao>>> = _estado

    // Todas as portas (GET /api/devices)
    override suspend fun atualizar() {
        _estado.carregarDe { api.dispositivos() }
    }

    // Porta pendente -> ativa, com o nome da sala
    fun aprovar(dispositivo: DispositivoGestao, nome: String) =
        executar("$nome aprovada") {
            api.alterarDispositivo(dispositivo.id, AlteracaoDispositivo(estado = "ativo", nome = nome))
        }

    // So muda o nome (aparece no LCD no proximo contacto do Arduino)
    fun mudarNome(dispositivo: DispositivoGestao, nome: String) =
        executar("Nome alterado para $nome") {
            api.alterarDispositivo(dispositivo.id, AlteracaoDispositivo(nome = nome))
        }

    // A chave do Arduino deixa de funcionar
    fun revogar(dispositivo: DispositivoGestao) =
        executar("${dispositivo.nome} revogada") {
            api.alterarDispositivo(dispositivo.id, AlteracaoDispositivo(estado = "revogado"))
        }

    // Apaga a porta e todo o historico dela
    fun apagar(dispositivo: DispositivoGestao) =
        executar("${dispositivo.nome} apagada") {
            api.apagarDispositivo(dispositivo.id)
        }

    // Revogada -> ativa
    fun reativar(dispositivo: DispositivoGestao) =
        executar("${dispositivo.nome} reativada") {
            api.alterarDispositivo(dispositivo.id, AlteracaoDispositivo(estado = "ativo"))
        }
}

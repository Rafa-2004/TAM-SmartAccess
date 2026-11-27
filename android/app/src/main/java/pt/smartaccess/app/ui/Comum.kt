// Pecas comuns a todos os ecras: estado de carregamento, ViewModel base, atualizacao periodica e textos.
package pt.smartaccess.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import pt.smartaccess.app.data.Permissao
import pt.smartaccess.app.data.mensagemErro
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Estado de um ecra com dados da API: a carregar, erro (com mensagem) ou sucesso (com os dados)
sealed interface Carregamento<out T> {
    data object ACarregar : Carregamento<Nothing>
    data class Erro(val mensagem: String) : Carregamento<Nothing>
    data class Sucesso<T>(val dados: T) : Carregamento<T>
}

// Se ja havia dados, um erro numa atualizacao automatica mantem-nos no ecra
// Faz o pedido e guarda o resultado no estado do ecra.
suspend fun <T> MutableStateFlow<Carregamento<T>>.carregarDe(pedido: suspend () -> T) {
    try {
        // Correu bem: guarda os dados
        value = Carregamento.Sucesso(pedido())
    // Cancelamento da coroutine (ex.: ecra fechado): deixa passar, nao e um erro para mostrar
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Falhou: so mostra o erro se ainda nao havia dados no ecra
        if (value !is Carregamento.Sucesso) value = Carregamento.Erro(e.mensagemErro())
    }
}

// ViewModel com atualizacao dos dados e acoes que mostram uma mensagem no fim
abstract class BaseViewModel : ViewModel() {
    // Mensagens para o Snackbar (SharedFlow: cada mensagem e mostrada uma vez)
    private val _mensagens = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val mensagens: SharedFlow<String> = _mensagens

    // Cada ViewModel diz aqui como vai buscar os seus dados a API
    abstract suspend fun atualizar()

    // Volta a pedir os dados (botao 'Tentar de novo', mudanca de filtros)
    fun recarregar() {
        // Coroutine: corre em segundo plano sem congelar o ecra; e cancelada se o ViewModel for destruido
        viewModelScope.launch { atualizar() }
    }

    // Envia um texto para o Snackbar do ecra
    protected fun mostrarMensagem(texto: String) {
        _mensagens.tryEmit(texto)
    }

    // Corre uma acao (aprovar, apagar...) numa coroutine:
    // se correr bem mostra 'sucesso' e recarrega os dados; se falhar mostra o erro.
    protected fun executar(sucesso: String, acao: suspend () -> Unit) {
        // Coroutine: corre em segundo plano sem congelar o ecra; e cancelada se o ViewModel for destruido
        viewModelScope.launch {
            try {
                // Executa a funcao recebida como parametro (ex.: o pedido a API)
                acao()
                // Mensagem de sucesso na barra de baixo
                mostrarMensagem(sucesso)
                // Volta a carregar os dados para o ecra mostrar a alteracao
                atualizar()
            // Cancelamento da coroutine (ex.: ecra fechado): deixa passar, nao e um erro para mostrar
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Correu mal: mostra o erro
                mostrarMensagem(e.mensagemErro())
            }
        }
    }
}

// Mostra no Snackbar cada mensagem que o ViewModel enviar
@Composable
fun MostrarMensagens(viewModel: BaseViewModel, snackbar: SnackbarHostState) {
    // Corre enquanto o ecra existir: recebe cada mensagem e mostra-a
    LaunchedEffect(viewModel) {
        viewModel.mensagens.collect { snackbar.showSnackbar(it) }
    }
}

// Repete a acao enquanto o ecra esta visivel (para quando a app vai para segundo plano)
@Composable
fun AtualizarPeriodicamente(intervaloMs: Long, acao: suspend () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // So corre enquanto a app esta visivel: para em segundo plano e recomeca ao voltar
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Repete para sempre: faz a acao, espera, faz de novo...
            while (true) {
                // Executa a funcao recebida como parametro (ex.: o pedido a API)
                acao()
                delay(intervaloMs)
            }
        }
    }
}

// Desenha o estado: roda a girar, erro com 'Tentar de novo', ou o conteudo com os dados
@Composable
fun <T> ConteudoCarregado(
    estado: Carregamento<T>,
    aoTentarDeNovo: () -> Unit,
    conteudo: @Composable (T) -> Unit,
) {
    // Desenha conforme o estado
    when (estado) {
        Carregamento.ACarregar -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is Carregamento.Erro -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Nao foi possivel carregar", style = MaterialTheme.typography.titleMedium)
            Text(estado.mensagem, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = aoTentarDeNovo) { Text("Tentar de novo") }
        }
        is Carregamento.Sucesso -> conteudo(estado.dados)
    }
}

// Texto centrado (nao e usada atualmente: os ecras usam EstadoVazio)
@Composable
fun MensagemVazia(texto: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

// Abreviaturas dos dias; o indice e dia_semana - 1 (1 = segunda ... 7 = domingo)
val DIAS_SEMANA = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sab", "Dom")

private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

// Data ISO 8601 da API -> "dd/MM/yyyy HH:mm" no fuso do telemovel (se falhar, devolve o texto original)
fun formatarData(iso: String): String =
    // runCatching: se der erro (ex.: data invalida), usa o valor do getOrDefault em vez de rebentar
    runCatching {
        // Le a data ISO e converte-a para o fuso do telemovel
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(FORMATO_DATA)
    }.getOrDefault(iso)

// Codigo do motivo de um registo -> texto para mostrar
fun textoMotivo(motivo: String?): String = when (motivo) {
    "cartao_desconhecido" -> "Cartao desconhecido"
    "cartao_pendente" -> "Cartao por aprovar"
    "cartao_revogado" -> "Cartao revogado"
    "fora_do_horario" -> "Fora do horario"
    "aula_a_decorrer" -> "Aula de outro docente a decorrer"
    "inicio_aula" -> "Inicio de aula"
    "fim_aula" -> "Fim de aula"
    "abertura_remota" -> "Abertura remota"
    null -> ""
    else -> motivo
}

// Perfis possiveis (o admin nao aparece nas escolhas: so pode haver um)
val PERFIS = listOf("admin", "funcionario", "docente")

// Codigo do perfil -> nome para mostrar
fun textoPerfil(perfil: String?): String = when (perfil) {
    "admin" -> "Docente administrador"
    "funcionario" -> "Funcionario"
    "docente" -> "Docente"
    null -> ""
    else -> perfil
}

// Regras de cada perfil, em texto (dialogos e ecra de horarios)
fun descricaoPerfil(perfil: String): String = when (perfil) {
    "admin" -> "Unico com conta na app. Entra a qualquer hora e fecha qualquer aula"
    "funcionario" -> "So cartao. Horario alargado, pode fechar qualquer aula"
    "docente" -> "So cartao. Entra no seu horario e fecha a aula que abriu (ou uma aberta pela app)"
    else -> ""
}

// Porque e que uma aula acabou (motivo_fim) -> texto
fun textoMotivoFim(motivo: String?): String = when (motivo) {
    "dono" -> "pelo dono da aula"
    "admin" -> "por um administrador"
    "funcionario" -> "por um funcionario"
    "docente" -> "por um docente (aula aberta pela app)"
    "offline" -> "sem rede, no proprio Arduino"
    "sistema" -> "pelo sistema (porta reiniciada)"
    else -> ""
}

// Minutos entre duas datas ISO 8601 (fim a null = agora), ex.: "1 h 05 min"
fun duracao(inicio: String, fim: String?): String =
    // runCatching: se der erro (ex.: data invalida), usa o valor do getOrDefault em vez de rebentar
    runCatching {
        val de = OffsetDateTime.parse(inicio)
        val ate = fim?.let { OffsetDateTime.parse(it) } ?: OffsetDateTime.now()
        val minutos = java.time.Duration.between(de, ate).toMinutes().coerceAtLeast(0)
        if (minutos < 60) "$minutos min" else "${minutos / 60} h %02d min".format(minutos % 60)
    }.getOrDefault("")

// Horario em texto, ex.: "Seg 08:00-20:00, Ter 08:00-20:00"
fun textoHorario(permissoes: List<Permissao>): String =
    permissoes.sortedBy { it.diaSemana }.joinToString(", ") {
        "${DIAS_SEMANA[it.diaSemana - 1]} ${it.horaInicio}-${it.horaFim}"
    }

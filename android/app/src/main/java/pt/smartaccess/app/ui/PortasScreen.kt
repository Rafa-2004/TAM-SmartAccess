// Gestao > Portas: lista dos Arduinos registados e acoes sobre cada um.
package pt.smartaccess.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.smartaccess.app.data.DispositivoGestao

private const val MAX_NOME = 16   // o LCD do Arduino so tem 16 colunas

// Lista de portas (atualizada a cada 10 s) e os dialogos de aprovar, mudar nome, revogar e apagar
@Composable
fun PortasScreen(snackbar: SnackbarHostState, viewModel: PortasViewModel) {
    // Le o estado do ViewModel (a carregar / erro / dados). Quando muda, o ecra e redesenhado sozinho.
    // 'by' permite usar 'estado' diretamente, em vez de 'estado.value'.
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // Estado so deste ecra: vazio (null/false) = dialogo fechado; com valor = o dialogo aparece.
    // remember: o valor mantem-se entre redesenhos; mutableStateOf: mudar o valor redesenha o ecra.
    var aAprovar by remember { mutableStateOf<DispositivoGestao?>(null) }
    // Estado so deste ecra: vazio (null/false) = dialogo fechado; com valor = o dialogo aparece.
    // remember: o valor mantem-se entre redesenhos; mutableStateOf: mudar o valor redesenha o ecra.
    var aRenomear by remember { mutableStateOf<DispositivoGestao?>(null) }
    // Estado so deste ecra: vazio (null/false) = dialogo fechado; com valor = o dialogo aparece.
    // remember: o valor mantem-se entre redesenhos; mutableStateOf: mudar o valor redesenha o ecra.
    var aRevogar by remember { mutableStateOf<DispositivoGestao?>(null) }
    // Estado so deste ecra: vazio (null/false) = dialogo fechado; com valor = o dialogo aparece.
    // remember: o valor mantem-se entre redesenhos; mutableStateOf: mudar o valor redesenha o ecra.
    var aApagar by remember { mutableStateOf<DispositivoGestao?>(null) }

    // Mostra na barra de baixo (Snackbar) as mensagens do ViewModel (ex.: 'Cartao apagado' ou um erro)
    MostrarMensagens(viewModel, snackbar)
    // Volta a pedir os dados a API de X em X milissegundos, so enquanto o ecra esta visivel
    AtualizarPeriodicamente(10_000) { viewModel.atualizar() }

    // Mostra a roda, o erro ou os dados. viewModel::recarregar = funcao do botao 'Tentar de novo'.
    // O bloco { ... -> } so corre quando ja ha dados; o nome antes da seta sao os dados recebidos da API.
    ConteudoCarregado(estado, viewModel::recarregar) { portas ->
        if (portas.isEmpty()) {
            EstadoVazio(
                Icons.Default.Home, "Ainda nao ha portas",
                "Ligue um Arduino: ele regista-se sozinho e aparece aqui para aprovar.",
            )
        } else {
            // Lista com scroll: so desenha os elementos visiveis no ecra (rapido mesmo com listas grandes)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Um elemento por item; key = id para o Compose saber qual e qual quando a lista muda
                items(portas, key = { it.id }) { porta ->
                    CartaoPorta(
                        porta = porta,
                        // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
                        aoAprovar = { aAprovar = porta },
                        // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
                        aoRenomear = { aRenomear = porta },
                        // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
                        aoRevogar = { aRevogar = porta },
                        aoReativar = { viewModel.reativar(porta) },
                        // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
                        aoApagar = { aApagar = porta },
                    )
                }
            }
        }
    }

    // Dialogo: so e desenhado se a variavel tiver valor (?.let = 'se nao for null, faz isto com o valor')
    aAprovar?.let { porta ->
        DialogoNome(
            titulo = "Aprovar porta",
            texto = "O Arduino ${porta.mac ?: ""} passa a funcionar. De-lhe o nome da sala (aparece no LCD).",
            inicial = porta.nome,
            confirmar = "Aprovar",
            // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
            aoFechar = { aAprovar = null },
            aoConfirmar = {
                // Chama a acao no ViewModel (que faz o pedido a API)
                viewModel.aprovar(porta, it)
                // Fecha o dialogo
                aAprovar = null
            },
        )
    }

    // Dialogo: so e desenhado se a variavel tiver valor (?.let = 'se nao for null, faz isto com o valor')
    aRenomear?.let { porta ->
        DialogoNome(
            titulo = "Mudar nome",
            texto = "O novo nome aparece no LCD da porta no proximo contacto com a API.",
            inicial = porta.nome,
            confirmar = "Guardar",
            // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
            aoFechar = { aRenomear = null },
            aoConfirmar = {
                // Chama a acao no ViewModel (que faz o pedido a API)
                viewModel.mudarNome(porta, it)
                // Fecha o dialogo
                aRenomear = null
            },
        )
    }

    // Dialogo: so e desenhado se a variavel tiver valor (?.let = 'se nao for null, faz isto com o valor')
    aRevogar?.let { porta ->
        AlertDialog(
            // Tocar fora do dialogo ou no botao 'voltar' do telemovel
            onDismissRequest = { aRevogar = null },
            title = { Text("Revogar ${porta.nome}?") },
            text = {
                Text(
                    "A chave deste Arduino deixa de funcionar: a porta deixa de validar cartoes ate ser reativada. " +
                        "Use isto se o Arduino for roubado ou substituido."
                )
            },
            // Botao de confirmar: e aqui que a acao e feita (chama o ViewModel) e o dialogo fecha
            confirmButton = {
                TextButton(onClick = {
                    // Chama a acao no ViewModel (que faz o pedido a API)
                    viewModel.revogar(porta)
                    // Fecha o dialogo
                    aRevogar = null
                }) { Text("Revogar") }
            },
            // Botao Cancelar: so fecha o dialogo
            dismissButton = { TextButton(onClick = { aRevogar = null }) { Text("Cancelar") } },
        )
    }

    // Dialogo: so e desenhado se a variavel tiver valor (?.let = 'se nao for null, faz isto com o valor')
    aApagar?.let { porta ->
        AlertDialog(
            // Tocar fora do dialogo ou no botao 'voltar' do telemovel
            onDismissRequest = { aApagar = null },
            title = { Text("Apagar ${porta.nome}?") },
            text = {
                Text(
                    "A porta e TODO o historico dela (registos de acesso, aulas, passagens e alertas) sao apagados " +
                        "e nao ha volta atras. Se o Arduino continuar ligado, regista-se de novo e aparece como " +
                        "\"Por aprovar\". Para so impedir a porta de funcionar, use \"Revogar\"."
                )
            },
            // Botao de confirmar: e aqui que a acao e feita (chama o ViewModel) e o dialogo fecha
            confirmButton = {
                TextButton(onClick = {
                    // Chama a acao no ViewModel (que faz o pedido a API)
                    viewModel.apagar(porta)
                    // Fecha o dialogo
                    aApagar = null
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
            // Botao Cancelar: so fecha o dialogo
            dismissButton = { TextButton(onClick = { aApagar = null }) { Text("Cancelar") } },
        )
    }
}

// Cartao de uma porta com os botoes do seu estado
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoPorta(
    porta: DispositivoGestao,
    aoAprovar: () -> Unit,
    aoRenomear: () -> Unit,
    aoRevogar: () -> Unit,
    aoReativar: () -> Unit,
    aoApagar: () -> Unit,
) {
    // Atalho para as cores do tema (c.primary, c.error...)
    val c = MaterialTheme.colorScheme
    // Cartao branco de lista (Componentes.kt); 'destaque' pinta o fundo (ex.: ambar para os pendentes)
    CartaoLista(destaque = if (porta.estado == "pendente") c.tertiaryContainer.copy(alpha = 0.45f) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(porta.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                porta.mac?.let {
                    Text("MAC $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = c.onSurfaceVariant)
                }
            }
            when (porta.estado) {
                "pendente" -> Etiqueta("Por aprovar", c.tertiaryContainer, c.onTertiaryContainer)
                "revogado" -> Etiqueta("Revogada", c.errorContainer, c.onErrorContainer)
                else -> Indicador(if (porta.online) "Online" else "Offline", if (porta.online) Verde else c.error)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            porta.ultimoContacto?.let { "Ultimo contacto: ${formatarData(it)}" } ?: "Ainda sem contacto",
            style = MaterialTheme.typography.bodySmall,
            color = c.onSurfaceVariant,
        )
        when (porta.estado) {
            "ativo" -> Text(
                "${if (porta.portaAberta) "Aula a decorrer" else "Trancada"} - ${porta.pessoasDentro} pessoa(s) dentro",
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
            )
            "pendente" -> Text(
                "Arduino novo. Aprove-o e de-lhe o nome da sala para comecar a funcionar.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(12.dp))
        // FlowRow: como Row, mas passa os botoes para a linha seguinte quando nao cabem
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (porta.estado) {
                "pendente" -> Button(onClick = aoAprovar) { Text("Aprovar porta") }
                "ativo" -> {
                    OutlinedButton(onClick = aoRenomear) { Text("Mudar nome") }
                    TextButton(onClick = aoRevogar) { Text("Revogar") }
                }
                "revogado" -> OutlinedButton(onClick = aoReativar) { Text("Reativar") }
            }
            TextButton(onClick = aoApagar) { Text("Apagar", color = c.error) }
        }
    }
}

// Dialogo com o nome da sala (maximo 16 caracteres, a largura do LCD)
@Composable
private fun DialogoNome(
    titulo: String,
    texto: String,
    inicial: String,
    confirmar: String,
    aoFechar: () -> Unit,
    aoConfirmar: (String) -> Unit,
) {
    var nome by remember { mutableStateOf(inicial) }
    AlertDialog(
        // Tocar fora do dialogo ou no botao 'voltar' do telemovel
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column {
                Text(texto, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nome,
                    onValueChange = { if (it.length <= MAX_NOME) nome = it },
                    label = { Text("Nome (ex.: Sala 1.04)") },
                    supportingText = { Text("${nome.length}/$MAX_NOME") },
                    singleLine = true,
                )
            }
        },
        // Botao de confirmar: e aqui que a acao e feita (chama o ViewModel) e o dialogo fecha
        confirmButton = {
            TextButton(onClick = { aoConfirmar(nome.trim()) }, enabled = nome.isNotBlank()) { Text(confirmar) }
        },
        // Botao Cancelar: so fecha o dialogo
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

// Separador Registos: filtros e lista dos acessos (concedidos e negados).
package pt.smartaccess.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.smartaccess.app.data.Registo
import pt.smartaccess.app.data.Utilizador

// Filtros (periodo, resultado, pessoa) e lista de registos, atualizada a cada 10 s
@Composable
fun RegistosScreen(utilizador: Utilizador, snackbar: SnackbarHostState, viewModel: RegistosViewModel) {
    // Le o estado do ViewModel (a carregar / erro / dados). Quando muda, o ecra e redesenhado sozinho.
    // 'by' permite usar 'estado' diretamente, em vez de 'estado.value'.
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val filtros by viewModel.filtros.collectAsStateWithLifecycle()
    val utilizadores by viewModel.utilizadores.collectAsStateWithLifecycle()

    // Mostra na barra de baixo (Snackbar) as mensagens do ViewModel (ex.: 'Cartao apagado' ou um erro)
    MostrarMensagens(viewModel, snackbar)
    // Volta a pedir os dados a API de X em X milissegundos, so enquanto o ecra esta visivel
    AtualizarPeriodicamente(10_000) { viewModel.atualizar() }
    if (utilizador.admin) {
        LaunchedEffect(Unit) { viewModel.carregarUtilizadores() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Periodo.entries.forEach { periodo ->
                FilterChip(
                    selected = filtros.periodo == periodo,
                    onClick = { viewModel.alterarFiltros(filtros.copy(periodo = periodo)) },
                    label = { Text(periodo.titulo) },
                )
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(null to "Todos", "concedido" to "Concedidos", "negado" to "Negados").forEach { (valor, titulo) ->
                FilterChip(
                    selected = filtros.resultado == valor,
                    onClick = { viewModel.alterarFiltros(filtros.copy(resultado = valor)) },
                    label = { Text(titulo) },
                )
            }
            if (utilizador.admin) {
                FiltroUtilizador(filtros.utilizador, utilizadores) {
                    // Chama a acao no ViewModel (que faz o pedido a API)
                    viewModel.alterarFiltros(filtros.copy(utilizador = it))
                }
            }
        }

        // Mostra a roda, o erro ou os dados. viewModel::recarregar = funcao do botao 'Tentar de novo'.
        // O bloco { ... -> } so corre quando ja ha dados; o nome antes da seta sao os dados recebidos da API.
        ConteudoCarregado(estado, viewModel::recarregar) { registos ->
            if (registos.isEmpty()) {
                EstadoVazio(Icons.AutoMirrored.Filled.List, "Sem registos", "Experimente outro periodo ou filtro.")
            } else {
                // Lista com scroll: so desenha os elementos visiveis no ecra (rapido mesmo com listas grandes)
                LazyColumn(Modifier.fillMaxSize()) {
                    // Um elemento por item; key = id para o Compose saber qual e qual quando a lista muda
                    items(registos, key = { it.id }) { registo ->
                        LinhaRegisto(registo)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// Chip que abre um menu para filtrar por pessoa
@Composable
private fun FiltroUtilizador(
    selecionado: Utilizador?,
    utilizadores: List<Utilizador>,
    aoEscolher: (Utilizador?) -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selecionado != null,
            onClick = { aberto = true },
            label = { Text(selecionado?.nome ?: "Pessoa") },
        )
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = {
                aoEscolher(null)
                aberto = false
            })
            utilizadores.forEach { u ->
                DropdownMenuItem(text = { Text(u.nome) }, onClick = {
                    aoEscolher(u)
                    aberto = false
                })
            }
        }
    }
}

// Uma linha da lista: quem, quando, onde, motivo, resultado e aviso (se houver)
@Composable
private fun LinhaRegisto(registo: Registo) {
    val detalhes = buildList {
        add(formatarData(registo.dataHora))
        add(registo.dispositivo)
        if (registo.origem == "remoto") add("remoto")
        registo.motivo?.let { add(textoMotivo(it)) }
        // As entradas ficam ligadas ao acesso que abriu a aula (inicio de aula ou abertura remota)
        if (registo.motivo == "inicio_aula" || registo.motivo == "abertura_remota") {
            add(if (registo.entradas > 0) "${registo.entradas} entrada(s) na aula" else "sem entradas detetadas")
        }
    }
    // Atalho para as cores do tema (c.primary, c.error...)
    val c = MaterialTheme.colorScheme
    ListItem(
        leadingContent = { Avatar(registo.utilizador ?: "?", perfil = null, tamanho = 40.dp) },
        headlineContent = { Text(registo.utilizador ?: "Cartao ${registo.cartaoUid}") },
        supportingContent = {
            Column {
                Text(detalhes.joinToString(" - "), color = c.onSurfaceVariant)
                registo.aviso?.let { Text("Aviso: $it", color = c.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        trailingContent = {
            if (registo.concedido) Etiqueta("Concedido", c.secondaryContainer, c.onSecondaryContainer)
            else Etiqueta("Negado", c.errorContainer, c.onErrorContainer)
        },
    )
}

// Dialogo de horario semanal (horario proprio de um cartao ou horario de um perfil).
package pt.smartaccess.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.smartaccess.app.data.Permissao

// Estado de um dia no dialogo: selecionado, hora de inicio e hora de fim
private data class LinhaHorario(val ativo: Boolean, val inicio: String, val fim: String)

// Hora valida: HH:MM entre 00:00 e 23:59
private val FORMATO_HORA = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

// Uma janela por dia da semana (se houver varias no mesmo dia, so a primeira e editada).
// Usado nos horarios proprios dos cartoes e nos horarios de cada perfil.
@Composable
fun DialogoHorarios(
    titulo: String,
    permissoes: List<Permissao>,
    aviso: String?,
    aoFechar: () -> Unit,
    aoGuardar: (List<Permissao>) -> Unit,
) {
    // As 7 linhas (segunda a domingo), preenchidas com o horario atual;
    // os dias sem janela comecam desmarcados, com 08:00-20:00 por omissao
    val linhas = remember {
        mutableStateListOf<LinhaHorario>().apply {
            for (dia in 1..7) {
                val p = permissoes.firstOrNull { it.diaSemana == dia }
                add(LinhaHorario(p != null, p?.horaInicio ?: "08:00", p?.horaFim ?: "20:00"))
            }
        }
    }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        // Tocar fora do dialogo ou no botao 'voltar' do telemovel
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                aviso?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                linhas.forEachIndexed { i, linha ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = linha.ativo, onCheckedChange = { linhas[i] = linha.copy(ativo = it) })
                        Text(DIAS_SEMANA[i], modifier = Modifier.width(36.dp))
                        CampoHora(linha.inicio, linha.ativo) { linhas[i] = linha.copy(inicio = it) }
                        Text(" - ")
                        CampoHora(linha.fim, linha.ativo) { linhas[i] = linha.copy(fim = it) }
                    }
                }
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        // Botao de confirmar: e aqui que a acao e feita (chama o ViewModel) e o dialogo fecha
        confirmButton = {
            TextButton(onClick = {
                // So os dias selecionados (com o indice do dia)
                val ativas = linhas.withIndex().filter { it.value.ativo }
                // Primeiro dia com a hora mal escrita ou o fim antes do inicio
                val invalida = ativas.firstOrNull { (_, l) ->
                    !FORMATO_HORA.matches(l.inicio) || !FORMATO_HORA.matches(l.fim) || l.fim <= l.inicio
                }
                if (invalida != null) {
                    erro = "${DIAS_SEMANA[invalida.index]}: use HH:MM e um fim depois do inicio"
                } else {
                    // Converte as linhas em Permissao (indice 0 -> dia 1 = segunda) e devolve-as
                    aoGuardar(ativas.map { (i, l) -> Permissao(i + 1, l.inicio, l.fim) })
                }
            }) { Text("Guardar") }
        },
        // Botao Cancelar: so fecha o dialogo
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

// Campo de hora HH:MM (desativado se o dia nao estiver selecionado)
@Composable
private fun CampoHora(valor: String, ativo: Boolean, aoMudar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { if (it.length <= 5) aoMudar(it) },
        enabled = ativo,
        singleLine = true,
        placeholder = { Text("HH:MM") },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.width(76.dp),
    )
}

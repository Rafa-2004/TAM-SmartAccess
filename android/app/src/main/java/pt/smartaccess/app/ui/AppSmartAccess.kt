// Raiz da interface: arranque, login / primeiro administrador e o ecra principal com separadores.
package pt.smartaccess.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.smartaccess.app.data.Utilizador

// Mostra o ecra certo conforme a sessao (o Compose redesenha sozinho quando a sessao muda)
// sessaoViewModel = viewModel(): cria o ViewModel na primeira vez e devolve o mesmo nas seguintes
// (sobrevive a rodar o ecra).
@Composable
fun AppSmartAccess(sessaoViewModel: SessaoViewModel = viewModel()) {
    // sessaoViewModel.sessao e um StateFlow: um valor que vai mudando ao longo do tempo
    // (a carregar -> sem sessao -> ativa...).
    // collectAsStateWithLifecycle() transforma-o num estado do Compose: sempre que a sessao muda,
    // esta funcao volta a correr e o ecra e redesenhado. 'WithLifecycle': so ouve com a app visivel.
    // 'by' (delegacao): permite usar 'sessao' diretamente como valor, em vez de 'sessao.value'.
    val sessao by sessaoViewModel.sessao.collectAsStateWithLifecycle()

    // Escolhe o ecra conforme a sessao. 'val s' guarda o valor para se poder usar s.dados no caso Ativa.
    when (val s = sessao) {
        // Ainda a ler a sessao guardada no telemovel: roda a girar no centro
        EstadoSessao.ACarregar -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        // Ninguem com sessao: primeiro arranque ou login
        EstadoSessao.SemSessao -> {
            // Primeiro arranque (ainda nao ha administrador): cria-o em vez de fazer login
            // null = ainda nao se sabe (ou sem rede), true = falta criar o administrador, false = login
            val precisaAdmin by sessaoViewModel.precisaAdmin.collectAsStateWithLifecycle()
            // Corre uma vez quando este bloco aparece: pergunta a API se ja ha administrador
            LaunchedEffect(Unit) { sessaoViewModel.verificarPrimeiroArranque() }
            // '== true' porque precisaAdmin pode ser null: nesse caso mostra o login
            if (precisaAdmin == true) PrimeiroAdminScreen(sessaoViewModel) else LoginScreen(sessaoViewModel)
        }
        // Sessao ativa: abre a app. sessaoViewModel::sair passa a funcao sair (sem a chamar) ao botao Sair.
        is EstadoSessao.Ativa -> EcraPrincipal(s.dados.utilizador, aoSair = sessaoViewModel::sair)
    }
}

// Separadores da barra de baixo; soAdmin = so aparece ao administrador
private enum class Separador(val titulo: String, val icone: ImageVector, val soAdmin: Boolean) {
    PAINEL("Painel", Icons.Default.Home, false),
    AULAS("Aulas", Icons.Default.DateRange, false),
    REGISTOS("Registos", Icons.AutoMirrored.Filled.List, false),
    GESTAO("Gestao", Icons.Default.Settings, true),
    ALERTAS("Alertas", Icons.Default.Warning, true),
}

// Ecra principal: barra de topo (titulo, utilizador, Sair), barra de separadores e o separador ativo
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcraPrincipal(utilizador: Utilizador, aoSair: () -> Unit) {
    // So os separadores que este utilizador pode ver (Gestao e Alertas so para o admin)
    val separadores = Separador.entries.filter { utilizador.admin || !it.soAdmin }
    // Separador escolhido. rememberSaveable: guarda o valor entre redesenhos e tambem ao rodar o ecra
    var atual by rememberSaveable { mutableStateOf(Separador.PAINEL) }
    // Controla a barra de mensagens em baixo (Snackbar). remember: criado uma vez e mantido
    val snackbar = remember { SnackbarHostState() }
    // Os ViewModels ficam associados ao utilizador, para nao mostrar dados de outra sessao
    val chave = "u${utilizador.id}"

    // Scaffold: estrutura base de um ecra Material (barra de topo, barra de baixo, Snackbar e conteudo)
    Scaffold(
        // Barra de topo: titulo do separador, nome e perfil do utilizador e botao Sair
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(atual.titulo)
                        Text(
                            "${utilizador.nome} (${textoPerfil(utilizador.perfil)})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = aoSair) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sair")
                    }
                },
            )
        },
        // Barra de navegacao em baixo: um botao por separador
        bottomBar = {
            NavigationBar {
                separadores.forEach { separador ->
                    NavigationBarItem(
                        selected = separador == atual,
                        // Mudar 'atual' faz o Compose redesenhar e mostrar o outro separador
                        onClick = { atual = separador },
                        icon = { Icon(separador.icone, contentDescription = null) },
                        label = { Text(separador.titulo) },
                    )
                }
            }
        },
        // Onde as mensagens (Snackbar) aparecem
        snackbarHost = { SnackbarHost(snackbar) },
    // margens: espaco das barras, para o conteudo nao ficar escondido por baixo delas
    ) { margens ->
        Box(Modifier.fillMaxSize().padding(margens)) {
            // Mostra o ecra do separador ativo; viewModel(key = ...) cria ou recupera o ViewModel de cada um
            when (atual) {
                Separador.PAINEL -> PainelScreen(utilizador, snackbar, viewModel(key = "painel-$chave"))
                Separador.AULAS -> AulasScreen(utilizador, snackbar, viewModel(key = "aulas-$chave"))
                Separador.REGISTOS -> RegistosScreen(utilizador, snackbar, viewModel(key = "registos-$chave"))
                Separador.GESTAO -> GestaoScreen(utilizador, snackbar, chave)
                Separador.ALERTAS -> AlertasScreen(snackbar, viewModel(key = "alertas-$chave"))
            }
        }
    }
}

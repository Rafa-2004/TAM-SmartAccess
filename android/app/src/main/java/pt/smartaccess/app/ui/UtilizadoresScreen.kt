// Gestao > Pessoas: lista de pessoas (docentes, funcionarios e o administrador) e acoes.
package pt.smartaccess.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.smartaccess.app.data.AlteracaoUtilizador
import pt.smartaccess.app.data.Cartao
import pt.smartaccess.app.data.NovoUtilizador
import pt.smartaccess.app.data.Utilizador

// Pesquisa, filtros por perfil, lista, botao 'Nova pessoa' e dialogos (atualiza a cada 30 s)
@Composable
fun UtilizadoresScreen(eu: Utilizador, snackbar: SnackbarHostState, viewModel: UtilizadoresViewModel) {
    // Le o estado do ViewModel (a carregar / erro / dados). Quando muda, o ecra e redesenhado sozinho.
    // 'by' permite usar 'estado' diretamente, em vez de 'estado.value'.
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val cartoes by viewModel.cartoes.collectAsStateWithLifecycle()
    // Estado so deste ecra: vazio (null/false) = dialogo fechado; com valor = o dialogo aparece.
    // remember: o valor mantem-se entre redesenhos; mutableStateOf: mudar o valor redesenha o ecra.
    var aCriar by remember { mutableStateOf(false) }
    // Estado so deste ecra: vazio (null/false) = dialogo fechado; com valor = o dialogo aparece.
    // remember: o valor mantem-se entre redesenhos; mutableStateOf: mudar o valor redesenha o ecra.
    var aMudarPerfil by remember { mutableStateOf<Utilizador?>(null) }
    // Estado so deste ecra: vazio (null/false) = dialogo fechado; com valor = o dialogo aparece.
    // remember: o valor mantem-se entre redesenhos; mutableStateOf: mudar o valor redesenha o ecra.
    var aAssociar by remember { mutableStateOf<Utilizador?>(null) }
    // Estado so deste ecra: vazio (null/false) = dialogo fechado; com valor = o dialogo aparece.
    // remember: o valor mantem-se entre redesenhos; mutableStateOf: mudar o valor redesenha o ecra.
    var aRemover by remember { mutableStateOf<Utilizador?>(null) }
    // Texto da pesquisa (rememberSaveable: mantem-se mesmo ao rodar o ecra)
    var pesquisa by rememberSaveable { mutableStateOf("") }
    var perfilFiltro by rememberSaveable { mutableStateOf<String?>(null) }

    // Mostra na barra de baixo (Snackbar) as mensagens do ViewModel (ex.: 'Cartao apagado' ou um erro)
    MostrarMensagens(viewModel, snackbar)
    // Volta a pedir os dados a API de X em X milissegundos, so enquanto o ecra esta visivel
    AtualizarPeriodicamente(30_000) { viewModel.atualizar() }

    Box(Modifier.fillMaxSize()) {
        // Mostra a roda, o erro ou os dados. viewModel::recarregar = funcao do botao 'Tentar de novo'.
        // O bloco { ... -> } so corre quando ja ha dados; o nome antes da seta sao os dados recebidos da API.
        ConteudoCarregado(estado, viewModel::recarregar) { utilizadores ->
            // Aplica o filtro escolhido e o texto da pesquisa (contem() ignora acentos e maiusculas)
            val visiveis = utilizadores.filter {
                (perfilFiltro == null || it.perfil == perfilFiltro) && (pesquisa.isBlank() || contem(it.nome, pesquisa))
            }
            Column(Modifier.fillMaxSize()) {
                CampoPesquisa(
                    pesquisa, { pesquisa = it }, "Pesquisar pelo nome",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (listOf<String?>(null) + PERFIS).forEach { p ->
                        val total = utilizadores.count { p == null || it.perfil == p }
                        FilterChip(
                            selected = perfilFiltro == p,
                            onClick = { perfilFiltro = p },
                            label = { Text("${if (p == null) "Todos" else textoPerfil(p).replace("Docente administrador", "Admin")} ($total)") },
                        )
                    }
                }
                if (visiveis.isEmpty()) {
                    EstadoVazio(
                        Icons.Default.Person,
                        if (pesquisa.isBlank()) "Ainda nao ha pessoas" else "Ninguem com o nome \"$pesquisa\"",
                        "Use \"Nova pessoa\" para criar um docente ou funcionario.",
                    )
                } else {
                    // Lista com scroll: so desenha os elementos visiveis no ecra (rapido mesmo com listas grandes)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Um elemento por item; key = id para o Compose saber qual e qual quando a lista muda
                        items(visiveis, key = { it.id }) { u ->
                            CartaoPessoa(
                                pessoa = u,
                                cartoes = cartoes.filter { it.utilizadorId == u.id },
                                souEu = u.id == eu.id,
                                // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
                                aoAssociar = { aAssociar = u },
                                // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
                                aoMudarPerfil = { aMudarPerfil = u },
                                // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
                                aoRemover = { aRemover = u },
                            )
                        }
                    }
                }
            }

            // Dialogo: so e desenhado se a variavel tiver valor (?.let = 'se nao for null, faz isto com o valor')
            aAssociar?.let { pessoa ->
                DialogoAssociarCartao(
                    titulo = "Associar cartao",
                    uidFixo = null,
                    pessoas = utilizadores,
                    pessoaInicial = pessoa,
                    // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
                    aoFechar = { aAssociar = null },
                    aoConfirmar = { uid, escolha ->
                        // Chama a acao no ViewModel (que faz o pedido a API)
                        viewModel.associar(uid, escolha)
                        // Fecha o dialogo
                        aAssociar = null
                    },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { aCriar = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Nova pessoa") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (aCriar) {
        DialogoNovoUtilizador(
            // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
            aoFechar = { aCriar = false },
            aoCriar = {
                // Chama a acao no ViewModel (que faz o pedido a API)
                viewModel.criar(it)
                aCriar = false
            },
        )
    }

    // Dialogo: so e desenhado se a variavel tiver valor (?.let = 'se nao for null, faz isto com o valor')
    aRemover?.let { u ->
        val ativos = cartoes.count { it.utilizadorId == u.id && it.estado == "ativo" }
        AlertDialog(
            // Tocar fora do dialogo ou no botao 'voltar' do telemovel
            onDismissRequest = { aRemover = null },
            title = { Text("Remover ${u.nome}?") },
            text = {
                Text(
                    (if (ativos > 0) "Os $ativos cartao(oes) ativo(s) desta pessoa sao revogados e deixam de abrir as portas. " else "") +
                        "A pessoa deixa de aparecer na lista, mas o historico de acessos e aulas continua a mostrar o nome."
                )
            },
            // Botao de confirmar: e aqui que a acao e feita (chama o ViewModel) e o dialogo fecha
            confirmButton = {
                TextButton(onClick = {
                    // Chama a acao no ViewModel (que faz o pedido a API)
                    viewModel.remover(u)
                    // Fecha o dialogo
                    aRemover = null
                }) { Text("Remover", color = MaterialTheme.colorScheme.error) }
            },
            // Botao Cancelar: so fecha o dialogo
            dismissButton = { TextButton(onClick = { aRemover = null }) { Text("Cancelar") } },
        )
    }

    // Dialogo: so e desenhado se a variavel tiver valor (?.let = 'se nao for null, faz isto com o valor')
    aMudarPerfil?.let { u ->
        DialogoPerfil(
            utilizador = u,
            // Callback: carregar neste botao so guarda o item na variavel -> aparece o dialogo de confirmacao
            aoFechar = { aMudarPerfil = null },
            aoGuardar = {
                // Chama a acao no ViewModel (que faz o pedido a API)
                viewModel.mudarPerfil(u, it)
                // Fecha o dialogo
                aMudarPerfil = null
            },
        )
    }
}

// Cartao de uma pessoa: nome, perfil, os seus cartoes e botoes
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoPessoa(
    pessoa: Utilizador,
    cartoes: List<Cartao>,
    souEu: Boolean,
    aoAssociar: () -> Unit,
    aoMudarPerfil: () -> Unit,
    aoRemover: () -> Unit,
) {
    CartaoLista {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(pessoa.nome, pessoa.perfil, tamanho = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pessoa.nome + if (souEu) " (eu)" else "", style = MaterialTheme.typography.titleMedium)
                Text(
                    pessoa.email?.let { "Conta na app: $it" } ?: "Sem conta na app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EtiquetaPerfil(pessoa.perfil)
        }

        Spacer(Modifier.height(12.dp))
        if (cartoes.isEmpty()) {
            Text(
                "Ainda sem cartao: nao consegue abrir as salas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            // FlowRow: como Row, mas passa os botoes para a linha seguinte quando nao cabem
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cartoes.forEach { c ->
                    val cor = when (c.estado) {
                        "ativo" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                        "revogado" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                    }
                    Etiqueta(c.uid + if (c.estado == "revogado") " (revogado)" else "", cor.first, cor.second)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // FlowRow: como Row, mas passa os botoes para a linha seguinte quando nao cabem
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = aoAssociar) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Associar cartao")
            }
            // O administrador (so ha um) nao muda o proprio perfil nem pode ser removido
            if (!souEu && !pessoa.admin) {
                TextButton(onClick = aoMudarPerfil) { Text("Mudar perfil") }
                TextButton(onClick = aoRemover) { Text("Remover", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

// Botoes de opcao para escolher o perfil
@Composable
private fun EscolhaPerfil(escolhido: String, aoEscolher: (String) -> Unit) {
    // So existe um administrador: nao se pode criar outro nem promover ninguem a admin
    PERFIS.filter { it != "admin" }.forEach { perfil ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = escolhido == perfil, onClick = { aoEscolher(perfil) })
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = escolhido == perfil, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(textoPerfil(perfil))
                Text(descricaoPerfil(perfil), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// Email e palavra-passe do login na app: so para administradores
// (Ja nao e alcancavel: o perfil admin deixou de ser opcao, so existe um administrador)
@Composable
private fun CamposConta(email: String, password: String, aoMudarEmail: (String) -> Unit, aoMudarPassword: (String) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text("Conta na app (so administradores)", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        email, { aoMudarEmail(it.trim()) },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    OutlinedTextField(
        password, aoMudarPassword,
        label = { Text("Palavra-passe") },
        supportingText = { Text("Pelo menos 6 caracteres") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

// Email com @ e palavra-passe com 6 ou mais caracteres
private fun contaValida(email: String, password: String) = "@" in email && password.length >= 6

// Passar a administrador pede email e palavra-passe se a pessoa ainda nao tem conta;
// deixar de ser administrador apaga a conta (fica so o nome do cartao)
@Composable
private fun DialogoPerfil(utilizador: Utilizador, aoFechar: () -> Unit, aoGuardar: (AlteracaoUtilizador) -> Unit) {
    var perfil by remember { mutableStateOf(utilizador.perfil) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val precisaConta = perfil == "admin" && utilizador.email == null
    val valido = perfil != utilizador.perfil && (!precisaConta || contaValida(email, password))

    AlertDialog(
        // Tocar fora do dialogo ou no botao 'voltar' do telemovel
        onDismissRequest = aoFechar,
        title = { Text("Perfil de ${utilizador.nome}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                EscolhaPerfil(perfil) { perfil = it }
                if (precisaConta) CamposConta(email, password, { email = it }, { password = it })
                if (utilizador.admin && perfil != "admin") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A conta na app de ${utilizador.nome} vai ser apagada (fica so o cartao).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        // Botao de confirmar: e aqui que a acao e feita (chama o ViewModel) e o dialogo fecha
        confirmButton = {
            TextButton(
                onClick = {
                    aoGuardar(
                        if (precisaConta) AlteracaoUtilizador(perfil = perfil, email = email, password = password)
                        else AlteracaoUtilizador(perfil = perfil)
                    )
                },
                enabled = valido,
            ) { Text("Guardar") }
        },
        // Botao Cancelar: so fecha o dialogo
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

// Dialogo para criar uma pessoa (nome + perfil)
@Composable
private fun DialogoNovoUtilizador(aoFechar: () -> Unit, aoCriar: (NovoUtilizador) -> Unit) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var perfil by remember { mutableStateOf("docente") }
    val admin = perfil == "admin"
    val valido = nome.isNotBlank() && (!admin || contaValida(email, password))

    AlertDialog(
        // Tocar fora do dialogo ou no botao 'voltar' do telemovel
        onDismissRequest = aoFechar,
        title = { Text("Nova pessoa") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Perfil", style = MaterialTheme.typography.labelLarge)
                EscolhaPerfil(perfil) { perfil = it }
                if (admin) {
                    CamposConta(email, password, { email = it }, { password = it })
                } else {
                    Text(
                        "Depois associe-lhe um cartao em Gestao > Cartoes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        // Botao de confirmar: e aqui que a acao e feita (chama o ViewModel) e o dialogo fecha
        confirmButton = {
            TextButton(
                onClick = {
                    aoCriar(
                        if (admin) NovoUtilizador(nome.trim(), perfil, email, password)
                        else NovoUtilizador(nome.trim(), perfil)
                    )
                },
                enabled = valido,
            ) { Text("Criar") }
        },
        // Botao Cancelar: so fecha o dialogo
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

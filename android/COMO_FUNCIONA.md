# App Android SmartAccess — como funciona

Documento de referência da aplicação Android: arquitetura, o que faz cada ficheiro e cada função.
Para instalar e correr a app, ver o [README.md](README.md).

---

## 1. Visão geral

A app é o painel de gestão do sistema SmartAccess. **Só o administrador tem conta** (existe um único
administrador); docentes e funcionários são apenas um nome associado a um ou mais cartões RFID.

A app **nunca fala com o Arduino nem com a base de dados**: tudo passa pela API REST (`api/index.py`,
alojada no Vercel). A app pede dados, mostra-os e envia ações (aprovar, revogar, abrir porta...).

```
 App Android  ──HTTPS + JWT──▶  API (Flask, Vercel)  ──▶  PostgreSQL (servidor da escola)
                                       ▲
 Arduino (porta) ──HTTPS + chave───────┘
```

### Tecnologias

| O quê | Versão / detalhe |
|---|---|
| Linguagem | Kotlin |
| Interface | Jetpack Compose + Material 3 (BOM `2026.02.01`) |
| Arquitetura | MVVM (ecrã Compose → ViewModel → API) |
| Pedidos HTTP | Retrofit `2.11.0` + conversor Gson, OkHttp `4.12.0` |
| Guardar a sessão | DataStore Preferences `1.1.7` |
| Concorrência | Coroutines (`viewModelScope`, `suspend`) e `StateFlow` |
| Build | Android Gradle Plugin `9.4.1`, Gradle `9.7.1`, Java 17 |
| SDK | `minSdk 26` (Android 8.0), `compileSdk`/`targetSdk 37` |

### Arquitetura MVVM

```
┌──────────────────┐  observa StateFlow   ┌──────────────────┐   suspend fun    ┌─────────────────┐
│ Ecrã (Compose)   │ ◀─────────────────── │ ViewModel        │ ───────────────▶ │ SmartAccessApi  │
│ ex.: PainelScreen│ ───────────────────▶ │ ex.: PainelVM    │ ◀─────────────── │ (Retrofit)      │
└──────────────────┘   chama funções      └──────────────────┘    dados (JSON)  └─────────────────┘
```

- **Ecrã (`*Screen.kt`)**: só desenha. Lê o estado do ViewModel com `collectAsStateWithLifecycle()` e,
  quando o utilizador carrega num botão, chama uma função do ViewModel. Sempre que o estado muda, o Compose
  volta a desenhar o ecrã sozinho ("recomposição").
- **ViewModel (`*ViewModel.kt`)**: guarda o estado (`MutableStateFlow`) e faz os pedidos à API numa
  coroutine (`viewModelScope.launch`). Sobrevive a rotações do ecrã.
- **Dados (`data/`)**: a interface Retrofit, os modelos JSON e o armazenamento da sessão.

### O que acontece ao abrir a app

1. `MainActivity.onCreate` aplica o tema (`TemaSmartAccess`) e mostra `AppSmartAccess()`.
2. `SessaoViewModel` lê do DataStore o token e o utilizador guardados.
3. `AppSmartAccess` escolhe o ecrã conforme o estado da sessão:
   - a carregar → roda de progresso;
   - **sem sessão** → pergunta à API se já existe administrador (`GET api/setup`):
     - não existe → **`PrimeiroAdminScreen`** (criar o administrador e entrar);
     - existe (ou sem rede) → **`LoginScreen`**;
   - **sessão ativa** → `EcraPrincipal` (barra de separadores em baixo).
4. Cada separador cria o seu ViewModel, que começa a pedir dados à API periodicamente
   (`AtualizarPeriodicamente`), só enquanto a app está visível.

### Estrutura de pastas

```
android/
├── build.gradle.kts, settings.gradle.kts, gradle.properties   configuração do Gradle
├── local.properties                                         (local, não vai para o Git) SDK e api.url
└── app/
    ├── build.gradle.kts                                     dependências, SDK, URL da API
    └── src/
        ├── debug/AndroidManifest.xml                        permite HTTP sem TLS (só debug)
        └── main/
            ├── AndroidManifest.xml                          permissão de Internet, atividade principal
            ├── res/values/strings.xml, themes.xml            nome da app e tema base
            └── java/pt/smartaccess/app/
                ├── MainActivity.kt
                ├── data/   Modelos.kt, SmartAccessApi.kt, SessaoStore.kt
                └── ui/     ecrãs, ViewModels, diálogos e componentes
```

---

## 2. Configuração (Gradle e manifestos)

### `settings.gradle.kts`
Define os repositórios (Google, Maven Central, Gradle Plugin Portal), o nome do projeto (`SmartAccess`)
e inclui o módulo `:app`.

### `build.gradle.kts` (raiz)
Declara as versões dos plugins: Android Application `9.4.1` e o plugin do compilador Compose `2.2.10`
(`apply false`: só são aplicados no módulo `app`).

### `gradle.properties`
Memória do Gradle (`-Xmx2048m`), AndroidX ativo, classes `R` não transitivas e estilo de código Kotlin oficial.

### `app/build.gradle.kts`
- Lê o `local.properties` (que não vai para o Git).
- `namespace`/`applicationId`: `pt.smartaccess.app`; `minSdk 26`, `compileSdk`/`targetSdk 37`.
- **`buildConfigField("String", "API_URL", ...)`**: cria a constante `BuildConfig.API_URL`. O valor vem de
  `api.url` no `local.properties`; se não existir, usa `https://tam-smartaccess-api.vercel.app/`.
- `buildFeatures { compose = true; buildConfig = true }` liga o Compose e a geração do `BuildConfig`.
- `release { optimization { enable = false } }`: a versão release não é minificada.
- Dependências: Compose (UI, Material 3, ícones, ferramentas de pré-visualização), `core-ktx`,
  `activity-compose`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, DataStore, Retrofit,
  Gson e OkHttp.

### `app/src/main/AndroidManifest.xml`
- `android.permission.INTERNET`: sem isto a app não consegue fazer pedidos.
- Regista `MainActivity` como atividade de arranque (`MAIN` + `LAUNCHER`).

### `app/src/debug/AndroidManifest.xml`
Só na versão de debug: `usesCleartextTraffic="true"`, que permite usar uma API local por HTTP
(ex.: `http://10.0.2.2:8000/` no emulador).

### `res/values/strings.xml` e `themes.xml`
Nome da app (`SmartAccess`) e o tema base do Android (`Theme.Material.Light.NoActionBar`), que só se vê
durante o arranque; o resto do aspeto vem do Compose (`Tema.kt`).

---

## 3. `MainActivity.kt`

| Função | O que faz |
|---|---|
| `onCreate(savedInstanceState)` | Ponto de entrada da app. `enableEdgeToEdge()` desenha por baixo das barras do sistema; `setContent { TemaSmartAccess { AppSmartAccess() } }` substitui os layouts XML pelo Compose e mostra a app com o tema SmartAccess. |

---

## 4. Pasta `data/`

### 4.1 `Modelos.kt` — as classes que representam o JSON da API

Cada `data class` corresponde a um objeto JSON. O Gson converte automaticamente JSON ↔ Kotlin;
`@SerializedName("porta_aberta")` liga o nome do JSON (`snake_case`) ao nome em Kotlin (`portaAberta`).
Campos `null` num pedido **não são enviados** (o Gson omite-os), o que permite alterar só alguns campos
num `PATCH`.

| Classe | Usada em | Campos e significado |
|---|---|---|
| `Utilizador` | `GET api/users`, `api/me`, login | `id`, `nome`, `email` (só o admin tem), `perfil` (`admin`/`funcionario`/`docente`). Propriedade calculada `admin` = `perfil == "admin"`. |
| `PedidoLogin` | `POST api/auth/login` | `email`, `password`. |
| `RespostaLogin` | login e criação do admin | `token` (JWT) e `utilizador`. |
| `EstadoInicial` | `GET api/setup` | `precisaAdmin`: `true` se ainda não existe administrador. |
| `NovoAdministrador` | `POST api/setup` | `nome`, `email`, `password` do primeiro (e único) administrador. |
| `NovoUtilizador` | `POST api/users` | `nome`, `perfil` (+ `email`/`password` só para admin). |
| `AlteracaoUtilizador` | `PATCH api/users/{id}` | Campos opcionais: `nome`, `perfil`, `email`, `password`. |
| `NovoCartao` | `POST api/cards` | `uid` e `utilizadorId` (cartão adicionado à mão, já associado). |
| `Dispositivo` | `GET api/status` (Painel) | Porta ativa: `portaAberta`, `pessoasDentro`, `ultimoContacto`, `online`, `ultimoAcesso`, `aula` (aula a decorrer ou `null`). |
| `AulaAtual` | dentro de `Dispositivo` | Aula a decorrer: `inicio`, `dono`, `donoPerfil`, `entradas`, `saidas`. |
| `DispositivoGestao` | `GET api/devices` (Gestão > Portas) | Todas as portas, incluindo pendentes e revogadas: `mac`, `estado`, `criadoEm`, etc. |
| `AlteracaoDispositivo` | `PATCH api/devices/{id}` | `estado` (`ativo`/`revogado`) e/ou `nome`. |
| `Aula` | `GET api/classes` | Histórico: `dispositivo`, `dono`, `cartaoUid`, `inicio`, `fim`, `fechadaPor`, `motivoFim`, `entradas`, `saidas`, `entradasNaoDetetadas`. Propriedade `aDecorrer` = `fim == null`. |
| `HorariosPerfis` | `GET api/schedules` | Listas de `Permissao` do `docente` e do `funcionario`. |
| `UltimoAcesso` | dentro de `Dispositivo` | Último acesso à porta: `dataHora`, `resultado`, `origem`, `utilizador`, `motivo`. |
| `PedidoAbertura` | `POST api/door/unlock` | `dispositivoId` da porta a abrir. |
| `RespostaAbertura` | resposta do unlock | `comandoId` (para acompanhar) e nome do `dispositivo`. |
| `Comando` | `GET api/door/commands/{id}` | `estado`: `pendente`, `executado` ou `expirado`. |
| `Registo` | `GET api/logs` | Registo de acesso: data, cartão, utilizador, `origem` (cartão/remoto), `resultado`, `motivo`, porta, `entradas` e `aviso` (ex.: saídas não detetadas). Propriedade `concedido`. |
| `Permissao` | horários | Uma janela: `diaSemana` (1 = segunda … 7 = domingo), `horaInicio`, `horaFim` (`"HH:MM"`). |
| `Cartao` | `GET api/cards` | `uid`, `estado` (`pendente`/`ativo`/`revogado`), dono (`utilizadorId`, `utilizador`, `utilizadorPerfil`), `criadoEm` e `permissoes` próprias. |
| `AlteracaoCartao` | `PATCH api/cards/{id}` | `estado` e/ou `utilizadorId`. |
| `Alerta` | `GET api/alerts` | `tipo`, `descricao`, `dataHora`, `resolvido`, `dispositivo`. |
| `AlteracaoAlerta` | `PATCH api/alerts/{id}` | `resolvido`. |

### 4.2 `SmartAccessApi.kt` — a ligação à API

É uma **interface Retrofit**: cada função anotada com `@GET`/`@POST`/`@PATCH`/`@PUT`/`@DELETE` corresponde
a um endpoint. O Retrofit gera a implementação. As funções são `suspend`, ou seja, são chamadas dentro de
uma coroutine e não bloqueiam a interface enquanto esperam pela resposta.

| Função | Pedido | Para quê |
|---|---|---|
| `login(pedido)` | `POST api/auth/login` | Entrar; devolve token + utilizador. |
| `estadoInicial()` | `GET api/setup` | Saber se ainda é preciso criar o administrador. |
| `criarAdministrador(novo)` | `POST api/setup` | Criar o único administrador e receber logo a sessão. |
| `eu()` | `GET api/me` | Dados atuais do utilizador autenticado. |
| `estado()` | `GET api/status` | Portas ativas com estado, aula e último acesso (Painel). |
| `abrirPorta(pedido)` | `POST api/door/unlock` | Pedir a abertura remota (cria um comando). |
| `comando(id)` | `GET api/door/commands/{id}` | Ver se o Arduino já executou o comando. |
| `registos(desde, ate, utilizadorId, resultado, limite)` | `GET api/logs` | Registos de acesso com filtros (parâmetros `null` não são enviados). |
| `cartoes()` | `GET api/cards` | Lista de cartões. |
| `criarCartao(novo)` | `POST api/cards` | Adicionar um cartão pelo UID, já associado. |
| `alterarCartao(id, alteracao)` | `PATCH api/cards/{id}` | Aprovar, revogar, reativar ou trocar o dono. |
| `apagarCartao(id)` | `DELETE api/cards/{id}` | Apagar o cartão (o histórico guarda só o UID). |
| `definirPermissoes(id, permissoes)` | `PUT api/cards/{id}/permissions` | Horário próprio do cartão (lista vazia = volta ao horário do perfil). |
| `alertas()` | `GET api/alerts` | Lista de alertas. |
| `alterarAlerta(id, alteracao)` | `PATCH api/alerts/{id}` | Marcar como resolvido. |
| `utilizadores()` | `GET api/users` | Lista de pessoas. |
| `criarUtilizador(novo)` | `POST api/users` | Criar docente ou funcionário. |
| `alterarUtilizador(id, alteracao)` | `PATCH api/users/{id}` | Mudar nome ou perfil. |
| `removerUtilizador(id)` | `DELETE api/users/{id}` | Remover pessoa (cartões revogados, histórico mantido). |
| `dispositivos()` | `GET api/devices` | Todas as portas (Gestão > Portas). |
| `alterarDispositivo(id, alteracao)` | `PATCH api/devices/{id}` | Aprovar, mudar nome, revogar, reativar. |
| `apagarDispositivo(id)` | `DELETE api/devices/{id}` | Apagar a porta e todo o histórico dela. |
| `aulas(limite)` | `GET api/classes` | Histórico de aulas. |
| `horarios()` | `GET api/schedules` | Horários por perfil. |
| `definirHorario(perfil, horario)` | `PUT api/schedules/{perfil}` | Guardar o horário de `docente` ou `funcionario`. |

**`companion object`** (partilhado por toda a app):

| Membro | O que faz |
|---|---|
| `token` | Token JWT da sessão atual. É definido pelo `SessaoViewModel` sempre que a sessão muda. `@Volatile` garante que todas as threads veem o valor mais recente. |
| `aoExpirarSessao` | Função chamada quando a API responde **401** a um pedido autenticado (token expirado ou utilizador removido). O `SessaoViewModel` liga-a a `sair()`. |
| `instancia` | Cria o cliente uma única vez (`by lazy`). O **interceptor OkHttp** acrescenta `Authorization: Bearer <token>` a todos os pedidos e, se a resposta for 401 e havia token, chama `aoExpirarSessao`. O Retrofit usa `BuildConfig.API_URL` como endereço base e o Gson para converter o JSON. O OkHttp reaproveita as ligações HTTPS entre pedidos. |

**`Throwable.mensagemErro()`** (função de extensão): transforma qualquer erro numa mensagem legível:
- `HttpException` (a API respondeu com erro) → lê o campo `detail` do JSON (`{"detail": "..."}`); se o
  `detail` não for texto, mostra "Dados invalidos"; se nem houver JSON, mostra "Erro 500", por exemplo;
- `IOException` (sem rede, timeout) → "Sem ligacao ao servidor";
- qualquer outro → a mensagem da exceção.

### 4.3 `SessaoStore.kt` — guardar a sessão no telemóvel

Usa o **DataStore Preferences** (ficheiro `sessao`, privado da app) para que o login sobreviva a fechar e
voltar a abrir a app.

| Membro | O que faz |
|---|---|
| `DadosSessao` | `data class` com o `token` e o `utilizador`. |
| `dados` | `Flow<DadosSessao?>`: emite a sessão guardada sempre que muda; `null` se não houver token. |
| `guardar(sessao)` | Escreve token, id, nome, email e perfil (`suspend`). |
| `limpar()` | Apaga tudo (logout). |
| `TOKEN`, `ID`, `NOME`, `EMAIL`, `PERFIL` | Chaves das preferências. |

---

## 5. Pasta `ui/` — base comum

### 5.1 `Comum.kt` — estado, ViewModel base e textos

| Elemento | O que faz |
|---|---|
| `Carregamento<T>` | Estado de um ecrã com dados da API: `ACarregar`, `Erro(mensagem)` ou `Sucesso(dados)`. `sealed interface`: o `when` obriga a tratar os três casos. |
| `MutableStateFlow<Carregamento<T>>.carregarDe(pedido)` | Faz o pedido e guarda `Sucesso`. Se falhar e **já havia dados**, mantém-nos no ecrã (uma falha momentânea numa atualização automática não apaga a lista); só mostra `Erro` se ainda não havia nada. `CancellationException` é relançada para não estragar o cancelamento das coroutines. |
| `BaseViewModel` | Classe base de todos os ViewModels dos separadores. |
| ↳ `mensagens` | `SharedFlow<String>` com as mensagens a mostrar no Snackbar (buffer de 4). |
| ↳ `atualizar()` | Abstrata: cada ViewModel diz como vai buscar os seus dados. |
| ↳ `recarregar()` | Chama `atualizar()` numa coroutine (botão "Tentar de novo", mudança de filtros). |
| ↳ `mostrarMensagem(texto)` | Envia uma mensagem para o Snackbar. |
| ↳ `executar(sucesso, acao)` | Corre uma ação (aprovar, apagar...). Se correr bem: mostra a mensagem de sucesso e recarrega os dados; se falhar: mostra o erro (`mensagemErro()`). |
| `MostrarMensagens(viewModel, snackbar)` | Composable que fica a ouvir `mensagens` e mostra cada uma no Snackbar. |
| `AtualizarPeriodicamente(intervaloMs, acao)` | Repete `acao` a cada `intervaloMs` **só enquanto o ecrã está visível** (`repeatOnLifecycle(STARTED)`): para quando a app vai para segundo plano e recomeça quando volta. |
| `ConteudoCarregado(estado, aoTentarDeNovo, conteudo)` | Desenha conforme o estado: roda de progresso, mensagem de erro com botão "Tentar de novo", ou o `conteudo` com os dados. |
| `MensagemVazia(texto)` | Texto centrado. **Não é usada atualmente** (substituída por `EstadoVazio`). |
| `DIAS_SEMANA` | `["Seg", ..., "Dom"]`; o índice é `diaSemana - 1`. |
| `formatarData(iso)` | Data ISO 8601 da API → `"dd/MM/yyyy HH:mm"` no fuso do telemóvel. Se não conseguir ler, devolve o texto original. |
| `textoMotivo(motivo)` | Código do motivo de um registo (`fora_do_horario`, `inicio_aula`...) → texto em português. |
| `PERFIS` | `["admin", "funcionario", "docente"]`. |
| `textoPerfil(perfil)` | Código → nome do perfil ("Docente administrador", "Funcionario", "Docente"). |
| `descricaoPerfil(perfil)` | Descrição das regras de cada perfil (usada nos diálogos e nos horários). |
| `textoMotivoFim(motivo)` | Porque é que uma aula acabou ("pelo dono da aula", "por um docente (aula aberta pela app)"...). |
| `duracao(inicio, fim)` | Duração entre duas datas ISO (fim `null` = agora): `"45 min"` ou `"1 h 05 min"`. |
| `textoHorario(permissoes)` | Lista de janelas → `"Seg 08:00-20:00, Ter 08:00-20:00"`. |

### 5.2 `Componentes.kt` — peças visuais reutilizadas

| Elemento | O que faz |
|---|---|
| `coresPerfil(perfil)` | Par (fundo, texto) da cor de cada perfil: admin azul, funcionário âmbar, docente verde-água. |
| `iniciais(nome)` | `"Ana Ferreira"` → `"AF"`; um só nome → as duas primeiras letras; vazio → `"?"`. |
| `Avatar(nome, perfil, tamanho)` | Círculo com as iniciais, na cor do perfil. |
| `Etiqueta(texto, fundo, cor)` | "Pílula" arredondada para estados ("Ativo", "Revogada", "Concedido"...). |
| `EtiquetaPerfil(perfil)` | Etiqueta do perfil em versão curta ("Admin" em vez de "Docente administrador"). |
| `Indicador(texto, cor)` | Ponto colorido + texto ("Online"/"Offline"). |
| `Verde` | Cor verde usada para "Online". |
| `CartaoLista(modifier, destaque, conteudo)` | Cartão das listas (fundo branco, cantos arredondados, sombra leve). `destaque` pinta o fundo (ex.: âmbar para pendentes), misturado com uma cor sólida para não se ver a sombra através. |
| `EstadoVazio(icone, titulo, texto)` | Ecrã de lista vazia com ícone num círculo, título e explicação. |
| `CampoPesquisa(valor, aoMudar, dica, modifier)` | Caixa de pesquisa arredondada com lupa e botão para limpar. |
| `contem(texto, pesquisa)` | Pesquisa que ignora acentos e maiúsculas (`"joao"` encontra `"João"`), usando `Normalizer` para retirar os acentos. |

### 5.3 `Tema.kt` — cores e formas

| Elemento | O que faz |
|---|---|
| `Claro` / `Escuro` | Paletas de cores Material 3: azul como cor principal, verde-água para o que está ativo, âmbar para o que espera aprovação, vermelho para erros. |
| `Formas` | Raios dos cantos (8 a 28 dp) usados por cartões, diálogos e botões. |
| `TemaSmartAccess(conteudo)` | Aplica a paleta clara ou escura conforme o modo do telemóvel (`isSystemInDarkTheme()`). |

---

## 6. Sessão, login e navegação

### 6.1 `SessaoViewModel.kt`

ViewModel da sessão (é um `AndroidViewModel`, porque o DataStore precisa do `Context` da aplicação).

| Elemento | O que faz |
|---|---|
| `EstadoSessao` | `ACarregar`, `SemSessao` ou `Ativa(dados)`. |
| `sessao` | `StateFlow<EstadoSessao>` calculado a partir de `store.dados`. Sempre que a sessão muda, também atualiza `SmartAccessApi.token`, para os pedidos levarem o token certo. `SharingStarted.Eagerly` começa a ler logo. |
| `aEntrar` | `true` enquanto um login ou a criação do administrador está a decorrer (o botão mostra uma roda e fica desativado). |
| `erroLogin` | Mensagem de erro a mostrar no ecrã de login / primeiro arranque. |
| `precisaAdmin` | `null` (ainda não se sabe ou sem rede), `true` (falta criar o administrador) ou `false`. |
| `init` | Liga `SmartAccessApi.aoExpirarSessao` a `sair()` e chama `atualizarPerfil()`. |
| `atualizarPerfil()` | Ao arrancar com sessão guardada, pede `api.eu()` e, se o nome/perfil mudou entretanto, guarda a versão nova. Sem rede, fica o que estava guardado. |
| `verificarPrimeiroArranque()` | Pede `api.estadoInicial()` e atualiza `precisaAdmin`. |
| `entrar(email, password)` | Valida que os campos estão preenchidos, faz `api.login` e guarda a sessão no DataStore. Ao guardar, `sessao` passa a `Ativa` e o ecrã muda sozinho para a app. |
| `criarAdministrador(nome, email, password, confirmacao)` | Valida (nome, email com `@`, palavra-passe com 6+ caracteres e igual à confirmação), chama `api.criarAdministrador` e guarda a sessão (entra logo). Se a API responder **409** (o administrador já existe, ex.: criado noutro telemóvel), volta ao login. |
| `sair()` | Apaga a sessão do DataStore → `sessao` passa a `SemSessao` → aparece o login. |
| `onCleared()` | Ao destruir o ViewModel, desliga `aoExpirarSessao`. |

### 6.2 `AppSmartAccess.kt`

| Elemento | O que faz |
|---|---|
| `AppSmartAccess(sessaoViewModel)` | Raiz da interface. Conforme `sessao`: roda de progresso, primeiro arranque/login, ou `EcraPrincipal`. No estado `SemSessao`, chama `verificarPrimeiroArranque()` (`LaunchedEffect`) e mostra `PrimeiroAdminScreen` se `precisaAdmin == true`, senão `LoginScreen`. |
| `Separador` (enum) | Os separadores da barra de baixo: Painel, Aulas, Registos, Gestão e Alertas, com título, ícone e se são só para admin (`soAdmin`). |
| `EcraPrincipal(utilizador, aoSair)` | `Scaffold` com: barra de topo (título do separador + nome e perfil do utilizador + botão Sair), barra de navegação em baixo (só os separadores permitidos) e o Snackbar. O separador ativo fica em `rememberSaveable` (sobrevive à rotação). Cada ViewModel é criado com uma `key` que inclui o id do utilizador (`"painel-u1"`), para nunca reaproveitar dados de outra sessão. |

### 6.3 `LoginScreen.kt`

| Função | O que faz |
|---|---|
| `LoginScreen(viewModel)` | Ecrã de entrada: logótipo (cadeado), campos de email e palavra-passe (escondida), mensagem de erro e botão "Entrar". O botão chama `viewModel.entrar` e, enquanto `aEntrar`, mostra uma roda e fica desativado. Os campos usam `rememberSaveable` para não se perderem ao rodar o ecrã. |

### 6.4 `PrimeiroAdminScreen.kt`

| Função | O que faz |
|---|---|
| `PrimeiroAdminScreen(viewModel)` | Ecrã do primeiro arranque (quando ainda não há administrador): explica que só existe um administrador e pede nome, email, palavra-passe e confirmação. O botão "Criar administrador e entrar" chama `viewModel.criarAdministrador`; se tudo correr bem, a app entra diretamente. Tem scroll, para caber em ecrãs pequenos com o teclado aberto. |

---

## 7. Separadores

### 7.1 Painel — `PainelScreen.kt` + `PainelViewModel.kt`

**`PainelViewModel`**

| Função | O que faz |
|---|---|
| `estado` | `Carregamento<List<Dispositivo>>` com as portas ativas. |
| `aAbrir` | `true` enquanto decorre uma abertura remota (desativa os botões). |
| `atualizar()` | `api.estado()` (`GET api/status`). |
| `abrirPorta(dispositivo)` | 1) `api.abrirPorta` cria o comando e mostra "Pedido enviado"; 2) consulta `api.comando(id)` **a cada 1 s, até 60 vezes**, enquanto o estado for `pendente`; 3) mostra "Porta aberta", "O dispositivo nao respondeu a tempo" (expirado) ou "Sem confirmacao do dispositivo"; 4) atualiza o painel. |

**`PainelScreen`**

| Função | O que faz |
|---|---|
| `PainelScreen(utilizador, snackbar, viewModel)` | Atualiza a cada **3 s**. Mostra o `Resumo` e um `CartaoDispositivo` por porta. Ao carregar em "Abrir porta a distancia", pede confirmação num `AlertDialog` antes de chamar `abrirPorta`. |
| `Resumo(dispositivos)` | Três números no topo: portas online/total, aulas a decorrer e pessoas dentro (soma de todas as portas). |
| `Numero(valor, rotulo, fundo, cor, modifier)` | Um desses quadrados coloridos. |
| `CartaoDispositivo(dispositivo, podeAbrir, aAbrir, aoAbrir)` | Cartão de uma porta: nome, hora do último contacto, Online/Offline, Aberta/Trancada, pessoas dentro; se houver aula: dono, desde quando, duração e entradas/saídas; o último acesso (quem, quando, motivo, concedido/negado); e, para o admin, o botão de abrir (desativado se a porta estiver offline ou já aberta). |

### 7.2 Aulas — `AulasScreen.kt` + `AulasViewModel.kt`

| Função | O que faz |
|---|---|
| `AulasViewModel.atualizar()` | `api.aulas(limite = 100)`: as últimas 100 aulas. |
| `AulasScreen(utilizador, snackbar, viewModel)` | Atualiza a cada **10 s** e mostra um `CartaoAula` por aula (ou um estado vazio). |
| `CartaoAula(aula)` | Dono (avatar), porta, etiqueta "A decorrer" ou a duração, início e fim (se acabou no mesmo dia mostra só a hora do fim), contadores de entradas e saídas, aviso de entradas não detetadas e quem/como fechou. As aulas a decorrer ficam com fundo verde-água. |
| `Contador(rotulo, valor, modifier)` | Caixa com um número grande e uma legenda. |

### 7.3 Registos — `RegistosScreen.kt` + `RegistosViewModel.kt`

**`RegistosViewModel`**

| Elemento | O que faz |
|---|---|
| `Periodo` (enum) | Hoje, 7 dias, 30 dias ou Tudo (`dias` = quantos dias para trás além de hoje). |
| `FiltrosRegistos` | Filtros ativos: período (por omissão 7 dias), resultado (`null`/`concedido`/`negado`) e utilizador. |
| `atualizar()` | Converte os filtros em parâmetros (`desde` = hoje menos N dias) e chama `api.registos`. |
| `alterarFiltros(novos)` | Guarda os filtros, mostra a roda de carregamento e recarrega. |
| `carregarUtilizadores()` | Carrega a lista de pessoas para o filtro (só uma vez). |

**`RegistosScreen`**

| Função | O que faz |
|---|---|
| `RegistosScreen(utilizador, snackbar, viewModel)` | Atualiza a cada **10 s**. Duas filas de filtros (período; Todos/Concedidos/Negados + pessoa, só para admin) e a lista de registos. |
| `FiltroUtilizador(selecionado, utilizadores, aoEscolher)` | Chip que abre um menu com "Todos" e cada pessoa. |
| `LinhaRegisto(registo)` | Uma linha: avatar, nome (ou "Cartao XXXX" se desconhecido), data, porta, "remoto", motivo, entradas da aula (nos inícios de aula), etiqueta Concedido/Negado e, se existir, o **aviso a vermelho** (ex.: sala fechada com pessoas contadas dentro). |

### 7.4 Gestão — `GestaoScreen.kt`

| Função | O que faz |
|---|---|
| `GestaoScreen(eu, snackbar, chave)` | Só para o administrador. Uma fila de separadores (Portas, Cartões, Pessoas, Horários) e o ecrã escolhido. O separador ativo é guardado com `rememberSaveable`. |

### 7.5 Gestão > Portas — `PortasScreen.kt` + `PortasViewModel.kt`

**`PortasViewModel`**

| Função | O que faz |
|---|---|
| `atualizar()` | `api.dispositivos()`: todas as portas (pendentes primeiro). |
| `aprovar(dispositivo, nome)` | Passa a porta a `ativo` e dá-lhe o nome da sala. |
| `mudarNome(dispositivo, nome)` | Muda só o nome (aparece no LCD no próximo contacto do Arduino). |
| `revogar(dispositivo)` | Passa a `revogado`: a chave do Arduino deixa de funcionar. |
| `reativar(dispositivo)` | Volta a `ativo`. |
| `apagar(dispositivo)` | Apaga a porta e todo o histórico (`api.apagarDispositivo`). |

**`PortasScreen`**

| Função | O que faz |
|---|---|
| `PortasScreen(snackbar, viewModel)` | Atualiza a cada **10 s**. Lista de `CartaoPorta` e os diálogos de aprovar, mudar nome, revogar e apagar (cada um guardado num estado `aAprovar`, `aRenomear`, `aRevogar`, `aApagar`). |
| `CartaoPorta(porta, ...)` | Nome, MAC, estado (Por aprovar / Revogada / Online / Offline), último contacto, aula e pessoas dentro, e os botões de cada estado: pendente → "Aprovar porta"; ativa → "Mudar nome" e "Revogar"; revogada → "Reativar"; sempre → "Apagar" (vermelho). Os botões usam `FlowRow` para passarem à linha seguinte em ecrãs estreitos. |
| `DialogoNome(titulo, texto, inicial, confirmar, aoFechar, aoConfirmar)` | Diálogo com um campo de texto para o nome, limitado a **16 caracteres** (`MAX_NOME`, a largura do LCD), com contador. |

### 7.6 Gestão > Cartões — `CartoesScreen.kt` + `CartoesViewModel.kt`

**`CartoesViewModel`**

| Função | O que faz |
|---|---|
| `atualizar()` | Cartões (`api.cartoes()`) e pessoas (`api.utilizadores()`, para o diálogo de associar). |
| `associar(uid, escolha, pendente)` | Associa um cartão a uma pessoa (ver `associarCartao`). |
| `revogar(cartao)` / `reativar(cartao)` | Muda o estado do cartão. |
| `apagar(cartao)` | Apaga o cartão. |
| `guardarHorarios(cartao, permissoes)` | Guarda o horário próprio do cartão. |

**`CartoesScreen`**

| Elemento | O que faz |
|---|---|
| `FiltroCartoes` (enum) | Todos, Por aprovar, Ativos, Revogados. |
| `AAssociar` | Pedido de associação: um cartão pendente (UID conhecido) ou `null` (cartão novo, UID escrito à mão). |
| `CartoesScreen(snackbar, viewModel)` | Atualiza a cada **15 s**. Pesquisa (UID ou nome), filtros com o número de pendentes, lista de cartões, botão "Adicionar cartao" e os diálogos de associar, revogar, apagar e horário. |
| `EtiquetaEstadoCartao(estado)` | Etiqueta Ativo / Revogado / Por aprovar com a cor certa. |
| `CartaoItem(cartao, donoPerfil, ...)` | UID, dono e perfil, estado, horário em vigor ("Horario proprio", "Horario do perfil" ou "Administrador: entra a qualquer hora") e botões: pendente → "Associar a alguem"; ativo → "Horario" (exceto admin) e "Revogar"; revogado → "Reativar"; sempre → "Apagar". |

### 7.7 Gestão > Pessoas — `UtilizadoresScreen.kt` + `UtilizadoresViewModel.kt`

**`UtilizadoresViewModel`**

| Função | O que faz |
|---|---|
| `atualizar()` | Pessoas e cartões (para mostrar os cartões de cada pessoa). |
| `criar(novo)` | Cria um docente ou funcionário. |
| `mudarPerfil(utilizador, alteracao)` | Troca entre docente e funcionário. |
| `remover(utilizador)` | Remove a pessoa (cartões revogados, histórico mantém o nome). |
| `associar(uid, escolha)` | Associa um cartão; se já existir um pendente com esse UID, aprova-o em vez de criar outro. |

**`UtilizadoresScreen`**

| Função | O que faz |
|---|---|
| `UtilizadoresScreen(eu, snackbar, viewModel)` | Atualiza a cada **30 s**. Pesquisa por nome, filtros por perfil (com contagem), lista de pessoas, botão "Nova pessoa" e os diálogos de associar cartão, remover e mudar perfil. |
| `CartaoPessoa(pessoa, cartoes, souEu, ...)` | Avatar, nome ("(eu)" no próprio), conta na app, perfil, os cartões da pessoa (com estado) ou o aviso "Ainda sem cartao", e botões "Associar cartao", "Mudar perfil" e "Remover" (estes dois não aparecem no administrador, que não pode mudar de perfil nem ser removido). |
| `EscolhaPerfil(escolhido, aoEscolher)` | Botões de opção para o perfil. **Não mostra "admin"**: só existe um administrador. |
| `CamposConta(...)` / `contaValida(...)` | Campos de email/palavra-passe de um administrador. **Já não são alcançáveis** (o perfil admin deixou de ser opção); ficaram de uma versão anterior. |
| `DialogoPerfil(utilizador, aoFechar, aoGuardar)` | Mudar o perfil de uma pessoa (docente ↔ funcionário). |
| `DialogoNovoUtilizador(aoFechar, aoCriar)` | Criar uma pessoa com nome e perfil; lembra que depois é preciso associar-lhe um cartão. |

### 7.8 Gestão > Horários — `HorariosScreen.kt` + `HorariosViewModel.kt`

| Função | O que faz |
|---|---|
| `HorariosViewModel.atualizar()` | `api.horarios()`. |
| `HorariosViewModel.guardar(perfil, horario)` | `api.definirHorario` para o perfil. |
| `HorariosScreen(snackbar, viewModel)` | Atualiza a cada **60 s**. Explicação das regras e um cartão por perfil (docente e funcionário), com o botão "Editar" que abre `DialogoHorarios`. |
| `CartaoHorario(perfil, horario, aoEditar)` | Nome e descrição do perfil e as janelas por dia; sem janelas, avisa a vermelho que esses cartões nunca abrem a sala. |

### 7.9 Alertas — `AlertasScreen.kt` + `AlertasViewModel.kt`

| Função | O que faz |
|---|---|
| `AlertasViewModel.atualizar()` | `api.alertas()` (a API verifica também as portas offline neste pedido). |
| `AlertasViewModel.resolver(alerta)` | Marca o alerta como resolvido. |
| `AlertasScreen(snackbar, viewModel)` | Atualiza a cada **10 s**. Lista de alertas ou "Sem alertas". |
| `tituloAlerta(tipo)` | Código → título: tentativas negadas, porta sem contacto, entrada com a sala fechada, porta nova por aprovar, saída não detetada. |
| `CartaoAlerta(alerta, aoResolver)` | Ícone (vermelho para problemas, âmbar para porta nova, cinzento se resolvido), título, descrição, data e botão "Resolver" ou etiqueta "Resolvido". |

---

## 8. Diálogos partilhados

### 8.1 `DialogoAssociarCartao.kt`

| Elemento | O que faz |
|---|---|
| `EscolhaPessoa` | A quem fica o cartão: `Existente(pessoa)` ou `Nova(nome, perfil)` (pessoa criada no momento). |
| `associarCartao(api, uid, escolha, cartaoPendenteId)` | Cria a pessoa se for nova; depois, se havia um cartão pendente, aprova-o para essa pessoa (`PATCH`), senão cria o cartão pelo UID (`POST`). |
| `FORMATO_UID` | UID válido: 8 a 20 caracteres hexadecimais maiúsculos. |
| `DialogoAssociarCartao(titulo, uidFixo, pessoas, pessoaInicial, aoFechar, aoConfirmar)` | Se `uidFixo` for `null`, mostra um campo para escrever o UID: aceita espaços, `:` e `-` e normaliza (ex.: `"8d f3 6e 69"` → `"8DF36E69"`), mostrando a validação por baixo. Depois escolhe-se a pessoa. "Associar" só fica ativo com UID válido e pessoa escolhida. |
| `SeletorPessoa(pessoas, escolha, aoEscolher)` | Pesquisa por nome (até 6 resultados) e, se o nome escrito não existir, oferece "Criar «nome»" como docente. |
| `PessoaEscolhida(escolha, aoMudarPerfil, aoTrocar)` | Mostra a pessoa escolhida com botão "Trocar"; para uma pessoa nova, permite escolher docente ou funcionário. |
| `LinhaPessoa(nome, perfil, modifier)` | Uma linha com avatar, nome e perfil (clicável na lista de resultados). |

### 8.2 `DialogoHorarios.kt`

| Elemento | O que faz |
|---|---|
| `LinhaHorario` | Estado de um dia no diálogo: ativo, hora de início e de fim. |
| `FORMATO_HORA` | Valida `HH:MM` entre `00:00` e `23:59`. |
| `DialogoHorarios(titulo, permissoes, aviso, aoFechar, aoGuardar)` | Sete linhas (segunda a domingo), cada uma com uma caixa de seleção e as duas horas (por omissão 08:00–20:00). Ao guardar, valida as linhas ativas (formato e fim depois do início) e devolve uma `Permissao` por dia ativo. Usado nos horários próprios dos cartões e nos horários por perfil. Só edita uma janela por dia. |
| `CampoHora(valor, ativo, aoMudar)` | Campo de hora com no máximo 5 caracteres, desativado se o dia não estiver selecionado. |

---

## 9. Fluxos completos

### Abrir uma porta à distância
1. Painel → "Abrir porta a distancia" → confirmação.
2. `PainelViewModel.abrirPorta` → `POST api/door/unlock` → a API cria um comando `pendente` (válido 1 min).
3. O Arduino consulta a API a cada ~2 s, recebe o comando, abre a porta e confirma (`comando_executado`);
   a API cria a aula em nome do administrador, sem cartão.
4. A app pergunta `GET api/door/commands/{id}` a cada 1 s; quando o estado é `executado` mostra
   "Porta aberta" e atualiza o Painel.

### Aprovar um cartão novo
1. Alguém passa um cartão desconhecido numa porta → a API guarda-o como `pendente`.
2. Cartões → filtro "Por aprovar" → "Associar a alguem".
3. `DialogoAssociarCartao` (UID já preenchido) → escolher ou criar a pessoa → "Associar".
4. `associarCartao` → `PATCH api/cards/{id}` com `estado = ativo` e o dono.

### Token expirado
1. Um pedido recebe **401**.
2. O interceptor OkHttp chama `aoExpirarSessao` → `SessaoViewModel.sair()` → DataStore apagado.
3. `sessao` passa a `SemSessao` → aparece o login.

### Primeiro arranque (depois de `database/reset.sql`)
1. Não há sessão → `verificarPrimeiroArranque()` → `GET api/setup` → `precisa_admin: true`.
2. `PrimeiroAdminScreen` → `POST api/setup` → a API cria o administrador e devolve token + utilizador.
3. A sessão é guardada → a app entra diretamente.

---

## 10. Intervalos de atualização

| Ecrã | Intervalo |
|---|---|
| Painel | 3 s |
| Confirmação de abertura remota | 1 s (até 60 tentativas) |
| Aulas, Registos, Portas, Alertas | 10 s |
| Cartões | 15 s |
| Pessoas | 30 s |
| Horários | 60 s |

Todos param quando a app vai para segundo plano (`AtualizarPeriodicamente` com `repeatOnLifecycle`).

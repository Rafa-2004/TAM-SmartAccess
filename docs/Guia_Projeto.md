# SmartAccess — Guia de estudo para a defesa

> Para estudar antes da defesa. O professor **não viu o código**: vai perguntar sobretudo *como funciona*,
> *porquê* e *o que acontece se...*. Este guia está organizado assim:
>
> 1. **Pitch** — explicar o projeto em 1 minuto
> 2. **Arquitetura** — as 4 partes e como falam entre si
> 3. **Guião da demonstração** — cada interação, o que se vê e o que acontece por trás
> 4. **Cada parte explicada** — Arduino, API, Base de Dados, App (com o código que vale a pena mostrar)
> 5. **Perguntas prováveis** e respostas
> 6. **Pontos fracos** — o que admitir com confiança
> 7. **Cábula final** + checklist antes de entrar
>
> Para diagramas mais detalhados ver [Explicacao_Projeto.md](Explicacao_Projeto.md).

---

## 1. Pitch (decorar a ideia, não as palavras)

> "O SmartAccess é um sistema de **controlo de acesso a salas de aula**. Em cada porta há um **Arduino
> UNO WiFi Rev2** com um **leitor RFID**, **dois sensores ultrassónicos** que contam quem entra e sai,
> um **LCD**, **LEDs** e um **buzzer**.
>
> Quando um professor passa o cartão, o Arduino pergunta a uma **API REST em Python (Flask)**, alojada
> no **Vercel**, se pode abrir. A API consulta a **base de dados PostgreSQL** — o cartão existe? está
> aprovado? está dentro do horário? — e responde *abrir*, *fechar* ou *negado*.
>
> Ao abrir começa uma **aula**: os sensores contam as entradas e saídas, e quando o professor volta a
> passar o cartão a sala fecha e fica registado o resumo.
>
> O **administrador** gere tudo numa **app Android** em Kotlin: aprova cartões e portas novas, associa
> cartões a pessoas, define horários, vê registos, aulas e alertas, e pode **abrir uma porta à distância**.
>
> A ideia central é que **o Arduino não decide nada: quem decide é sempre a API**."

---

## 2. Arquitetura

```
 ┌─────────────────────────┐                         ┌──────────────────────┐
 │  PORTA (Arduino)        │   HTTPS + x-api-key     │  API REST            │      SQL       ┌──────────────┐
 │  RFID, 2 sensores, LCD, ├────────────────────────▶│  Python + Flask      ├──────────────▶│ PostgreSQL   │
 │  LEDs, buzzer, Wi-Fi    │  (pergunta a cada 2 s)  │  alojada no Vercel   │  funções fn_   │ schema "tam" │
 └─────────────────────────┘                         │                      │  e proced. sp_ └──────────────┘
                                                     │                      │
 ┌─────────────────────────┐   HTTPS + JWT Bearer    │                      │
 │  APP Android (admin)    ├────────────────────────▶│                      │
 │  Kotlin + Compose       │                         └──────────────────────┘
 └─────────────────────────┘
```

| Parte | Tecnologia | Papel (uma frase) |
|---|---|---|
| **Arduino** | C++ (Arduino IDE), WiFiNINA, MFRC522, LiquidCrystal_I2C | Lê cartões e sensores, mostra o estado e **executa** o que a API manda |
| **API** | Python 3, Flask, psycopg, bcrypt, PyJWT — Vercel | **Decide** tudo: autentica, aplica as regras, cria alertas |
| **Base de dados** | PostgreSQL (servidor da escola), schema `tam` | **Guarda** os dados e **garante** as regras críticas (restrições, índices, trigger) |
| **App** | Kotlin, Jetpack Compose, MVVM, Retrofit, DataStore | **Interface** do administrador |

**Regras de comunicação que convém saber dizer:**
- O Arduino e a app **nunca falam diretamente** um com o outro nem com a BD — tudo passa pela API.
- A API **nunca contacta** o Arduino: é sempre o Arduino que pergunta (**polling** a cada 2 s).
- Tudo em **HTTPS**, dados em **JSON**.
- Duas formas de autenticação: o Arduino usa uma **chave por porta** (`x-api-key`), a app usa um **token JWT**.

---

## 3. Guião da demonstração

Ordem sugerida (conta uma história do zero até ao fim de uma aula). Para cada passo: **o que se faz**,
**o que se vê** (LCD / LEDs / buzzer / app) e **o que acontece por trás** — é isto que se explica
enquanto se demonstra.

> Dica: o **Monitor Série (9600 baud)** mostra um registo legível de tudo o que o Arduino faz
> (`[00:12:34] CARTAO Lido 8DF36E69`, `PASSAGEM ENTRADA | dentro: 1`...). Vale a pena tê-lo aberto no
> portátil durante a demonstração — mostra a "conversa" com a API.

### 3.0 Estado inicial — a porta em repouso

| Vê-se | LCD: `Sala 1.04` / `Passe o cartao` · LED **vermelho** aceso (trancada) |
|---|---|
| Por trás | No arranque o Arduino calibrou os sensores, ligou ao Wi-Fi, leu a sua chave da EEPROM e pediu a configuração (`GET /api/device/config`: nome da porta, intervalo, pessoas dentro, se havia aula a decorrer). Depois fica no `loop` a perguntar `GET /api/device/commands` a cada 2 s — isto serve também de **heartbeat** (a API atualiza o `ultimo_contacto`). |

### 3.1 (Opcional) Primeiro arranque da app — criar o administrador

| Faz-se | Abrir a app com a BD sem administrador |
|---|---|
| Vê-se | Ecrã **"Criar administrador"** (nome, email, palavra-passe) → entra logo |
| Por trás | A app pede `GET /api/setup` → `{"precisa_admin": true}`. `POST /api/setup` cria o admin (password com **bcrypt**) e devolve um **JWT**. Esta rota só funciona enquanto não houver admin; depois responde **409**. A BD tem um índice único que impede haver 2 admins. |

### 3.2 (Opcional) Uma porta nova — o Arduino regista-se sozinho

| Faz-se | Ligar um Arduino "novo" (ou escrever `R` no Monitor Série para apagar a chave) |
|---|---|
| Vê-se | LCD `A registar...` → `Aguarda aprovac.` / `PORTA-A1B2C3`. Na app aparece um **alerta "dispositivo novo"** e a porta em **Gestão › Portas** como *por aprovar*. Passar um cartão → `Acesso negado` / `Porta por aprovar`. |
| Faz-se | Na app: **Aprovar** a porta e dar-lhe o nome `Sala 1.04` |
| Vê-se | Em ≤10 s o LCD passa a `Sala 1.04` / `Passe o cartao` |
| Por trás | O Arduino envia `POST /api/device/register {"mac": "..."}`. A API gera uma **chave aleatória de 256 bits** (`secrets.token_hex(32)`), guarda na BD **só o SHA-256** e devolve a chave ao Arduino, que a grava na **EEPROM** (memória que não se apaga). Enquanto pendente, a API responde **403** e o Arduino ignora cartões. Quando o admin aprova (`PATCH /api/devices/{id}`), a próxima configuração devolve **200** com o nome. |

**Frase:** "Não há nada de identificação no código — o mesmo firmware serve para qualquer porta."

### 3.3 Cartão desconhecido → negado → aprovado → funciona

Este é o fluxo que mostra o ciclo de vida de um cartão.

**Passo A — passar um cartão nunca visto**

| Vê-se | Bip curto (lido) → `A verificar...` → `Acesso negado` / `Cartao novo` · LED vermelho pisca 3× · 3 bips graves |
|---|---|
| Por trás | `POST /api/access {"uid":"8DF36E69"}`. A API não encontra o cartão → **cria-o com estado `pendente`** e responde **403** com `motivo: cartao_desconhecido`. Fica um **registo de acesso negado** na BD. |

**Passo B — passar o mesmo cartão outra vez (ainda não aprovado)**

| Vê-se | `Acesso negado` / `Por aprovar` |
|---|---|
| Por trás | Agora o cartão existe mas está `pendente` → `motivo: cartao_pendente`. |

**Passo C — o administrador aprova na app**

| Faz-se | **Gestão › Pessoas**: criar `Prof. Silva` (perfil **docente**). **Gestão › Cartões**: o cartão aparece no topo como *pendente* → **associar** ao Prof. Silva e aprovar. |
|---|---|
| Por trás | `POST /api/users` e depois `PATCH /api/cards/{id} {"estado":"ativo","utilizador_id":...}`. A BD tem uma restrição: **um cartão ativo tem de ter dono** (`ativo_tem_dono`). |

**Passo D — passar o cartão** → abre (ver 3.4).

> Atenção: 3 negados seguidos na mesma porta em 5 minutos geram o alerta **"tentativas negadas"**.
> Na demonstração isto pode aparecer — e é bom, mostra outra funcionalidade.

### 3.4 Professor abre a sala (início da aula)

| Vê-se | Bip → LED **verde** acende, vermelho apaga → LCD `Aula iniciada` / `Prof. Silva`, depois `Aula: Prof. Silva` / `Dentro: 0`. Na app, **Painel**: a porta aparece *aberta* com a aula a decorrer. |
|---|---|
| Por trás | A API verifica: cartão existe → está `ativo` → não há aula aberta nesta porta → é admin **ou** está **dentro do horário** (docente: seg–sex 08:00–20:00 por omissão; ou o horário próprio do cartão). Tudo ok → grava **registo concedido** (`inicio_aula`), **cria uma aula** na tabela `aulas`, marca a porta aberta e responde `200 {"acao":"abrir", "nome", "perfil", ...}`. O Arduino só executa: LED verde, bip, LCD. |

> O texto no LCD depende do perfil: docente → `Aula iniciada`, funcionário → `Sala aberta`, admin → `Aula (admin)`.

### 3.5 Pessoas a entrar e a sair

| Faz-se | Passar a mão/corpo primeiro pelo sensor **A (fora)** e depois pelo **B (dentro)** |
|---|---|
| Vê-se | Bip **agudo** → `Entrou 1 pessoa` / `Dentro: 1`. No sentido contrário (B → A): bip **grave** → `Saiu 1 pessoa` / `Dentro: 0`. A app (Painel / Aulas) atualiza o número em poucos segundos. |
| Por trás | O Arduino segue o lado onde a pessoa está: **só A tapado → só B tapado = entrada**; **B → A = saída**; chegar ao A e voltar para trás **não conta**. A passagem conta logo no LCD mas vai para uma **fila** e só é enviada (`POST /api/device/events {"tipo":"passagem","sentido":"entrada"}`) quando a porta estiver livre há 0,7 s — porque um pedido HTTPS **bloqueia o Arduino** ~0,3–1 s e nesse tempo os sensores não seriam lidos. A API grava a passagem, atualiza `pessoas_dentro` e os contadores da aula, e devolve a **contagem oficial**. |

> Se os sensores falharem na demo: escrever `E` ou `S` no Monitor Série simula uma entrada/saída.
> Com a porta **trancada** os sensores são ignorados (quem passa no corredor não conta).

### 3.6 Outro professor tenta fechar a aula de um colega

| Faz-se | Passar o cartão de **outro docente** ativo durante a aula |
|---|---|
| Vê-se | `Aula a decorrer` / `So o dono fecha` |
| Por trás | A API vê que há aula aberta, o cartão não é do dono e o perfil é docente → `403 motivo: aula_a_decorrer`. Fica registado como negado, mas **não conta** para o alerta de tentativas (é um cartão válido). |

> Um **admin** ou **funcionário** fecha qualquer aula (`Fechada p/ admin` / `Fechada p/ func.`).

### 3.7 Professor fecha a sala (fim da aula)

| Faz-se | O dono volta a passar o cartão |
|---|---|
| Vê-se | LED **vermelho**, bip longo → `Sala fechada` / `Ate a proxima!`. No Monitor Série aparece o **resumo da aula** (duração, entradas, saídas). Na app, **Aulas**: a aula fica com fim, duração e contagens. |
| Por trás | Antes de pedir, o Arduino **envia primeiro as passagens em fila** (para a API fechar com a contagem certa). A API vê que é o **dono da aula** → `acao: fechar` (o dono fecha sempre, **mesmo fora do horário**). Fecha a aula, tranca a porta. **Se ainda havia pessoas contadas dentro**, cria o alerta **"saída não detetada"** e repõe a contagem a 0 — porque quem fecha a sala é o último a sair. |

### 3.8 Abrir a porta pela app (abertura remota)

| Faz-se | Na app, **Painel** › **Abrir** numa porta trancada |
|---|---|
| Vê-se | Snackbar `Pedido enviado para Sala 1.04` → em ~2 s o Arduino abre (LED verde, `Aula (admin)` / nome do admin) → snackbar `Porta aberta` |
| Por trás | 1) A app faz `POST /api/door/unlock` → a API **cria um comando** `pendente` na tabela `comandos` e responde **202** com o id. 2) O Arduino, no seu polling de 2 s (`GET /api/device/commands`), recebe o comando. 3) Executa e confirma com `POST /api/device/events {"tipo":"comando_executado"}` → a API grava um registo de origem **remoto** e cria a aula em nome do admin. 4) A app pergunta a cada 1 s `GET /api/door/commands/{id}` até ver `executado`. Um comando com mais de **1 minuto** **expira** (não abre a porta mais tarde por surpresa). |

> Uma aula aberta pela app (sem cartão) pode ser fechada **por qualquer cartão ativo**, incluindo um
> docente — senão só se fechava com a app ou com o cartão do admin.

### 3.9 Cartão revogado

| Faz-se | Na app, **Cartões** › revogar o cartão do Prof. Silva. Passar o cartão. |
|---|---|
| Vê-se | `Acesso negado` / `Cartao revogado` |
| Por trás | `motivo: cartao_revogado`. O cartão não é apagado (histórico fica); pode ser **reativado**. Remover uma **pessoa** também revoga todos os seus cartões (*soft delete*: o nome continua no histórico). |

### 3.10 Fora do horário

| Faz-se | Em **Gestão › Horários** mudar o horário do docente para excluir a hora atual (ou dar ao cartão um horário próprio). Passar o cartão. |
|---|---|
| Vê-se | `Acesso negado` / `Fora do horario`. O cartão do **admin** abre na mesma (entra a qualquer hora). |
| Por trás | Função da BD `fn_dentro_do_horario`: se o cartão tem **permissões próprias** usa essas; senão usa o **horário do perfil**. Compara com o dia da semana e a hora atuais em `Europe/Lisbon`. |

### 3.11 Sem rede

| Faz-se | Desligar o hotspot / router e passar um cartão |
|---|---|
| Vê-se | LCD `Sem rede` → `Acesso negado` / `Sem rede`. Na app, passado 1 minuto, aparece o alerta **"dispositivo offline"** e a porta como offline no Painel. |
| Por trás | **Sem a API nada abre** — decisão de segurança (*fail-secure*). Se a rede cair **a meio de uma aula**, a porta fica como estava (aberta), as passagens ficam guardadas na fila (até 32) e são enviadas quando a rede voltar; aí o Arduino pede de novo a configuração e, se a aula tiver sido fechada na API entretanto, tranca. |

### 3.12 Registos, Aulas e Alertas na app (para fechar a demo)

- **Registos**: todos os acessos (concedidos e negados, com o motivo), com filtros por data, pessoa e resultado.
- **Aulas**: histórico com quem abriu, quem fechou, duração, entradas e saídas.
- **Alertas**: tentativas negadas, porta offline, porta nova, saída não detetada, entrada sem acesso → marcar como resolvido.

### Resumo de todos os resultados possíveis ao passar um cartão

| Situação | Resposta da API | LCD |
|---|---|---|
| Cartão nunca visto | 403 `cartao_desconhecido` (fica pendente) | `Cartao novo` |
| Cartão pendente | 403 `cartao_pendente` | `Por aprovar` |
| Cartão revogado | 403 `cartao_revogado` | `Cartao revogado` |
| Sala fechada, ativo, dentro do horário (ou admin) | 200 `abrir` | `Aula iniciada` / `Sala aberta` / `Aula (admin)` |
| Sala fechada, fora do horário | 403 `fora_do_horario` | `Fora do horario` |
| Aula aberta, é o dono | 200 `fechar` | `Sala fechada` / `Ate a proxima!` |
| Aula aberta, admin ou funcionário | 200 `fechar` | `Fechada p/ admin` / `p/ func.` |
| Aula aberta pela app, qualquer cartão ativo | 200 `fechar` | `Fechada p/ doc.` ... |
| Aula aberta, outro docente | 403 `aula_a_decorrer` | `So o dono fecha` |
| Porta pendente / revogada | (nem pergunta à API) | `Porta por aprovar` / `Porta revogada` |
| Sem rede / API em baixo | erro de rede ou 5xx | `Sem rede` |

---

## 4. Cada parte explicada

### 4.1 Arduino — a porta (`arduino/arduino.ino`)

**Hardware e pinos**

| Componente | Ligação | Para quê |
|---|---|---|
| RFID **RC522** | SPI (SS=10, RST=0) | Ler o **UID** do cartão (13,56 MHz) |
| 2× **HC-SR04** | A: pinos 2/3 (fora) · B: pinos 4/5 (dentro) | Medir distância → detetar passagem e sentido |
| LED verde / vermelho | pinos 6 / 7 | Porta aberta / trancada |
| Buzzer | pino 8 | Feedback sonoro |
| LCD 16×2 | I2C, endereço 0x27 | Mensagens |
| Wi-Fi | módulo NINA integrado | HTTPS para a API |

> **Não há servo/fechadura:** o servo puxava demasiada corrente e fazia cair o Wi-Fi. O estado da porta
> é mostrado pelos LEDs. Numa instalação real usava-se uma fechadura elétrica com relé e fonte própria.

**Estrutura do programa**

- `setup()` — corre uma vez: pinos, RFID, LCD → `lerMemoria()` (chave da EEPROM) → `calibrarSensores()`
  → `ligarWiFi()` → `prepararDispositivo()` (regista-se se não tem chave; pede a configuração).
- `loop()` — corre sem parar, cada volta em poucos milissegundos:

```cpp
void loop() {
  // 1) Há cartão no leitor? -> lê o UID em hexadecimal e trata-o (pergunta à API)
  if (rfid.PICC_IsNewCardPresent() && rfid.PICC_ReadCardSerial()) { ... tratarCartao(uid); }

  // 2) Comandos escritos no Monitor Série (testes: UID, E, S, D, I, R)
  ...
  // 3) Lê os 2 sensores e deteta entradas/saídas
  verificarPassagem();

  // 4) Fala com a API SÓ com a porta livre (o pedido bloqueia o loop)
  if (primeiroSensor == 0 && (livre || forcar)) {
    if (há passagens na fila)        enviarPendentes(1);      // uma de cada vez
    else if (passaram 2 s)           consultarComandos();     // abertura remota + heartbeat
    else if (porta não aprovada)     prepararDispositivo();   // de 10 em 10 s
  }
  // 5) De 10 em 10 s verifica se o RC522 ainda responde; se não, reinicia-o
}
```

**Estados do dispositivo:** `SEM_REGISTO` → (regista-se) → `PENDENTE` → (admin aprova) → `ATIVO`;
o admin pode pôr `REVOGADO`. Se a API responder **401** (chave já não existe — porta apagada ou BD
limpa), o Arduino apaga a chave e regista-se de novo sozinho.

**Tratar um cartão — o Arduino só interpreta a resposta:**

```cpp
int estado = pedidoApi(true, "/api/access", "{\"uid\":\"" + uid + "\"}", resposta);
if (estado < 0 || estado >= 500)          -> "Sem rede"   (nada abre sem a API)
if (estado == 200 && acao == "abrir")     -> abrirSala()  (LED verde, bip, LCD)
if (estado == 200 && acao == "fechar")    -> fecharSala() + resumo
if (estado == 403)                        -> acessoNegado(motivo)
```

**Contagem de pessoas — o algoritmo (a parte mais "inteligente" do Arduino)**

```
     FORA            PORTA            DENTRO
   (corredor)    [A]       [B]        (sala)
   pessoa  ───▶  tapa A ─▶ só B   = ENTRADA
   pessoa  ◀───  só A  ◀─ tapa B  = SAÍDA
```

```cpp
// Durante a passagem: sempre que a pessoa fica SÓ no sensor do outro lado, atravessou
if (a != b) {                                  // só um dos sensores tapado
  char lado = a ? 'A' : 'B';
  if (lado != ladoAtual) {
    travessias[numTravessias++] = (lado == 'B') ? 'E' : 'S';   // A->B entrada, B->A saída
    ladoAtual = lado;
  }
}
// Fim: os dois livres durante 250 ms (ou 5 s sem mudanças = alguém parado na porta)
```

| Movimento | Resultado |
|---|---|
| A → B | 1 entrada |
| B → A | 1 saída |
| A → B → A (entra e volta logo a sair) | 1 entrada + 1 saída |
| tapa A e volta para trás | nada |

**Como se combate o ruído dos sensores:**
- **Calibração no arranque**: 10 medições com a porta livre → "ocupado" = mais perto que (referência − 45 cm).
  Por isso **a porta tem de estar livre quando o Arduino liga**.
- **2 leituras seguidas** iguais para mudar de estado (um eco falso isolado é ignorado).
- **10 ms entre os dois sensores** para o eco de um não chegar ao outro.
- Como funciona o HC-SR04: envia um pulso de ultrassom, mede quanto tempo o eco demora a voltar
  (`pulseIn`); **distância (cm) = tempo (µs) / 58** (ida e volta à velocidade do som).

**Fila de eventos pendentes:** porque um pedido HTTPS demora 0,3–1 s e **bloqueia** o programa, as
passagens contam-se logo no LCD mas guardam-se numa fila (até 32) e só se enviam quando a porta fica
livre há 0,7 s (ou ao fim de 15 s de movimento contínuo). A **API tem a contagem oficial**; o Arduino
acerta a sua com a resposta da API + o que ainda está na fila.

**Comunicação:**
- `WiFiSSLClient` — **HTTPS obrigatório** (o Vercel só aceita HTTPS).
- **Keep-alive**: a ligação fica aberta; só o 1.º pedido paga o handshake TLS (~1–2 s).
- **JSON sem biblioteca**: `lerCampoTexto` / `lerCampoNumero` procuram `"campo":` no texto da resposta
  (poupa RAM — o Arduino tem só 6 KB). Por isso a API envia JSON compacto, sem espaços.

**EEPROM** (memória que sobrevive a desligar): bytes 0–1 = assinatura `'S','A'`; bytes 2–65 = chave (64 hex).

**Se o Arduino reiniciar a meio de uma aula:** ao pedir a configuração, a API diz que há uma aula
a decorrer e o Arduino **retoma-a** (LED verde, dono, contagens).

---

### 4.2 API REST — o cérebro (`api/index.py`)

- **Python + Flask**, um só ficheiro, alojado no **Vercel** (serverless) com **deploy automático** a
  cada `git push` para a `main`.
- Variáveis de ambiente (segredos fora do código): `DATABASE_URL`, `JWT_SECRET`, `FUSO_HORARIO`.
- Erros sempre no formato `{"detail": "mensagem"}` com o código HTTP certo.

**Rotas principais**

| Quem usa | Rota | Para quê |
|---|---|---|
| Arduino | `POST /api/device/register` | Porta nova envia o MAC, recebe a chave |
| Arduino | `GET /api/device/config` | Nome, intervalo, pessoas dentro, aula a decorrer |
| Arduino | `POST /api/access` | **Decidir** abrir / fechar / negar |
| Arduino | `GET /api/device/commands` | Aberturas remotas pendentes (+ heartbeat) |
| Arduino | `POST /api/device/events` | Passagens e confirmação de comandos |
| App | `POST /api/auth/login` · `GET/POST /api/setup` | Login · criar o 1.º admin |
| App | `GET /api/status` | Estado das portas (Painel) |
| App | `POST /api/door/unlock` · `GET /api/door/commands/{id}` | Abertura remota |
| App | `GET /api/logs` · `GET /api/classes` | Registos · aulas |
| App | `/api/cards`, `/api/users`, `/api/devices`, `/api/schedules`, `/api/alerts` | Gestão (GET, POST, PATCH, PUT, DELETE) |

**O código mais importante do projeto — a decisão em `POST /api/access`:**

```python
cartao = SELECT * FROM fn_cartao_acesso(uid)
aula   = aula aberta nesta porta (ou None)
dono_da_aula = cartão pertence a quem abriu a aula

if cartao is None:                       # nunca visto -> fica pendente
    criar cartão pendente;  motivo = "cartao_desconhecido"
elif dono_da_aula:
    acao = "fechar"                      # o dono fecha sempre, mesmo fora do horário
elif cartao["estado"] == "pendente":     motivo = "cartao_pendente"
elif cartao["estado"] == "revogado":     motivo = "cartao_revogado"
elif aula and perfil in ("admin", "funcionario"):
    acao = "fechar"
elif aula and aula["cartao_uid"] is None:
    acao = "fechar"                      # aberta pela app: qualquer cartão ativo fecha
elif aula:                               motivo = "aula_a_decorrer"
elif perfil != "admin" and not dentro_do_horario(...):
                                         motivo = "fora_do_horario"
else:
    acao = "abrir"
```

Depois da decisão, **sempre**: grava um registo em `registos_acesso`; se abrir cria a aula; se fechar
fecha a aula (e cria o alerta se ficou gente contada dentro); se negar verifica os 3 negados seguidos.

**Autenticação do Arduino** — a chave nunca é guardada em claro:

```python
def dispositivo_atual():
    chave = request.headers.get("x-api-key", "")
    dispositivo = SELECT * FROM fn_dispositivo_por_chave(sha256(chave))
    if not dispositivo:                   -> 401 "Chave invalida"
    if estado == "pendente":              -> 403 "A aguardar aprovacao"
    if estado == "revogado":              -> 403 "Dispositivo revogado"
```

**Autenticação da app** — JWT:

```python
# login: confere a password com bcrypt e devolve um token assinado (HS256) válido 12 h
jwt.encode({"sub": id, "perfil": "admin", "exp": agora + 12h}, JWT_SECRET, algorithm="HS256")

# em cada pedido: valida o token E confirma na BD que o utilizador ainda existe e ainda é admin
```

**Ligação à base de dados** — abrir uma ligação nova ao PostgreSQL (TCP + TLS + autenticação) custava
~1 s por pedido. Por isso a API mantém **uma ligação partilhada** reutilizada entre pedidos (protegida
por um *lock*; se estiver ocupada, abre uma temporária). **Cada pedido é uma transação**: `COMMIT` no
fim se correu bem, `ROLLBACK` se houve erro — ou fica tudo gravado ou nada.

**Validação** — todos os dados que chegam são validados antes de usar (`ler_uid`, `ler_inteiro`,
`ler_hora`, `ler_janelas`...) → **422** se inválidos. Todas as consultas usam **parâmetros** (`%s`),
nunca texto concatenado → **sem SQL injection**.

**Alertas automáticos**

| Alerta | Quando |
|---|---|
| `tentativas_negadas` | 3 negados seguidos na mesma porta em 5 min (sem um concedido pelo meio) |
| `dispositivo_offline` | Porta sem contacto há mais de 60 s |
| `dispositivo_novo` | Um Arduino novo registou-se |
| `saida_nao_detetada` | Sala fechada com pessoas ainda contadas dentro |
| `entrada_sem_acesso` | Chegou uma entrada sem aula a decorrer na API |

> O Vercel gratuito não tem tarefas agendadas (*cron*), por isso o alerta de offline é verificado
> quando a app pede o estado ou os alertas.

---

### 4.3 Base de dados — a memória (`database/`)

PostgreSQL no servidor da escola; tudo no schema **`tam`**.

**Tabelas (o que guarda cada uma, numa frase)**

| Tabela | Guarda |
|---|---|
| `utilizadores` | Pessoas: nome, perfil (`admin` / `funcionario` / `docente`). Só o admin tem email + password (bcrypt). |
| `cartoes` | UID, dono, estado (`pendente` / `ativo` / `revogado`) |
| `permissoes` | Horário **próprio** de um cartão (substitui o do perfil) |
| `horarios_perfil` | Horário por omissão de docentes e funcionários |
| `dispositivos` | Portas: nome, MAC, **hash** da chave, estado, último contacto, porta aberta, pessoas dentro |
| `registos_acesso` | Cada tentativa de acesso (concedido/negado, motivo, origem cartão/remoto) — **imutável** |
| `aulas` | Início, fim, quem abriu, quem fechou, entradas, saídas |
| `passagens` | Cada entrada/saída detetada pelos sensores |
| `alertas` | Tipo, descrição, porta, resolvido |
| `comandos` | Aberturas remotas (`pendente` / `executado` / `expirado`) |
| `configuracao` | Parâmetros (ex.: intervalo de contacto = 2 s) |

Relações principais: um utilizador tem vários cartões; um cartão tem várias permissões; uma porta tem
vários registos, aulas, passagens, alertas e comandos; uma aula tem várias passagens.

**Regras garantidas pela própria BD** (mesmo que a API tivesse um erro, a BD não deixa):

```sql
-- Só pode existir UM administrador
CREATE UNIQUE INDEX um_so_admin_idx ON tam.utilizadores ((true))
  WHERE perfil = 'admin' AND removido_em IS NULL;

-- Só pode haver UMA aula aberta por porta (dois cartões ao mesmo tempo -> o 2.º recebe 409)
CREATE UNIQUE INDEX aulas_uma_aberta_idx ON tam.aulas (dispositivo_id) WHERE fim IS NULL;

-- Registos de acesso só aceitam INSERT (auditoria): UPDATE ou DELETE dão erro
CREATE TRIGGER registos_imutaveis BEFORE UPDATE OR DELETE ON tam.registos_acesso
  FOR EACH ROW EXECUTE FUNCTION tam.impedir_alteracao_registos();

-- CHECKs: UID hexadecimal, MAC válido, hora_fim > hora_inicio, cartão ativo tem dono, admin tem conta
CONSTRAINT ativo_tem_dono CHECK (estado <> 'ativo' OR utilizador_id IS NOT NULL)
```

**Funções e procedimentos** — a API não escreve SQL solto: chama **funções `fn_`** (consultas que
devolvem linhas, ex.: `fn_cartao_acesso`, `fn_dentro_do_horario`, `fn_aula_aberta`) e **procedimentos
`sp_`** (ações, ex.: `sp_inserir_aula`, `sp_fechar_aula`, `sp_inserir_alerta`). Vantagens: a lógica de
dados fica na BD, a API fica mais simples, e as consultas são reutilizáveis e parametrizadas.

```sql
-- Está dentro do horário? Usa as permissões do cartão; se não tiver nenhuma, o horário do perfil
SELECT 1 FROM (
  SELECT dia_semana, hora_inicio, hora_fim FROM tam.permissoes WHERE cartao_id = p_cartao_id
  UNION ALL
  SELECT dia_semana, hora_inicio, hora_fim FROM tam.horarios_perfil
  WHERE perfil = p_perfil AND NOT EXISTS (SELECT 1 FROM tam.permissoes WHERE cartao_id = p_cartao_id)
) h
WHERE h.dia_semana = p_dia AND h.hora_inicio <= p_hora AND p_hora < h.hora_fim;
```

**Soft delete:** remover uma pessoa não apaga a linha — preenche `removido_em` e revoga os cartões.
Assim o histórico de registos e aulas continua a mostrar o nome.

**Scripts:** `schema.sql` (tabelas, índices, trigger) → `functions.sql` + `procedures.sql` →
`seed.sql` (horários por omissão). Todos podem correr várias vezes (`IF NOT EXISTS`,
`CREATE OR REPLACE`). `reset.sql` apaga todos os dados (`TRUNCATE`).

---

### 4.4 App Android — o painel do administrador (`android/`)

- **Kotlin + Jetpack Compose** (interface declarativa), Material 3. Android 8.0+ (`minSdk 26`).
- **Só o administrador tem conta.** Docentes e funcionários são apenas nomes associados a cartões.

**Separadores:** **Painel** (portas, estado, pessoas dentro, botão Abrir) · **Aulas** · **Registos**
(filtros) · **Gestão** (Portas, Cartões, Pessoas, Horários) · **Alertas**.

**Arquitetura MVVM**

```
 Ecrã Compose (View)  ──chama funções──▶  ViewModel  ──suspend fun──▶  SmartAccessApi (Retrofit)  ──HTTPS──▶ API
        ▲                                     │
        └────────── observa o StateFlow ──────┘
```

- **View** (`PainelScreen`, ...) só desenha o estado; quando muda, o Compose redesenha sozinho.
- **ViewModel** (`PainelViewModel`, ...) guarda o estado num `StateFlow`, faz os pedidos em
  **coroutines** (não bloqueiam o ecrã) e sobrevive à rotação do ecrã.
- **Model**: `SmartAccessApi` (Retrofit + OkHttp + Gson), `Modelos.kt` (classes de dados), `SessaoStore`
  (token guardado com **DataStore**, para não ter de fazer login sempre).

**Retrofit — cada função é uma rota da API:**

```kotlin
@POST("api/door/unlock")
suspend fun abrirPorta(@Body pedido: PedidoAbertura): RespostaAbertura
```

**Interceptor do OkHttp** — junta o token a todos os pedidos e termina a sessão se a API responder 401:

```kotlin
.addInterceptor { cadeia ->
    val pedido = cadeia.request().newBuilder().apply {
        if (token != null) header("Authorization", "Bearer $token")
    }.build()
    val resposta = cadeia.proceed(pedido)
    if (resposta.code == 401 && token != null) aoExpirarSessao?.invoke()   // volta ao login
    resposta
}
```

**Abertura remota no ViewModel** — cria o comando e espera a confirmação do Arduino:

```kotlin
val pedido = api.abrirPorta(PedidoAbertura(dispositivo.id))
while (estadoComando == "pendente" && tentativas < 60) {
    delay(1000)                                         // pergunta a cada 1 s, até 1 minuto
    estadoComando = api.comando(pedido.comandoId).estado
}
// "executado" -> "Porta aberta" · "expirado" -> "O dispositivo nao respondeu a tempo"
```

**Atualização automática:** cada ecrã volta a pedir os dados periodicamente (Painel a cada 3 s,
Registos/Alertas/Aulas a cada 10 s) **só enquanto a app está visível** (`repeatOnLifecycle`), para não
gastar bateria e dados em segundo plano.

**Arranque:** há token guardado → entra direto. Não há → `GET /api/setup`: se falta admin mostra
"Criar administrador", senão mostra o Login.

---

## 5. Perguntas prováveis e respostas

### Arquitetura e decisões

**Porque é que o Arduino não fala diretamente com a base de dados?**
O Arduino é um aparelho físico na porta — pode ser roubado ou lido. Se tivesse a password da BD,
quem o roubasse tinha acesso a tudo. Assim só tem a chave da sua porta, que o admin pode revogar.
Além disso, as regras ficam **num só sítio** (a API): mudar um horário na app tem efeito imediato em
todas as portas, sem reprogramar Arduinos.

**Porque é que o Arduino não decide sozinho?**
Para haver **uma só fonte de verdade**. Se cada porta decidisse com dados guardados localmente, os
dados podiam estar desatualizados (ex.: um cartão revogado há 5 minutos ainda abria).

**Porque Flask / Python?** Simples, leve, rápido de desenvolver, ótimas bibliotecas (psycopg para
PostgreSQL, bcrypt, PyJWT). Para uma API REST com este tamanho chega perfeitamente.

**Porque o Vercel?** Gratuito, HTTPS automático, deploy automático a cada `push` no GitHub, não é
preciso gerir um servidor. Desvantagem: é *serverless* — não mantém ligações abertas nem tem tarefas
agendadas no plano gratuito (daí o polling e a verificação de offline "a pedido").

**Porque PostgreSQL?** Base de dados relacional robusta, com transações, chaves estrangeiras,
restrições `CHECK`, índices parciais, triggers e funções — usamos tudo isso para garantir as regras.
E era o servidor disponível na escola.

**O que é uma API REST?** Uma interface onde cada recurso tem um endereço (`/api/cards`,
`/api/devices`) e usam-se os métodos HTTP para as operações: `GET` ler, `POST` criar, `PATCH`/`PUT`
alterar, `DELETE` apagar. Os dados vão em JSON e o resultado é indicado pelo código HTTP
(200 ok, 201 criado, 401 não autenticado, 403 proibido, 404 não existe, 409 conflito, 422 dados inválidos).

**Porque polling (o Arduino pergunta a cada 2 s) e não a API avisar o Arduino?**
O Arduino está numa rede local, **não tem IP público**, e o Vercel **não mantém ligações abertas**.
Logo, a API não consegue "chamar" o Arduino. O custo é até ~2 s de atraso na abertura remota e muitos
pedidos. Melhoria: **MQTT** ou **WebSockets** num servidor próprio.

**O sistema funciona com várias portas?** Sim. O mesmo código serve para qualquer porta: cada Arduino
regista-se pelo seu MAC, recebe a sua chave e o admin aprova-o e dá-lhe um nome. Na BD tudo
(registos, aulas, passagens) está ligado ao `dispositivo_id`.

### Funcionamento

**O que acontece se a rede ou a API falhar?** Nenhum cartão abre (*fail-secure*) — o LCD mostra
`Sem rede`. Preferimos segurança a conveniência. Chegámos a ter um modo offline (lista de cartões na
EEPROM) mas retirámo-lo de propósito, porque a porta podia abrir com dados desatualizados.
Se falhar a meio de uma aula, a sala fica aberta e as passagens ficam em fila até a rede voltar.

**E se o Arduino reiniciar a meio de uma aula?** Pede a configuração à API, que lhe diz que há uma
aula a decorrer, e retoma-a (dono, contagens).

**Como sabem o sentido (entrada ou saída)?** Com **dois** sensores: A do lado de fora, B do lado de
dentro. Se a pessoa tapa primeiro o A e depois fica só no B → entrou; o contrário → saiu.

**E se duas pessoas passarem lado a lado?** Conta como uma — é uma limitação conhecida dos sensores
ultrassónicos. Mitigação: quando a sala fecha com pessoas contadas dentro, a API cria o alerta
"saída não detetada" e repõe a 0; e uma saída com 0 pessoas dentro é registada como "entrada não
detetada". Melhoria: sensores ToF, mais sensores ou câmara de contagem.

**Como funciona o sensor ultrassónico?** Emite um pulso de ultrassom (40 kHz) e mede o tempo até o
eco voltar. Distância = tempo × velocidade do som / 2 → no código, `tempo_µs / 58` dá centímetros.

**Como funciona o RFID?** O leitor RC522 cria um campo eletromagnético a 13,56 MHz que alimenta o
chip do cartão (passivo, sem bateria). O cartão responde com o seu **UID** (identificador único), que
enviamos para a API em hexadecimal (ex.: `8DF36E69`).

**O que acontece quando dois cartões são passados ao mesmo tempo em portas diferentes / na mesma porta?**
Portas diferentes: sem problema, cada pedido é independente. Na mesma porta: o índice único
`aulas_uma_aberta_idx` impede duas aulas abertas — o segundo pedido recebe 409.

**Quem pode fechar uma aula?** O dono (sempre, mesmo fora do horário), um admin ou um funcionário.
Se a aula foi aberta pela app (sem cartão), qualquer cartão ativo.

**Porque é que o dono pode fechar fora do horário?** Senão um professor que ficasse até depois das
20:00 não conseguia fechar a sua sala.

**Para que servem os perfis?** `admin` — entra a qualquer hora, fecha qualquer aula, único com conta
na app. `funcionario` — horário alargado (seg–sáb 07:00–23:00), fecha qualquer aula (ex.: limpeza,
segurança). `docente` — seg–sex 08:00–20:00, só fecha a sua aula.

**Porque só um administrador?** Simplifica o modelo (é quem gere o sistema). A BD garante-o com um
índice único, e o admin não se pode remover nem mudar o próprio perfil (o sistema nunca fica sem admin).

### Segurança

**Como é que a API sabe que o pedido vem mesmo de um Arduino aprovado?** Cada Arduino envia a sua
chave no cabeçalho `x-api-key`. A API calcula o SHA-256 e procura na BD. Sem chave válida → 401;
porta pendente ou revogada → 403.

**Porque guardam só o hash da chave?** Se alguém conseguir ler a BD, não fica com as chaves das
portas. É o mesmo princípio das passwords.

**Porquê bcrypt nas passwords e SHA-256 nas chaves?** Passwords humanas são fracas e adivinháveis →
bcrypt é propositadamente **lento** e tem **salt**, o que trava ataques de força bruta. A chave do
Arduino tem 256 bits aleatórios → impossível de adivinhar, um hash rápido chega.

**O que é um JWT?** Um token assinado pela API (com o `JWT_SECRET`) que diz "este é o utilizador X,
admin, válido até às HH:MM". A app envia-o em cada pedido (`Authorization: Bearer ...`). A API
verifica a assinatura — se alguém alterar o token, a assinatura deixa de bater. Expira em 12 h.
Além disso, em cada pedido a API confirma na BD que o utilizador ainda existe e ainda é admin.

**E se alguém registar um Arduino falso?** Fica **pendente** e gera o alerta "dispositivo novo". Sem
aprovação do admin não consegue fazer nada.

**E se roubarem um Arduino?** Só levam a chave daquela porta; o admin revoga ou apaga a porta na app.
Não há password da BD nem Wi-Fi de outras portas lá dentro (o Wi-Fi da própria porta sim).

**A rota `/api/setup` não tem autenticação — não é perigoso?** Só funciona enquanto não existir
nenhum admin. Depois responde 409. E mesmo que dois pedidos chegassem ao mesmo tempo, o índice
único da BD só deixa criar um.

**Os cartões podem ser clonados?** Sim — usamos só o UID, que se pode copiar. Melhoria: cartões com
criptografia (MIFARE DESFire) ou cartão + PIN.

**Onde estão as passwords/segredos?** Nunca no Git: `.env` (API local), `arduino_secrets.h` (Wi-Fi e
endereço da API), variáveis de ambiente no Vercel. Cada pasta tem um ficheiro `*.example` com o formato.

**SQL injection?** Não é possível: todas as consultas usam parâmetros (`%s`) passados à parte, via
funções/procedimentos da BD. Os dados recebidos são também validados (ex.: UID tem de ser hexadecimal).

**Os registos podem ser apagados ou alterados?** Não — um trigger na BD rejeita `UPDATE` e `DELETE`
na tabela `registos_acesso`. É um histórico de auditoria. (Única exceção: apagar uma porta inteira
apaga o histórico dela.)

**Privacidade / RGPD?** Guardamos o mínimo: nome e perfil das pessoas (sem email/password, exceto o
admin), UID do cartão e as horas de acesso. Os acessos são consultados só pelo admin. A contagem de
pessoas é anónima (só números).

### App

**Porquê MVVM?** Separa a interface (View) da lógica e do estado (ViewModel) e dos dados (Model). O
ViewModel sobrevive à rotação do ecrã (não se perdem dados nem se repetem pedidos) e o código fica
mais organizado e testável.

**O que são coroutines?** A forma do Kotlin fazer trabalho assíncrono: os pedidos à rede correm
"em segundo plano" sem bloquear o ecrã, com código escrito de forma sequencial (`suspend fun`).

**O que é o StateFlow?** Um "valor observável": o ViewModel muda o estado, o ecrã Compose que o
observa redesenha-se automaticamente.

**Como é que a app se mantém atualizada?** Pede os dados periodicamente (Painel a cada 3 s) enquanto
está visível. Não há notificações push (melhoria: Firebase Cloud Messaging).

**Onde fica guardado o login?** O token fica no **DataStore** (ficheiro privado da app). Melhoria:
cifrá-lo com o Android Keystore.

### Desenvolvimento e testes

**Como testaram?** Testes manuais com o hardware; a API com uma coleção **Postman**
(`testes/postman/`); o Arduino tem comandos no Monitor Série para simular cartões (`<UID>`) e
passagens (`E`/`S`) sem hardware. A API envia um cabeçalho `Server-Timing` para medir quanto tempo
cada pedido demora.

**Quais foram as maiores dificuldades?** (bons exemplos para contar)
1. **O servo fazia cair o Wi-Fi** (consumo de corrente) → retirado, estado nos LEDs.
2. **Os pedidos HTTPS bloqueiam o Arduino** e perdiam-se passagens → fila de eventos, só se envia com a porta livre.
3. **Ruído dos sensores** (ecos falsos) → calibração, 2 leituras seguidas, intervalo entre sensores.
4. **Lentidão da API**: cada pedido abria uma ligação nova à BD (~1 s) → ligação partilhada reutilizada.
5. **TLS no Arduino é lento** (~1–2 s) → keep-alive.
6. **O leitor RC522 às vezes deixava de responder** → verificação a cada 10 s e reinício automático.
7. **O buzzer ficava a apitar sem parar** quando dois tons se sobrepunham → `noTone()` antes de cada tom.

**Quanto tempo demora a abrir?** Tipicamente menos de 1 s depois de passar o cartão (o primeiro
pedido depois de ligar é mais lento por causa do handshake TLS).

**Como se instala?** BD: correr `schema.sql`, `functions.sql`, `procedures.sql`, `seed.sql`. API: push
para o GitHub → Vercel com as variáveis de ambiente. Arduino: preencher `arduino_secrets.h` (Wi-Fi e
endereço da API) e carregar o código. App: compilar no Android Studio. No primeiro arranque a app
pede para criar o administrador.

---

## 6. Pontos fracos — admitir com confiança

Um professor gosta de ouvir que a equipa **conhece os limites** do seu sistema. Formato: *limitação →
porquê → como se resolvia*.

| Limitação | Como se resolvia |
|---|---|
| Não há fechadura física, só LEDs | Fechadura eletromagnética com relé e alimentação própria |
| Duas pessoas lado a lado contam como uma | Sensores ToF / mais sensores / câmara |
| Polling a cada 2 s (atraso + muitos pedidos) | MQTT ou WebSockets num servidor próprio |
| Cartões clonáveis (só UID) | Cartões com criptografia ou cartão + PIN |
| Sem rede não funciona | Era intencional (segurança); uma alternativa seria um modo offline só para admin, com cache assinada |
| Sem notificações push | Firebase Cloud Messaging para alertas |
| Token guardado sem cifra extra | Android Keystore / EncryptedDataStore |
| Testes manuais | Testes automáticos à API (pytest) |
| Restos do antigo modo offline na API (`cartoes_offline`, evento `porta`) | Remover esse código morto |
| Alerta "entrada sem acesso" quase nunca dispara: com a porta trancada o Arduino ignora os sensores | É uma rede de segurança para quando a API e o Arduino discordam (ex.: aula fechada na API) |

---

## 7. Cábula final

**7 frases para usar:**
1. "O Arduino só lê e executa; **a API decide sempre**."
2. "Cada porta **regista-se sozinha pelo MAC** e só funciona depois de o admin a aprovar."
3. "Um cartão novo fica **pendente** até o admin o associar a uma pessoa."
4. "**Sem rede a porta não abre** — preferimos segurança a conveniência."
5. "Com dois sensores: **de A para B é entrada, de B para A é saída**."
6. "Os pedidos **bloqueiam os sensores**, por isso as passagens vão para uma fila e só se enviam com a porta livre."
7. "A **BD garante as regras críticas**: registos imutáveis, um só admin, uma só aula aberta por porta."

**Números a saber:**

| | |
|---|---|
| Polling do Arduino | 2 s |
| Porta considerada offline | 60 s sem contacto |
| Comando remoto expira | 1 min |
| App espera pela abertura remota | até 60 × 1 s |
| Token JWT | 12 h |
| Alerta de tentativas | 3 negados em 5 min |
| Chave do Arduino | 256 bits (64 hex), guardada como SHA-256 |
| Horário docente / funcionário | seg–sex 08–20 / seg–sáb 07–23 |
| Fila de eventos no Arduino | 32 |
| LCD | 16 colunas × 2 linhas (por isso o nome da porta tem no máximo 16 caracteres) |

### Checklist antes de entrar na sala

- [ ] Hotspot/Wi-Fi ligado e com o nome/password que estão no `arduino_secrets.h`
- [ ] Ligar o Arduino **com a porta livre** (calibração dos sensores) e confirmar `Passe o cartao` no LCD
- [ ] Monitor Série aberto a **9600 baud** no portátil
- [ ] App com sessão iniciada; confirmar que o Painel mostra a porta **online**
- [ ] Ter preparados: 1 cartão do **admin**, 1 cartão de **docente** já aprovado, 1 cartão **nunca usado** (para o fluxo de aprovação), 1 de outro docente (para "só o dono fecha")
- [ ] Confirmar a **hora**: se a defesa for depois das 20:00 ou ao fim de semana, o docente fica *fora do horário* (ou usar isso na demo de propósito)
- [ ] Marcar os alertas antigos como resolvidos para a lista de alertas ficar limpa
- [ ] Plano B: se um sensor ou o leitor falhar, simular no Monitor Série (`8DF36E69` = cartão, `E`/`S` = passagem)

# android

Aplicação Android do SmartAccess em Kotlin (Jetpack Compose, MVVM), desenvolvida no Android Studio.
Serve para o administrador gerir o sistema e abrir portas remotamente, através da API REST.

## Ligação à API

A app comunica com a API (pasta `api/`) por HTTPS, com Retrofit + OkHttp + Gson.

- **URL por omissão:** `https://tam-smartaccess-api.vercel.app/` (API de produção no Vercel), definido em
  `app/build.gradle.kts` e exposto ao código como `BuildConfig.API_URL`.
- **Outro URL (opcional):** adicionar ao `local.properties` (não vai para o Git):

  ```properties
  api.url=http://10.0.2.2:8000/
  ```

  `10.0.2.2` é o endereço do PC visto a partir do emulador. Num telemóvel real usa-se o IP do PC na rede local
  (ex.: `http://192.168.1.50:8000/`) e a API tem de correr com `flask --app index run --host 0.0.0.0 --port 8000`.
  O HTTP sem TLS só é permitido na versão de debug (`app/src/debug/AndroidManifest.xml`).
- **Autenticação:** `POST api/auth/login` devolve um token JWT. O token é guardado em DataStore (`SessaoStore`) e
  enviado em todos os pedidos no cabeçalho `Authorization: Bearer <token>`. Se a API responder `401`, a sessão
  termina e a app volta ao ecrã de login.
- **Erros:** as mensagens `{"detail": "..."}` da API são mostradas ao utilizador; sem rede aparece
  "Sem ligacao ao servidor".

### Endpoints usados (`data/SmartAccessApi.kt`)

| Método | Endpoint | Ecrã |
|---|---|---|
| POST | `api/auth/login` | Login |
| GET | `api/me` | Sessão |
| GET | `api/status` | Painel (estado das portas) |
| POST | `api/door/unlock` | Painel / Portas (abrir porta remotamente) |
| GET | `api/door/commands/{id}` | Confirmação de que o Arduino executou a abertura |
| GET | `api/logs` | Registos de acesso (filtros: datas, utilizador, resultado) |
| GET / POST / PATCH | `api/cards`, `api/cards/{id}` | Cartões (aprovar, associar, revogar) |
| PUT | `api/cards/{id}/permissions` | Horário próprio de um cartão |
| GET / PATCH | `api/alerts`, `api/alerts/{id}` | Alertas |
| GET / POST / PATCH | `api/users`, `api/users/{id}` | Utilizadores |
| GET / PATCH | `api/devices`, `api/devices/{id}` | Gestão de portas (Arduinos) |
| GET | `api/classes` | Aulas |
| GET / PUT | `api/schedules`, `api/schedules/{perfil}` | Horários por perfil |

## Estrutura

```
app/src/main/java/pt/smartaccess/app/
├── MainActivity.kt
├── data/
│   ├── SmartAccessApi.kt   # interface Retrofit, cliente HTTP e token
│   ├── Modelos.kt          # classes de dados (JSON da API)
│   └── SessaoStore.kt      # guarda o token (DataStore)
└── ui/                     # ecrãs Compose + ViewModels
    ├── LoginScreen.kt, SessaoViewModel.kt
    ├── PainelScreen.kt, PortasScreen.kt, RegistosScreen.kt
    ├── CartoesScreen.kt, UtilizadoresScreen.kt, HorariosScreen.kt
    ├── AlertasScreen.kt, AulasScreen.kt, GestaoScreen.kt
    └── Tema.kt, Componentes.kt, Comum.kt, Dialogo*.kt
```

## Requisitos

- Android Studio recente (com JDK 25 incluído) e Android SDK 37
- Telemóvel ou emulador com Android 8.0 (API 26) ou superior

## Abrir e correr no Android Studio

1. *File → Open* e escolher esta pasta (`android/`).
2. Esperar pela sincronização do Gradle (o `local.properties` com `sdk.dir` é criado automaticamente).
3. Escolher o dispositivo e carregar em **Run ▶**.

## Instalar num telemóvel

### Opção A: por cabo USB (recomendado)

1. No telemóvel: *Definições → Acerca do telefone* e tocar 7 vezes em **Número de compilação** para ativar as
   *Opções de programador*.
2. Em *Definições → Sistema → Opções de programador*, ativar **Depuração USB**.
3. Ligar o telemóvel ao PC por USB e aceitar o pedido "Permitir depuração USB?".
4. No Android Studio, o telemóvel aparece na lista de dispositivos: escolhê-lo e carregar em **Run ▶**.

### Opção B: por Wi-Fi (Android 11+)

Em *Opções de programador → Depuração sem fios*, emparelhar com o Android Studio
(*Device Manager → Pair Devices Using Wi-Fi*, por código QR). PC e telemóvel têm de estar na mesma rede.

### Opção C: copiar o APK

1. Gerar o APK: no Android Studio *Build → Build App Bundle(s) / APK(s) → Build APK(s)*, ou na linha de comandos:

   ```
   gradlew.bat assembleDebug
   ```

2. O ficheiro fica em `app/build/outputs/apk/debug/app-debug.apk`.
3. Enviá-lo para o telemóvel (cabo, Google Drive, email...), abri-lo e permitir
   **Instalar apps desconhecidas** quando o Android pedir.

## Login de teste

Credenciais do `database/seed.sql`: `admin@smartaccess.pt` / `admin123` (mudar antes da entrega).

## Notas

- `local.properties`, `build/`, `.gradle/` e `.idea/` não vão para o GitHub (ver `.gitignore`).
- Se o Gradle falhar com "jlink executable ... does not exist", o `JAVA_HOME` está a apontar para um JRE sem
  JDK completo (ex.: o da extensão Java do VS Code). Compilar a partir do Android Studio ou definir
  `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`.

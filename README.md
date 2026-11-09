# SmartAccess

Sistema de Controlo de Acesso e Segurança de Sala Técnica.
Projeto de Tecnologias e Aplicações Móveis (TAM), Licenciatura em Engenharia Informática, ESTGOH / Politécnico de Coimbra, 2026/2027.

## Estrutura

| Pasta | Conteúdo |
|---|---|
| `arduino/` | Firmware do Arduino UNO WiFi Rev2 (RFID, sensor ultrassónico, servo, LCD, buzzer) |
| `api/` | API REST em Python (Flask), alojada no Vercel |
| `database/` | Esquema PostgreSQL (`schema.sql`) e dados iniciais |
| `android/` | Aplicação Android em Kotlin (Jetpack Compose) |
| `docs/` | Relatórios e documentação |

## Equipa

- David Mendes
- Dinis Jesus
- Rafael Ferreira

## Como trabalhar em equipa

1. Clonar o repositório: `git clone <URL-do-repositorio>`
2. Antes de começar a trabalhar: `git pull`
3. Depois de alterar: `git add .` → `git commit -m "mensagem"` → `git push`
4. Cada pessoa trabalha preferencialmente na sua pasta para evitar conflitos.

## Segredos (nunca vão para o GitHub)

Palavras-passe, chaves de API, `connection string` da base de dados, Wi-Fi e `google-services.json`
ficam em ficheiros locais ignorados pelo Git (`.env`, `secrets.h`) e em variáveis de ambiente no Vercel.
Cada pasta tem um ficheiro `*.example` que mostra o formato, sem valores reais.

# api

API REST em Python (Flask), alojada no Vercel com deploy automatico a partir do GitHub (branch `main`).

- Ponto de entrada: `index.py` (variavel `app`). No Vercel, **Root Directory = `api`**.
- Dependencias em `requirements.txt` (o Vercel instala so o que la estiver).
- Variaveis de ambiente obrigatorias: `DATABASE_URL` e `JWT_SECRET` (ver `.env.example`).
  Definir no Vercel em Settings -> Environment Variables e, para testes locais, num `.env` (ignorado pelo Git).
- Todas as tabelas estao no schema `tam`: a ligacao (em `index.py`) define `search_path=tam`, por isso as consultas usam so o nome da tabela.
- Localmente: `flask --app index run --port 8000 --debug` (dentro desta pasta).

## URL da API (producao)

https://tam-smartaccess-api.vercel.app

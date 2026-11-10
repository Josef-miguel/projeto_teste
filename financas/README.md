# Sistema de Gerenciamento Financeiro Pessoal

Sistema completo para controle das suas finanças, desenvolvido com **Java** (servidor HTTP embutido do JDK + JDBC), **MySQL**, **HTML**, **CSS** e **JavaScript** — sem frameworks, apenas o driver oficial do MySQL como dependência.

## Funcionalidades

| Funcionalidade | Descrição |
|---|---|
| Cadastro e login | Autenticação com senha criptografada (PBKDF2) e sessões com cookie `HttpOnly` |
| Receitas e despesas | Registro, edição e exclusão de transações com valor, data, categoria e descrição |
| Categorias | Categorias de gastos e receitas com cor personalizada (padrões criadas ao cadastrar) |
| Contas a pagar | Controle de vencimentos, marcação de pagamento, destaque para contas vencidas |
| Relatórios mensais | Totais de receitas, despesas e saldo; resumo por categoria; visão anual |
| Gráficos | Barras (receitas × despesas no ano) e rosca (despesas por categoria) com Chart.js |
| Exportação CSV | Transações e contas a pagar em CSV compatível com Excel pt-BR (BOM + `;`) |

## Design

Identidade visual própria em verde profundo: sidebar escura `#172C27`, fundo quente `#F4F3EE`, acento `#477B65`, destaques `#D99B55` e alertas `#D9534F`. Tipografia **Manrope** (títulos e valores) + **Inter** (textos e dados), ícones **Lucide** embutidos em SVG, botões com estados de hover, sombras leves e bordas discretas.

## Como executar

1. **MySQL** — inicie o servidor (se ainda não estiver rodando):
   ```
   start-mysql.bat
   ```
   > Não precisa criar nada manualmente: na inicialização o sistema **cria
   > automaticamente** o banco `financeiro`, o usuário da aplicação e as
   > tabelas (caso não existam), usando as credenciais administrativas
   > (`db.admin.*`) do `config.properties`.

2. **Servidor da aplicação**:
   ```
   run.bat
   ```
   (compila se necessário e sobe o servidor em `http://localhost:8080`)

3. Abra **http://localhost:8080** no navegador e crie sua conta.

> Para instalar o MySQL como serviço do Windows (início automático), execute em um Prompt **como Administrador**:
> `"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe" --install MySQL84 --defaults-file="C:\Users\MIGUEL\.mysql-financeiro\my.ini"`

## Estrutura do projeto

```
financas/
├── database/
│   └── schema.sql              # Criação do banco e tabelas
├── lib/
│   └── mysql-connector-j-9.4.0.jar   # Driver JDBC do MySQL
├── src/com/financas/
│   ├── App.java                # Servidor HTTP + arquivos estáticos
│   ├── DatabaseSetup.java      # Criação automática do banco e tabelas
│   ├── Config.java             # config.properties
│   ├── DB.java                 # Conexões JDBC
│   ├── Http.java               # Respostas JSON, cookies, erros
│   ├── Router.java             # Roteador da API REST
│   ├── Json.java               # Parser/gerador JSON (sem libs)
│   ├── Passwords.java          # Hash PBKDF2 das senhas
│   ├── Validation.java         # Validação de dados
│   ├── Auth.java               # Cadastro, login, sessões
│   ├── AuthController.java     # /api/auth/*
│   ├── CategoryController.java # /api/categories
│   ├── TransactionController.java # /api/transactions
│   ├── BillController.java     # /api/bills
│   └── ReportController.java   # /api/reports/* e /api/export/csv
├── web/
│   ├── index.html              # Login / cadastro
│   ├── dashboard.html          # Painel do sistema
│   ├── css/style.css
│   └── js/{auth.js, app.js}
├── config.properties.example   # Modelo de configuração (copie para config.properties)
├── build.bat / run.bat         # Scripts de compilação/execução
└── start-mysql.bat / stop-mysql.bat
```

## API REST

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/register` | Criar conta |
| POST | `/api/auth/login` | Entrar |
| POST | `/api/auth/logout` | Sair |
| GET | `/api/auth/me` | Usuário da sessão |
| GET/POST/PUT/DELETE | `/api/categories[/{id}]` | Categorias |
| GET/POST/PUT/DELETE | `/api/transactions[/{id}]` | Transações (filtros: `month`, `type`, `category_id`) |
| GET/POST/PUT/DELETE | `/api/bills[/{id}]` | Contas a pagar (filtros: `month`, `status`) |
| POST | `/api/bills/{id}/pay` · `/unpay` | Marcar/reverter pagamento |
| GET | `/api/reports/summary?month=AAAA-MM` | Resumo do mês |
| GET | `/api/reports/yearly?year=AAAA` | Totais por mês do ano |
| GET | `/api/export/csv?kind=transactions\|bills&month=` · `&year=` | Exportação CSV |

## Segurança

- Senhas com **PBKDF2-HmacSHA256** (120.000 iterações, salt aleatório por usuário)
- Sessões com token de 256 bits e cookie `HttpOnly`/`SameSite=Lax`
- Todas as consultas SQL usam `PreparedStatement` (sem risco de SQL Injection)
- Escopo de dados por usuário em todas as consultas
- Servidor estático com proteção contra *path traversal*

# Finanças Pessoais

Sistema web completo para gerenciamento de finanças pessoais, desenvolvido com **Java** (servidor HTTP embutido do JDK + JDBC), **MySQL**, **HTML**, **CSS** e **JavaScript puro**. Sem frameworks no backend e sem build de front-end — a única dependência do projeto é o driver oficial do MySQL.

O sistema roda inteiramente na sua máquina: os dados ficam no seu banco MySQL local e nada é enviado para a internet.

## Funcionalidades

| Funcionalidade | Descrição |
|---|---|
| Cadastro e login | Autenticação própria, senha protegida com PBKDF2 e sessão por cookie `HttpOnly` |
| Tela de login/cadastro | Página dividida com apresentação da marca, abas Entrar/Criar conta e alternador de visibilidade da senha |
| Receitas e despesas | Lançamentos com valor, data, categoria e descrição; filtros por tipo e categoria |
| Categorias | Personalizáveis com nome, tipo (receita/despesa) e cor; 10 categorias padrão criadas ao abrir a conta |
| Contas a pagar | Controle de vencimentos, marcar/reverter pagamento, destaque para contas vencidas |
| Resumo | Cards do mês (receitas, despesas, saldo, contas vencidas), gráficos e últimas transações |
| Relatórios | Totais do mês, resumo por categoria e visão de todos os meses do ano |
| Gráficos | Barras (receitas × despesas no ano) e rosca (despesas por categoria), com Chart.js |
| Exportação CSV | Transações e contas a pagar em CSV compatível com Excel pt-BR (BOM + separador `;` + vírgula decimal) |
| Navegação por mês | Seletor de mês presente em todas as seções, com dados sempre no escopo do usuário logado |

## Tecnologias

- **Backend:** Java 21, servidor HTTP embutido (`com.sun.net.httpserver`), JDBC, MySQL Connector/J 9.4
- **Banco de dados:** MySQL 8+ (testado no MySQL 8.4)
- **Frontend:** HTML5, CSS3 e JavaScript (ES6), Chart.js 4 via CDN, fontes Manrope e Inter (Google Fonts)
- **Segurança:** PBKDF2-HmacSHA256 (120.000 iterações), tokens de sessão de 256 bits, `PreparedStatement` em todas as consultas

## Requisitos

- JDK 21 ou superior
- MySQL 8 ou superior em execução
- Navegador moderno (Chrome, Edge, Firefox)

## Como executar

### 1. Prepare a configuração

Copie o modelo de configuração:

```
copy config.properties.example config.properties
```

Abra `config.properties` e ajuste ao seu ambiente — as credenciais administrativas são usadas apenas para criar o banco na inicialização:

```properties
# Conexão da aplicação com o banco
db.url=jdbc:mysql://localhost:3306/financeiro?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
db.user=financas
db.password=sua_senha_app

# Credenciais administrativas (root ou equivalente)
db.admin.url=jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
db.admin.user=root
db.admin.password=sua_senha_root

server.port=8080
web.dir=web
```

> O arquivo `config.properties` contém senhas e **não é versionado** no git — por isso ele é criado a partir do `.example`.

### 2. Inicie o MySQL

No Windows, se o MySQL estiver instalado como serviço, ele já roda automaticamente. Os scripts auxiliares do projeto:

| Script | Função |
|---|---|
| `start-mysql.bat` | Inicia o servidor MySQL com o `my.ini` configurado |
| `stop-mysql.bat` | Encerra o MySQL (solicita a senha do root) |
| `build.bat` | Compila o código Java para a pasta `out/` |
| `run.bat` | Compila (se necessário) e inicia o servidor da aplicação |

### 3. Inicie a aplicação

```
run.bat
```

### 4. Acesse

Abra **http://localhost:8080** no navegador e crie sua conta.

## Criação automática do banco

Não é preciso criar banco, usuário ou tabelas manualmente. A cada inicialização o sistema:

1. Tenta conectar no banco `financeiro` com o usuário da aplicação;
2. Se o banco não existir, conecta com as credenciais administrativas e cria o banco, o usuário da aplicação e concede os privilégios;
3. Executa o DDL das tabelas (`users`, `sessions`, `categories`, `transactions`, `bills`) com `CREATE TABLE IF NOT EXISTS` — seguro rodar sempre.

Se o MySQL estiver parado ou as credenciais administrativas estiverem erradas, o sistema exibe uma mensagem clara no console e encerra, em vez de iniciar com falha.

O script `database/schema.sql` fica disponível para quem preferir criar a estrutura manualmente: `mysql -u root -p < database/schema.sql`.

## Estrutura do projeto

```
financas/
├── database/
│   └── schema.sql              # DDL do banco e tabelas (referência)
├── lib/
│   └── mysql-connector-j-9.4.0.jar   # Driver JDBC do MySQL (única dependência)
├── src/com/financas/
│   ├── App.java                # main: inicialização, servidor HTTP e arquivos estáticos
│   ├── DatabaseSetup.java      # Criação automática do banco, usuário e tabelas
│   ├── Config.java             # Leitura do config.properties (com valores padrão)
│   ├── DB.java                 # Fábrica de conexões JDBC
│   ├── Http.java               # Respostas JSON, cookies, leitura de corpo, erros
│   ├── Router.java             # Roteador da API REST (método + caminho)
│   ├── Json.java               # Parser/gerador JSON escrito à mão (sem libs)
│   ├── Passwords.java          # Hash e verificação PBKDF2
│   ├── Validation.java         # Validação de entrada (e-mail, valores, datas)
│   ├── Auth.java               # Cadastro, login, sessões e categorias padrão
│   ├── AuthController.java     # Rotas /api/auth/*
│   ├── CategoryController.java # Rotas /api/categories
│   ├── TransactionController.java # Rotas /api/transactions
│   ├── BillController.java     # Rotas /api/bills (inclui pay/unpay)
│   └── ReportController.java   # Rotas /api/reports/* e /api/export/csv
├── web/
│   ├── index.html              # Página de login/cadastro
│   ├── dashboard.html          # Painel do sistema (single page com seções)
│   ├── css/style.css           # Design system completo
│   └── js/
│       ├── auth.js             # Formulários de autenticação
│       └── app.js              # Navegação, tabelas, modais, gráficos e CSV
├── config.properties.example   # Modelo de configuração
├── build.bat / run.bat         # Compilação e execução (Windows)
└── start-mysql.bat / stop-mysql.bat
```

## API REST

Todas as rotas respondem JSON. As rotas autenticadas exigem o cookie de sessão (`fin_token`), definido no login.

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/register` | Criar conta (e gera as categorias padrão) |
| POST | `/api/auth/login` | Entrar |
| POST | `/api/auth/logout` | Sair (invalida a sessão) |
| GET | `/api/auth/me` | Dados do usuário da sessão |
| GET/POST/PUT/DELETE | `/api/categories[/{id}]` | CRUD de categorias |
| GET/POST/PUT/DELETE | `/api/transactions[/{id}]` | CRUD de transações (filtros: `month`, `type`, `category_id`) |
| GET/POST/PUT/DELETE | `/api/bills[/{id}]` | CRUD de contas a pagar (filtros: `month`, `status`) |
| POST | `/api/bills/{id}/pay` | Marcar conta como paga |
| POST | `/api/bills/{id}/unpay` | Reverter pagamento |
| GET | `/api/reports/summary?month=AAAA-MM` | Resumo do mês (receitas, despesas, saldo, vencidas) |
| GET | `/api/reports/yearly?year=AAAA` | Totais por mês do ano |
| GET | `/api/export/csv?kind=transactions\|bills&month=` · `&year=` | Download CSV |

Exemplo:

```
POST /api/auth/login
{ "email": "voce@email.com", "password": "suaSenha" }
```

## Segurança

- Senhas protegidas com **PBKDF2-HmacSHA256**, 120.000 iterações e salt aleatório por usuário
- Sessões com token de 256 bits armazenado no banco e cookie **HttpOnly** / `SameSite=Lax`
- Todas as consultas SQL usam **PreparedStatement** (sem risco de SQL Injection)
- Escopo por usuário em todas as consultas — nenhuma rota acessa dados de outro usuário
- Servidor estático com proteção contra *path traversal*
- Respostas 401 imediatas em rotas protegidas quando não há sessão válida

## Design

Identidade visual própria em verde profundo:

| Papel | Cor |
|---|---|
| Cor principal (sidebar, botões) | `#172C27` |
| Destaque (receitas, gráficos) | `#477B65` |
| Fundo principal | `#F4F3EE` |
| Superfícies/cards | `#FFFFFF` |
| Destaque secundário (pendências) | `#D99B55` |
| Alertas e contas vencidas | `#D9534F` |

Tipografia **Manrope** (títulos e valores financeiros) + **Inter** (textos, formulários e tabelas), ícones **Lucide** embutidos em SVG, bordas discretas, cantos arredondados de 8–12px, sombras leves e layout responsivo (a sidebar vira navegação horizontal em telas menores).

## Observações

- O `config.properties` desta máquina não é versionado; quem clonar o projeto cria o próprio a partir do exemplo.
- Os scripts `.bat` assumem Windows com JDK instalado em `C:\Program Files\Eclipse Adoptium`. Em outros ambientes, compile e execute manualmente:

```
javac -encoding UTF-8 -cp "lib/mysql-connector-j-9.4.0.jar" -d out $(find src -name "*.java")
java -cp "out;lib/mysql-connector-j-9.4.0.jar" com.financas.App   # Windows
java -cp "out:lib/mysql-connector-j-9.4.0.jar" com.financas.App   # Linux/macOS
```

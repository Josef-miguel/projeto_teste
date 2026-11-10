package com.financas;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepara o banco de dados a cada inicialização do sistema:
 * cria o banco, o usuário da aplicação e as tabelas, caso não existam.
 *
 * Ordem de tentativa:
 * 1. Conecta com as credenciais da aplicação (db.user / db.password);
 * 2. Se falhar, usa as credenciais administrativas (db.admin.*) para
 *    criar o banco, o usuário e conceder privilégios;
 * 3. Executa o DDL das tabelas (CREATE TABLE IF NOT EXISTS) sempre.
 */
public final class DatabaseSetup {
    private DatabaseSetup() {}

    private static final String[] DDL = {
            """
            CREATE TABLE IF NOT EXISTS users (
              id            BIGINT AUTO_INCREMENT PRIMARY KEY,
              name          VARCHAR(120)  NOT NULL,
              email         VARCHAR(180)  NOT NULL UNIQUE,
              password_hash VARCHAR(255)  NOT NULL,
              created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB""",
            """
            CREATE TABLE IF NOT EXISTS sessions (
              token      CHAR(64)  PRIMARY KEY,
              user_id    BIGINT    NOT NULL,
              expires_at TIMESTAMP NOT NULL,
              FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
            ) ENGINE=InnoDB""",
            """
            CREATE TABLE IF NOT EXISTS categories (
              id      BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT       NOT NULL,
              name    VARCHAR(80)  NOT NULL,
              type    ENUM('receita','despesa') NOT NULL,
              color   VARCHAR(7)   NOT NULL DEFAULT '#477B65',
              FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
              UNIQUE KEY uq_user_name (user_id, name)
            ) ENGINE=InnoDB""",
            """
            CREATE TABLE IF NOT EXISTS transactions (
              id          BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id     BIGINT        NOT NULL,
              category_id BIGINT        NULL,
              type        ENUM('receita','despesa') NOT NULL,
              description VARCHAR(200)  NOT NULL,
              amount      DECIMAL(12,2) NOT NULL,
              tx_date     DATE          NOT NULL,
              created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
              FOREIGN KEY (user_id)     REFERENCES users (id)      ON DELETE CASCADE,
              FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL,
              INDEX idx_user_date (user_id, tx_date)
            ) ENGINE=InnoDB""",
            """
            CREATE TABLE IF NOT EXISTS bills (
              id          BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id     BIGINT        NOT NULL,
              description VARCHAR(200)  NOT NULL,
              amount      DECIMAL(12,2) NOT NULL,
              due_date    DATE          NOT NULL,
              status      ENUM('pendente','pago') NOT NULL DEFAULT 'pendente',
              paid_date   DATE          NULL,
              created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
              FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
              INDEX idx_user_status (user_id, status, due_date)
            ) ENGINE=InnoDB""",
    };

    /** Executa a preparação completa. Lança IllegalStateException com instruções em caso de falha. */
    static void run() {
        String dbName = databaseName();
        try (Connection cn = DB.open()) {
            ensureTables(cn);
            System.out.println("[banco] Estrutura verificada em \"" + dbName + "\".");
            return;
        } catch (SQLException e) {
            System.out.println("[banco] \"" + dbName + "\" indisponível (" + resumo(e) + "). Tentando criar...");
        }

        bootstrap(dbName);

        try (Connection cn = DB.open()) {
            ensureTables(cn);
            System.out.println("[banco] Banco \"" + dbName + "\" criado e estrutura verificada.");
        } catch (SQLException e) {
            throw new IllegalStateException("""
                    Não foi possível preparar o banco de dados "%s".

                    Verifique:
                      1. Se o MySQL está em execução (start-mysql.bat);
                      2. As credenciais administrativas em config.properties
                         (db.admin.user / db.admin.password).

                    Detalhe técnico: %s""".formatted(dbName, e.getMessage()), e);
        }
    }

    /** Cria o banco, o usuário da aplicação e concede privilégios, usando a conta administrativa. */
    private static void bootstrap(String dbName) {
        String adminUrl = Config.get("db.admin.url");
        String adminUser = Config.get("db.admin.user");
        String adminPass = Config.get("db.admin.password", "");
        String appUser = Config.get("db.user");
        String appPass = Config.get("db.password", "");
        String host = urlHost();

        try (Connection cn = DriverManager.getConnection(adminUrl, adminUser, adminPass);
             Statement st = cn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS " + dbName
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            // Cria o usuário nos dois formatos de host mais comuns para acesso local.
            st.execute("CREATE USER IF NOT EXISTS '" + quote(appUser) + "'@'localhost'"
                    + " IDENTIFIED BY '" + quote(appPass) + "'");
            st.execute("CREATE USER IF NOT EXISTS '" + quote(appUser) + "'@'" + quote(host) + "'"
                    + " IDENTIFIED BY '" + quote(appPass) + "'");
            st.execute("GRANT ALL PRIVILEGES ON " + dbName + ".* TO '" + quote(appUser) + "'@'localhost'");
            st.execute("GRANT ALL PRIVILEGES ON " + dbName + ".* TO '" + quote(appUser) + "'@'" + quote(host) + "'");
            st.execute("FLUSH PRIVILEGES");
            System.out.println("[banco] Banco e usuário criados com a conta administrativa.");
        } catch (SQLException e) {
            throw new IllegalStateException("""
                    Falha ao criar o banco de dados com a conta administrativa (%s).

                    Confira db.admin.user e db.admin.password em config.properties.

                    Detalhe técnico: %s""".formatted(adminUser, e.getMessage()), e);
        }
    }

    /** Executa o DDL (CREATE TABLE IF NOT EXISTS) — seguro rodar a cada inicialização. */
    private static void ensureTables(Connection cn) throws SQLException {
        try (Statement st = cn.createStatement()) {
            for (String sql : DDL) {
                st.execute(sql);
            }
        }
    }

    private static String databaseName() {
        Matcher m = Pattern.compile("jdbc:mysql://[^/]+/(\\w+)").matcher(Config.get("db.url"));
        if (!m.find()) throw new IllegalStateException("db.url inválida em config.properties");
        return m.group(1);
    }

    private static String urlHost() {
        Matcher m = Pattern.compile("jdbc:mysql://([^:/]+)").matcher(Config.get("db.url"));
        if (!m.find()) throw new IllegalStateException("db.url inválida em config.properties");
        return m.group(1);
    }

    private static String quote(String s) {
        return s.replace("'", "''");
    }

    private static String resumo(SQLException e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg.split("\n")[0];
    }
}

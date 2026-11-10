package com.financas;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configurações da aplicação.
 * Lê o arquivo config.properties na raiz do projeto (com valores padrão caso não exista).
 */
public final class Config {
    private static final Properties PROPS = new Properties();

    static {
        PROPS.setProperty("db.url",
                "jdbc:mysql://localhost:3306/financeiro?useSSL=false&allowPublicKeyRetrieval=true"
                        + "&serverTimezone=UTC&characterEncoding=UTF-8");
        PROPS.setProperty("db.user", "root");
        PROPS.setProperty("db.password", "");
        // Credenciais administrativas usadas apenas para criar o banco/usuário
        // quando eles ainda não existem (executadas no início do sistema).
        PROPS.setProperty("db.admin.url",
                "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true"
                        + "&serverTimezone=UTC&characterEncoding=UTF-8");
        PROPS.setProperty("db.admin.user", "root");
        PROPS.setProperty("db.admin.password", "");
        PROPS.setProperty("server.port", "8080");
        PROPS.setProperty("web.dir", "web");

        Path file = Path.of("config.properties");
        if (Files.exists(file)) {
            try (FileInputStream in = new FileInputStream(file.toFile())) {
                PROPS.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("Aviso: não foi possível ler config.properties: " + e.getMessage());
            }
        }
    }

    private Config() {}

    public static String get(String key) {
        return PROPS.getProperty(key);
    }

    public static String get(String key, String defaultValue) {
        return PROPS.getProperty(key, defaultValue);
    }

    public static int getInt(String key) {
        return Integer.parseInt(PROPS.getProperty(key));
    }
}

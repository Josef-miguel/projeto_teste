package com.financas;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

/** Cadastro, login e sessões de usuários. */
public final class Auth {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int SESSION_DAYS = 7;
    public static final String COOKIE = "fin_token";

    private Auth() {}

    // ------------------------------------------------------------------
    // Cadastro
    // ------------------------------------------------------------------
    public static long register(String name, String email, String password) {
        if (name == null || name.isBlank()) throw new Http.HttpError(400, "Informe seu nome.");
        if (email == null || !email.matches("^[\\w.+\\-]+@[\\w\\-]+\\.[\\w.\\-]+$")) {
            throw new Http.HttpError(400, "Informe um e-mail válido.");
        }
        if (password == null || password.length() < 6) {
            throw new Http.HttpError(400, "A senha deve ter pelo menos 6 caracteres.");
        }
        email = email.trim().toLowerCase();
        String hash = Passwords.hash(password);
        try (Connection cn = DB.open()) {
            try (PreparedStatement ps = cn.prepareStatement("SELECT id FROM users WHERE email = ?")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) throw new Http.HttpError(409, "Já existe uma conta com este e-mail.");
                }
            }
            long userId;
            try (PreparedStatement ps = cn.prepareStatement(
                    "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name.trim());
                ps.setString(2, email);
                ps.setString(3, hash);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    userId = rs.getLong(1);
                }
            }
            seedDefaultCategories(cn, userId);
            return userId;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao registrar usuário", e);
        }
    }

    /** Categorias padrão criadas para cada novo usuário. */
    private static void seedDefaultCategories(Connection cn, long userId) throws SQLException {
        String[][] defaults = {
                {"Moradia", "despesa", "#D99B55"},
                {"Alimentação", "despesa", "#D9534F"},
                {"Transporte", "despesa", "#5B84A0"},
                {"Saúde", "despesa", "#3E8E7E"},
                {"Lazer", "despesa", "#C4A243"},
                {"Educação", "despesa", "#7D9C5A"},
                {"Outras despesas", "despesa", "#93A099"},
                {"Salário", "receita", "#2F6B4F"},
                {"Investimentos", "receita", "#477B65"},
                {"Outras receitas", "receita", "#6FA287"},
        };
        try (PreparedStatement ps = cn.prepareStatement(
                "INSERT INTO categories (user_id, name, type, color) VALUES (?, ?, ?, ?)")) {
            for (String[] d : defaults) {
                ps.setLong(1, userId);
                ps.setString(2, d[0]);
                ps.setString(3, d[1]);
                ps.setString(4, d[2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ------------------------------------------------------------------
    // Login / logout
    // ------------------------------------------------------------------
    public static Map<String, Object> login(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new Http.HttpError(400, "Informe e-mail e senha.");
        }
        email = email.trim().toLowerCase();
        try (Connection cn = DB.open()) {
            long userId = 0;
            String name = null;
            String hash = null;
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT id, name, password_hash FROM users WHERE email = ?")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getLong(1);
                        name = rs.getString(2);
                        hash = rs.getString(3);
                    }
                }
            }
            // Mensagem genérica para não revelar se o e-mail existe.
            if (hash == null || !Passwords.verify(password, hash)) {
                throw new Http.HttpError(401, "E-mail ou senha incorretos.");
            }
            String token = createSession(cn, userId);
            Map<String, Object> user = new HashMap<>();
            user.put("id", userId);
            user.put("name", name);
            user.put("email", email);
            Map<String, Object> out = new HashMap<>();
            out.put("token", token);
            out.put("user", user);
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao fazer login", e);
        }
    }

    public static void logout(String token) {
        if (token == null || token.isBlank()) return;
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement("DELETE FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Aviso: falha ao encerrar sessão: " + e.getMessage());
        }
    }

    public static long userIdFromToken(String token) {
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement(
                     "SELECT user_id FROM sessions WHERE token = ? AND expires_at > CURRENT_TIMESTAMP")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao validar sessão", e);
        }
    }

    /** Dados públicos do usuário (ou null se não existir). */
    public static Map<String, Object> userById(long id) {
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement("SELECT id, name, email FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getLong(1));
                user.put("name", rs.getString(2));
                user.put("email", rs.getString(3));
                return user;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário", e);
        }
    }

    private static String createSession(Connection cn, long userId) throws SQLException {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        LocalDateTime expires = LocalDateTime.now().plusDays(SESSION_DAYS);
        try (PreparedStatement ps = cn.prepareStatement(
                "INSERT INTO sessions (token, user_id, expires_at) VALUES (?, ?, ?)")) {
            ps.setString(1, token);
            ps.setLong(2, userId);
            ps.setTimestamp(3, Timestamp.valueOf(expires));
            ps.executeUpdate();
        }
        return token;
    }

    /** Cookie de sessão (HttpOnly). */
    public static String sessionCookie(String token) {
        return COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + (SESSION_DAYS * 24 * 3600);
    }

    public static String clearCookie() {
        return COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0";
    }
}

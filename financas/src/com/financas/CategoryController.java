package com.financas;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CRUD de categorias de receitas e despesas (escopo por usuário). */
public final class CategoryController {
    private CategoryController() {}

    public static void register(Router r) {
        r.add("GET", "/api/categories", CategoryController::list);
        r.add("POST", "/api/categories", CategoryController::create);
        r.add("PUT", "/api/categories/{id}", CategoryController::update);
        r.add("DELETE", "/api/categories/{id}", CategoryController::delete);
    }

    private static void list(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        String type = Http.q(ex, "type").toLowerCase();
        StringBuilder sql = new StringBuilder(
                "SELECT id, name, type, color FROM categories WHERE user_id = ?");
        if (!type.isEmpty()) sql.append(" AND type = ?");
        sql.append(" ORDER BY type, name");
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            ps.setLong(1, uid);
            if (!type.isEmpty()) ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(row(rs));
            }
        }
        Http.sendJson(ex, 200, out);
    }

    private static void create(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        Map<String, Object> body = Http.jsonBody(ex);
        String name = Validation.text(body.get("name"), "nome", 80);
        String type = Validation.txType(body.get("type"));
        String color = color(body.get("color"));

        try (Connection cn = DB.open()) {
            ensureUnique(cn, uid, name, -1);
            long id;
            try (PreparedStatement ps = cn.prepareStatement(
                    "INSERT INTO categories (user_id, name, type, color) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, uid);
                ps.setString(2, name);
                ps.setString(3, type);
                ps.setString(4, color);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    id = rs.getLong(1);
                }
            }
            Map<String, Object> out = new HashMap<>();
            out.put("id", id);
            out.put("name", name);
            out.put("type", type);
            out.put("color", color);
            Http.sendJson(ex, 201, out);
        }
    }

    private static void update(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        long id = Validation.id(params, "id");
        Map<String, Object> body = Http.jsonBody(ex);
        String name = Validation.text(body.get("name"), "nome", 80);
        String type = Validation.txType(body.get("type"));
        String color = color(body.get("color"));

        try (Connection cn = DB.open()) {
            ensureOwned(cn, uid, id);
            ensureUnique(cn, uid, name, id);
            try (PreparedStatement ps = cn.prepareStatement(
                    "UPDATE categories SET name = ?, type = ?, color = ? WHERE id = ? AND user_id = ?")) {
                ps.setString(1, name);
                ps.setString(2, type);
                ps.setString(3, color);
                ps.setLong(4, id);
                ps.setLong(5, uid);
                ps.executeUpdate();
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("id", id);
        out.put("name", name);
        out.put("type", type);
        out.put("color", color);
        Http.sendJson(ex, 200, out);
    }

    private static void delete(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        long id = Validation.id(params, "id");
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement(
                     "DELETE FROM categories WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, uid);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new Http.HttpError(404, "Categoria não encontrada.");
        }
        Http.sendJson(ex, 200, Map.of("message", "Categoria excluída."));
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> c = new HashMap<>();
        c.put("id", rs.getLong("id"));
        c.put("name", rs.getString("name"));
        c.put("type", rs.getString("type"));
        c.put("color", rs.getString("color"));
        return c;
    }

    private static String color(Object o) {
        String s = o == null ? "" : String.valueOf(o).trim();
        return s.matches("#[0-9a-fA-F]{6}") ? s.toLowerCase() : "#477B65";
    }

    private static void ensureOwned(Connection cn, long uid, long id) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT id FROM categories WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new Http.HttpError(404, "Categoria não encontrada.");
            }
        }
    }

    private static void ensureUnique(Connection cn, long uid, String name, long ignoreId) throws SQLException {
        String sql = "SELECT id FROM categories WHERE user_id = ? AND name = ? AND id <> ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, uid);
            ps.setString(2, name);
            ps.setLong(3, ignoreId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) throw new Http.HttpError(409, "Já existe uma categoria com esse nome.");
            }
        }
    }
}

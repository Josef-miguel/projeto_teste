package com.financas;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CRUD de transações (receitas e despesas), com filtros por mês, tipo e categoria. */
public final class TransactionController {
    private TransactionController() {}

    public static void register(Router r) {
        r.add("GET", "/api/transactions", TransactionController::list);
        r.add("POST", "/api/transactions", TransactionController::create);
        r.add("PUT", "/api/transactions/{id}", TransactionController::update);
        r.add("DELETE", "/api/transactions/{id}", TransactionController::delete);
    }

    private static void list(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        String month = Http.q(ex, "month");
        String type = Http.q(ex, "type").toLowerCase();
        String category = Http.q(ex, "category_id");

        StringBuilder sql = new StringBuilder("""
                SELECT t.id, t.type, t.description, t.amount,
                       DATE_FORMAT(t.tx_date, '%Y-%m-%d') AS tx_date,
                       t.category_id, c.name AS category_name, c.color AS category_color
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = ?""");
        if (!month.isEmpty()) sql.append(" AND DATE_FORMAT(t.tx_date, '%Y-%m') = ?");
        if (!type.isEmpty()) sql.append(" AND t.type = ?");
        if (!category.isEmpty()) sql.append(" AND t.category_id = ?");
        sql.append(" ORDER BY t.tx_date DESC, t.id DESC LIMIT 500");

        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setLong(i++, uid);
            if (!month.isEmpty()) ps.setString(i++, Validation.month(month));
            if (!type.isEmpty()) ps.setString(i++, type);
            if (!category.isEmpty()) ps.setLong(i, Long.parseLong(category));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(row(rs));
            }
        }
        Http.sendJson(ex, 200, out);
    }

    private static void create(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        Map<String, Object> body = Http.jsonBody(ex);
        String type = Validation.txType(body.get("type"));
        String description = Validation.text(body.get("description"), "descrição", 200);
        BigDecimal amount = Validation.money(body.get("amount"));
        LocalDate date = Validation.date(body.get("date"), "data");
        Long categoryId = categoryIdOrNull(uid, body, type);

        long id;
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement("""
                     INSERT INTO transactions (user_id, category_id, type, description, amount, tx_date)
                     VALUES (?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, uid);
            if (categoryId == null) ps.setNull(2, java.sql.Types.BIGINT);
            else ps.setLong(2, categoryId);
            ps.setString(3, type);
            ps.setString(4, description);
            ps.setBigDecimal(5, amount);
            ps.setDate(6, java.sql.Date.valueOf(date));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                id = rs.getLong(1);
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("id", id);
        out.put("message", "Transação registrada.");
        Http.sendJson(ex, 201, out);
    }

    private static void update(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        long id = Validation.id(params, "id");
        Map<String, Object> body = Http.jsonBody(ex);
        String type = Validation.txType(body.get("type"));
        String description = Validation.text(body.get("description"), "descrição", 200);
        BigDecimal amount = Validation.money(body.get("amount"));
        LocalDate date = Validation.date(body.get("date"), "data");
        Long categoryId = categoryIdOrNull(uid, body, type);

        try (Connection cn = DB.open()) {
            ensureOwned(cn, uid, id);
            try (PreparedStatement ps = cn.prepareStatement("""
                    UPDATE transactions
                    SET category_id = ?, type = ?, description = ?, amount = ?, tx_date = ?
                    WHERE id = ? AND user_id = ?""")) {
                if (categoryId == null) ps.setNull(1, java.sql.Types.BIGINT);
                else ps.setLong(1, categoryId);
                ps.setString(2, type);
                ps.setString(3, description);
                ps.setBigDecimal(4, amount);
                ps.setDate(5, java.sql.Date.valueOf(date));
                ps.setLong(6, id);
                ps.setLong(7, uid);
                ps.executeUpdate();
            }
        }
        Http.sendJson(ex, 200, Map.of("message", "Transação atualizada."));
    }

    private static void delete(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        long id = Validation.id(params, "id");
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement(
                     "DELETE FROM transactions WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, uid);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new Http.HttpError(404, "Transação não encontrada.");
        }
        Http.sendJson(ex, 200, Map.of("message", "Transação excluída."));
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> t = new HashMap<>();
        t.put("id", rs.getLong("id"));
        t.put("type", rs.getString("type"));
        t.put("description", rs.getString("description"));
        t.put("amount", rs.getBigDecimal("amount"));
        t.put("date", rs.getString("tx_date"));
        long catId = rs.getLong("category_id");
        t.put("category_id", rs.wasNull() ? null : catId);
        t.put("category_name", rs.getString("category_name"));
        t.put("category_color", rs.getString("category_color"));
        return t;
    }

    /** Garante que a categoria (quando informada) pertence ao usuário e ao tipo. */
    private static Long categoryIdOrNull(long uid, Map<String, Object> body, String type) throws SQLException {
        Object raw = body.get("category_id");
        if (raw == null || String.valueOf(raw).isBlank() || String.valueOf(raw).equals("null")) return null;
        long categoryId;
        try {
            categoryId = Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new Http.HttpError(400, "Categoria inválida.");
        }
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement(
                     "SELECT type FROM categories WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, categoryId);
            ps.setLong(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new Http.HttpError(400, "Categoria não encontrada.");
                if (!type.equals(rs.getString(1))) {
                    throw new Http.HttpError(400, "A categoria escolhida não é do tipo \"" + type + "\".");
                }
            }
        }
        return categoryId;
    }

    private static void ensureOwned(Connection cn, long uid, long id) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT id FROM transactions WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new Http.HttpError(404, "Transação não encontrada.");
            }
        }
    }
}

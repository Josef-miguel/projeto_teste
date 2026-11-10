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

/** CRUD das contas a pagar, com marcação de pagamento e filtros. */
public final class BillController {
    private BillController() {}

    public static void register(Router r) {
        r.add("GET", "/api/bills", BillController::list);
        r.add("POST", "/api/bills", BillController::create);
        r.add("PUT", "/api/bills/{id}", BillController::update);
        r.add("POST", "/api/bills/{id}/pay", BillController::pay);
        r.add("POST", "/api/bills/{id}/unpay", BillController::unpay);
        r.add("DELETE", "/api/bills/{id}", BillController::delete);
    }

    private static void list(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        String status = Http.q(ex, "status").toLowerCase(); // pendente | pago | vencida
        String month = Http.q(ex, "month");

        StringBuilder sql = new StringBuilder("""
                SELECT id, description, amount,
                       DATE_FORMAT(due_date, '%Y-%m-%d') AS due_date,
                       status,
                       DATE_FORMAT(paid_date, '%Y-%m-%d') AS paid_date
                FROM bills
                WHERE user_id = ?""");
        if (!month.isEmpty()) sql.append(" AND DATE_FORMAT(due_date, '%Y-%m') = ?");
        switch (status) {
            case "pendente" -> sql.append(" AND status = 'pendente'");
            case "pago" -> sql.append(" AND status = 'pago'");
            case "vencida" -> sql.append(" AND status = 'pendente' AND due_date < CURDATE()");
            default -> { /* todas */ }
        }
        sql.append(" ORDER BY due_date ASC, id ASC LIMIT 500");

        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setLong(i++, uid);
            if (!month.isEmpty()) ps.setString(i, Validation.month(month));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(row(rs));
            }
        }
        Http.sendJson(ex, 200, out);
    }

    private static void create(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        Map<String, Object> body = Http.jsonBody(ex);
        String description = Validation.text(body.get("description"), "descrição", 200);
        BigDecimal amount = Validation.money(body.get("amount"));
        LocalDate dueDate = Validation.date(body.get("due_date"), "vencimento");

        long id;
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement(
                     "INSERT INTO bills (user_id, description, amount, due_date) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, uid);
            ps.setString(2, description);
            ps.setBigDecimal(3, amount);
            ps.setDate(4, java.sql.Date.valueOf(dueDate));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                id = rs.getLong(1);
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("id", id);
        out.put("message", "Conta a pagar registrada.");
        Http.sendJson(ex, 201, out);
    }

    private static void update(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        long id = Validation.id(params, "id");
        Map<String, Object> body = Http.jsonBody(ex);
        String description = Validation.text(body.get("description"), "descrição", 200);
        BigDecimal amount = Validation.money(body.get("amount"));
        LocalDate dueDate = Validation.date(body.get("due_date"), "vencimento");
        String status = String.valueOf(body.getOrDefault("status", "pendente")).trim().toLowerCase();
        if (!status.equals("pendente") && !status.equals("pago")) {
            throw new Http.HttpError(400, "Status inválido (use \"pendente\" ou \"pago\").");
        }

        try (Connection cn = DB.open()) {
            ensureOwned(cn, uid, id);
            try (PreparedStatement ps = cn.prepareStatement("""
                    UPDATE bills
                    SET description = ?, amount = ?, due_date = ?, status = ?,
                        paid_date = CASE WHEN ? = 'pago' THEN COALESCE(paid_date, CURRENT_DATE) ELSE NULL END
                    WHERE id = ? AND user_id = ?""")) {
                ps.setString(1, description);
                ps.setBigDecimal(2, amount);
                ps.setDate(3, java.sql.Date.valueOf(dueDate));
                ps.setString(4, status);
                ps.setString(5, status);
                ps.setLong(6, id);
                ps.setLong(7, uid);
                ps.executeUpdate();
            }
        }
        Http.sendJson(ex, 200, Map.of("message", "Conta atualizada."));
    }

    /** Marca a conta como paga (define a data de pagamento como hoje). */
    private static void pay(HttpExchange ex, Map<String, String> params) throws Exception {
        setPaid(ex, params, true);
    }

    /** Reverte o pagamento (volta para pendente). */
    private static void unpay(HttpExchange ex, Map<String, String> params) throws Exception {
        setPaid(ex, params, false);
    }

    private static void setPaid(HttpExchange ex, Map<String, String> params, boolean paid) throws Exception {
        long uid = Http.requireUser(ex);
        long id = Validation.id(params, "id");
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement("""
                     UPDATE bills
                     SET status = ?, paid_date = ?
                     WHERE id = ? AND user_id = ?""")) {
            ps.setString(1, paid ? "pago" : "pendente");
            ps.setDate(2, paid ? java.sql.Date.valueOf(LocalDate.now()) : null);
            ps.setLong(3, id);
            ps.setLong(4, uid);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new Http.HttpError(404, "Conta não encontrada.");
        }
        Http.sendJson(ex, 200, Map.of("message", paid ? "Conta marcada como paga." : "Pagamento revertido."));
    }

    private static void delete(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        long id = Validation.id(params, "id");
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement("DELETE FROM bills WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, uid);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new Http.HttpError(404, "Conta não encontrada.");
        }
        Http.sendJson(ex, 200, Map.of("message", "Conta excluída."));
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> b = new HashMap<>();
        b.put("id", rs.getLong("id"));
        b.put("description", rs.getString("description"));
        b.put("amount", rs.getBigDecimal("amount"));
        b.put("due_date", rs.getString("due_date"));
        b.put("status", rs.getString("status"));
        b.put("paid_date", rs.getString("paid_date"));
        return b;
    }

    private static void ensureOwned(Connection cn, long uid, long id) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT id FROM bills WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new Http.HttpError(404, "Conta não encontrada.");
            }
        }
    }
}

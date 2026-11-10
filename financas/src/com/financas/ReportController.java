package com.financas;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Relatórios mensais/anuais e exportação em CSV. */
public final class ReportController {
    private ReportController() {}

    public static void register(Router r) {
        r.add("GET", "/api/reports/summary", ReportController::summary);
        r.add("GET", "/api/reports/yearly", ReportController::yearly);
        r.add("GET", "/api/export/csv", ReportController::exportCsv);
    }

    // ------------------------------------------------------------------
    // Resumo do mês: totais, saldo, categorias e contas a pagar
    // ------------------------------------------------------------------
    private static void summary(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        String monthParam = Http.q(ex, "month");
        String month = Validation.month(monthParam.isEmpty() ? LocalDate.now().toString().substring(0, 7) : monthParam);

        BigDecimal receitas = BigDecimal.ZERO;
        BigDecimal despesas = BigDecimal.ZERO;
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement("""
                     SELECT type, COALESCE(SUM(amount), 0) AS total
                     FROM transactions
                     WHERE user_id = ? AND DATE_FORMAT(tx_date, '%Y-%m') = ?
                     GROUP BY type""")) {
            ps.setLong(1, uid);
            ps.setString(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if ("receita".equals(rs.getString("type"))) receitas = rs.getBigDecimal("total");
                    else despesas = rs.getBigDecimal("total");
                }
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("month", month);
        out.put("totalReceitas", receitas);
        out.put("totalDespesas", despesas);
        out.put("saldo", receitas.subtract(despesas));
        out.put("receitasPorCategoria", byCategory(uid, month, "receita"));
        out.put("despesasPorCategoria", byCategory(uid, month, "despesa"));
        out.put("bills", billsSummary(uid, month));
        Http.sendJson(ex, 200, out);
    }

    private static List<Map<String, Object>> byCategory(long uid, String month, String type) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = """
                SELECT COALESCE(c.name, 'Sem categoria') AS name,
                       COALESCE(c.color, '#93A099')      AS color,
                       SUM(t.amount)                     AS total
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = ? AND t.type = ? AND DATE_FORMAT(t.tx_date, '%Y-%m') = ?
                GROUP BY COALESCE(c.name, 'Sem categoria'), COALESCE(c.color, '#93A099')
                ORDER BY total DESC""";
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, uid);
            ps.setString(2, type);
            ps.setString(3, month);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("color", rs.getString("color"));
                    row.put("total", rs.getBigDecimal("total"));
                    out.add(row);
                }
            }
        }
        return out;
    }

    private static Map<String, Object> billsSummary(long uid, String month) throws SQLException {
        Map<String, Object> out = new HashMap<>();
        String sql = """
                SELECT
                  SUM(status = 'pendente')                          AS pendentes_count,
                  COALESCE(SUM(CASE WHEN status = 'pendente' THEN amount END), 0) AS pendentes_total,
                  SUM(status = 'pendente' AND due_date < CURDATE()) AS vencidas_count,
                  COALESCE(SUM(CASE WHEN status = 'pendente' AND due_date < CURDATE() THEN amount END), 0) AS vencidas_total
                FROM bills
                WHERE user_id = ? AND DATE_FORMAT(due_date, '%Y-%m') = ?""";
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, uid);
            ps.setString(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    out.put("pendentesCount", rs.getInt("pendentes_count"));
                    out.put("pendentesTotal", rs.getBigDecimal("pendentes_total"));
                    out.put("vencidasCount", rs.getInt("vencidas_count"));
                    out.put("vencidasTotal", rs.getBigDecimal("vencidas_total"));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Visão anual: totais por mês (usado no gráfico de barras)
    // ------------------------------------------------------------------
    private static void yearly(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        String yearParam = Http.q(ex, "year");
        int year = Validation.year(yearParam.isEmpty() ? String.valueOf(LocalDate.now().getYear()) : yearParam);

        Map<String, BigDecimal[]> byMonth = new HashMap<>();
        try (Connection cn = DB.open();
             PreparedStatement ps = cn.prepareStatement("""
                     SELECT DATE_FORMAT(tx_date, '%Y-%m') AS ym, type, SUM(amount) AS total
                     FROM transactions
                     WHERE user_id = ? AND tx_date BETWEEN ? AND ?
                     GROUP BY ym, type""")) {
            ps.setLong(1, uid);
            ps.setDate(2, java.sql.Date.valueOf(year + "-01-01"));
            ps.setDate(3, java.sql.Date.valueOf(year + "-12-31"));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal[] totals = byMonth.computeIfAbsent(rs.getString("ym"),
                            k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                    if ("receita".equals(rs.getString("type"))) totals[0] = rs.getBigDecimal("total");
                    else totals[1] = rs.getBigDecimal("total");
                }
            }
        }
        List<Map<String, Object>> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            String ym = String.format("%04d-%02d", year, m);
            BigDecimal[] t = byMonth.getOrDefault(ym, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            Map<String, Object> row = new HashMap<>();
            row.put("ym", ym);
            row.put("receitas", t[0]);
            row.put("despesas", t[1]);
            months.add(row);
        }
        Http.sendJson(ex, 200, Map.of("year", year, "months", months));
    }

    // ------------------------------------------------------------------
    // Exportação em CSV (Excel pt-BR: separador ";" e decimais com vírgula)
    // ------------------------------------------------------------------
    private static void exportCsv(HttpExchange ex, Map<String, String> params) throws Exception {
        long uid = Http.requireUser(ex);
        String kind = Http.q(ex, "kind").isBlank() ? "transactions" : Http.q(ex, "kind");
        String month = Http.q(ex, "month");
        String year = Http.q(ex, "year");
        if (!month.isEmpty()) Validation.month(month);
        if (!year.isEmpty()) Validation.year(year);

        StringBuilder csv = new StringBuilder();
        String fileName;
        if (kind.equals("bills")) {
            csv.append("Vencimento;Descrição;Valor (R$);Status;Pago em\r\n");
            fileName = "contas-a-pagar";
            try (Connection cn = DB.open();
                 PreparedStatement ps = cn.prepareStatement("""
                         SELECT DATE_FORMAT(due_date, '%d/%m/%Y'), description, amount, status,
                                COALESCE(DATE_FORMAT(paid_date, '%d/%m/%Y'), '')
                         FROM bills
                         WHERE user_id = ? """ + whereDate(month, year, "due_date")
                                 + " ORDER BY due_date, id")) {
                bindDate(ps, uid, month, year);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        csv.append(field(rs.getString(1))).append(';')
                                .append(field(rs.getString(2))).append(';')
                                .append(decimal(rs.getBigDecimal(3))).append(';')
                                .append(field("pago".equals(rs.getString(4)) ? "Pago" : "Pendente")).append(';')
                                .append(field(rs.getString(5))).append("\r\n");
                    }
                }
            }
        } else {
            csv.append("Data;Tipo;Categoria;Descrição;Valor (R$)\r\n");
            fileName = "transacoes";
            try (Connection cn = DB.open();
                 PreparedStatement ps = cn.prepareStatement("""
                         SELECT DATE_FORMAT(t.tx_date, '%d/%m/%Y'), t.type,
                                COALESCE(c.name, 'Sem categoria'), t.description, t.amount
                         FROM transactions t
                         LEFT JOIN categories c ON c.id = t.category_id
                         WHERE t.user_id = ? """ + whereDate(month, year, "t.tx_date")
                                 + " ORDER BY t.tx_date, t.id")) {
                bindDate(ps, uid, month, year);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        csv.append(field(rs.getString(1))).append(';')
                                .append(field("receita".equals(rs.getString(2)) ? "Receita" : "Despesa")).append(';')
                                .append(field(rs.getString(3))).append(';')
                                .append(field(rs.getString(4))).append(';')
                                .append(decimal(rs.getBigDecimal(5))).append("\r\n");
                    }
                }
            }
        }

        // Sufixo do nome do arquivo conforme o período
        if (!month.isEmpty()) fileName += "-" + month;
        else if (!year.isEmpty()) fileName += "-" + year;
        fileName += ".csv";

        // BOM para o Excel reconhecer UTF-8 (acentuação)
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=" + fileName);
        ex.sendResponseHeaders(200, body.length);
        try (var os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    /** Filtro de período opcional: mês (AAAA-MM) ou ano (AAAA). */
    private static String whereDate(String month, String year, String column) {
        if (!month.isEmpty()) return " AND DATE_FORMAT(" + column + ", '%Y-%m') = ?";
        if (!year.isEmpty()) return " AND YEAR(" + column + ") = ?";
        return "";
    }

    private static void bindDate(PreparedStatement ps, long uid, String month, String year) throws SQLException {
        ps.setLong(1, uid);
        if (!month.isEmpty()) ps.setString(2, month);
        else if (!year.isEmpty()) ps.setInt(2, Integer.parseInt(year));
    }

    /** Escapa um campo CSV, envolvendo em aspas quando necessário. */
    private static String field(String s) {
        String v = s == null ? "" : s;
        String escaped = v.replace("\"", "\"\"");
        boolean quotes = v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        return quotes ? "\"" + escaped + "\"" : escaped;
    }

    /** Valor no formato brasileiro: 1234,56 */
    private static String decimal(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}

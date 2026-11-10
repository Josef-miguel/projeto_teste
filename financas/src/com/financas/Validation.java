package com.financas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Validação e conversão de dados enviados pelo cliente. */
public final class Validation {
    private Validation() {}

    /** Texto obrigatório, sem espaços nas pontas. */
    public static String text(Object o, String field, int max) {
        String s = o == null ? "" : String.valueOf(o).trim();
        if (s.isEmpty()) throw new Http.HttpError(400, "O campo \"" + field + "\" é obrigatório.");
        if (s.length() > max) throw new Http.HttpError(400, "O campo \"" + field + "\" excede " + max + " caracteres.");
        return s;
    }

    /** Texto opcional. */
    public static String optionalText(Object o, int max) {
        String s = o == null ? "" : String.valueOf(o).trim();
        if (s.length() > max) throw new Http.HttpError(400, "Texto excede " + max + " caracteres.");
        return s;
    }

    /** Valor monetário positivo (aceita número JSON ou texto com vírgula). */
    public static BigDecimal money(Object o) {
        BigDecimal v = null;
        if (o instanceof Number n) v = new BigDecimal(n.toString());
        else if (o instanceof String s && !s.isBlank()) {
            try {
                v = new BigDecimal(s.trim().replace(",", "."));
            } catch (NumberFormatException ignored) {
                // tratado abaixo
            }
        }
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            throw new Http.HttpError(400, "Valor inválido: informe um número maior que zero.");
        }
        if (v.scale() > 2) v = v.setScale(2, RoundingMode.HALF_UP);
        return v;
    }

    /** Data ISO (AAAA-MM-DD). */
    public static LocalDate date(Object o, String field) {
        try {
            return LocalDate.parse(String.valueOf(o));
        } catch (Exception e) {
            throw new Http.HttpError(400, "Data inválida em \"" + field + "\" (use AAAA-MM-DD).");
        }
    }

    /** Mês no formato AAAA-MM. */
    public static String month(Object o) {
        String s = String.valueOf(o == null ? "" : o).trim();
        if (!s.matches("\\d{4}-\\d{2}")) {
            throw new Http.HttpError(400, "Mês inválido (use o formato AAAA-MM).");
        }
        int m = Integer.parseInt(s.substring(5, 7));
        if (m < 1 || m > 12) throw new Http.HttpError(400, "Mês inválido.");
        return s;
    }

    /** Ano (quatro dígitos). */
    public static int year(Object o) {
        String s = String.valueOf(o == null ? "" : o).trim();
        if (!s.matches("\\d{4}")) throw new Http.HttpError(400, "Ano inválido.");
        return Integer.parseInt(s);
    }

    /** Tipo de lançamento ("receita" ou "despesa"). */
    public static String txType(Object o) {
        String s = String.valueOf(o == null ? "" : o).trim().toLowerCase();
        if (!s.equals("receita") && !s.equals("despesa")) {
            throw new Http.HttpError(400, "Tipo inválido (use \"receita\" ou \"despesa\").");
        }
        return s;
    }

    /** Id numérico de rota (-1 quando ausente/inválido). */
    public static long id(Map<String, String> params, String key) {
        String s = params.get(key);
        if (s == null) return -1;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new Http.HttpError(400, "Identificador inválido.");
        }
    }
}

package com.financas;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Utilitários HTTP: respostas JSON, leitura de corpo, cookies, query params e erros. */
public final class Http {
    private Http() {}

    /** Erro de aplicação com status HTTP associado. */
    public static class HttpError extends RuntimeException {
        public final int status;

        public HttpError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    public static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendError(HttpExchange ex, int status, String message) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        sendJson(ex, status, body);
    }

    public static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    public static Map<String, String> cookies(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return out;
        for (String part : header.split(";")) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                out.put(part.substring(0, idx).trim(), part.substring(idx + 1).trim());
            }
        }
        return out;
    }

    public static Map<String, String> queryParams(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null || q.isBlank()) return out;
        for (String pair : q.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                out.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    /** Valor de um parâmetro de query ("" quando ausente). */
    public static String q(HttpExchange ex, String name) {
        return queryParams(ex).getOrDefault(name, "");
    }

    /** Corpo da requisição já convertido em mapa JSON (vazio quando o corpo é vazio). */
    public static Map<String, Object> jsonBody(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        if (body.isBlank()) return new HashMap<>();
        return Json.parseObject(body);
    }

    /** Obtém o id do usuário autenticado (cookie de sessão ou token Bearer). Lança 401 se inválido. */
    public static long requireUser(HttpExchange ex) {
        String token = cookies(ex).get("fin_token");
        if (token == null) {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) token = auth.substring(7).trim();
        }
        if (token == null) throw new HttpError(401, "Não autenticado. Faça login novamente.");
        long userId = Auth.userIdFromToken(token);
        if (userId <= 0) throw new HttpError(401, "Sessão expirada. Faça login novamente.");
        return userId;
    }
}

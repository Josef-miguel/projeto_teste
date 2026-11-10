package com.financas;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Endpoints de cadastro, login, sessão e logout. */
public final class AuthController {
    private AuthController() {}

    public static void register(Router r) {
        r.add("POST", "/api/auth/register", (ex, params) -> {
            Map<String, Object> body = Http.jsonBody(ex);
            String email = (String) body.get("email");
            Auth.register((String) body.get("name"), email, (String) body.get("password"));
            // já autentica o usuário recém-criado
            Map<String, Object> result = Auth.login(email, (String) body.get("password"));
            ex.getResponseHeaders().add("Set-Cookie", Auth.sessionCookie((String) result.get("token")));
            Map<String, Object> out = new HashMap<>();
            out.put("message", "Conta criada com sucesso!");
            out.put("user", result.get("user"));
            Http.sendJson(ex, 201, out);
        });

        r.add("POST", "/api/auth/login", (ex, params) -> {
            Map<String, Object> body = Http.jsonBody(ex);
            Map<String, Object> result = Auth.login((String) body.get("email"), (String) body.get("password"));
            ex.getResponseHeaders().add("Set-Cookie", Auth.sessionCookie((String) result.get("token")));
            Map<String, Object> out = new HashMap<>();
            out.put("message", "Login realizado.");
            out.put("user", result.get("user"));
            Http.sendJson(ex, 200, out);
        });

        r.add("POST", "/api/auth/logout", (ex, params) -> {
            String token = Http.cookies(ex).get(Auth.COOKIE);
            Auth.logout(token);
            ex.getResponseHeaders().add("Set-Cookie", Auth.clearCookie());
            Map<String, Object> out = new HashMap<>();
            out.put("message", "Sessão encerrada.");
            Http.sendJson(ex, 200, out);
        });

        r.add("GET", "/api/auth/me", (ex, params) -> {
            long userId = Http.requireUser(ex);
            Map<String, Object> user = Auth.userById(userId);
            if (user == null) throw new Http.HttpError(401, "Sessão inválida.");
            Http.sendJson(ex, 200, user);
        });
    }
}

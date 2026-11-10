package com.financas;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Roteador simples para a API: associa "MÉTODO /caminho/{param}" a handlers.
 * Ex.: router.add("GET", "/api/transactions/{id}", handler);
 */
public final class Router {
    public interface Handler {
        void handle(HttpExchange ex, Map<String, String> params) throws Exception;
    }

    private record Route(String method, Pattern pattern, List<String> vars, Handler handler) {}

    private final List<Route> routes = new ArrayList<>();

    public void add(String method, String path, Handler handler) {
        List<String> vars = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        for (String seg : path.split("/")) {
            if (seg.isEmpty()) continue;
            if (seg.startsWith("{") && seg.endsWith("}")) {
                vars.add(seg.substring(1, seg.length() - 1));
                regex.append("/([^/]+)");
            } else {
                regex.append('/').append(Pattern.quote(seg));
            }
        }
        regex.append('$');
        routes.add(new Route(method, Pattern.compile(regex.toString()), vars, handler));
    }

    public void dispatch(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            for (Route r : routes) {
                if (!r.method().equals(method)) continue;
                Matcher m = r.pattern().matcher(path);
                if (!m.matches()) continue;
                Map<String, String> params = new HashMap<>();
                for (int i = 0; i < r.vars().size(); i++) {
                    params.put(r.vars().get(i), decode(m.group(i + 1)));
                }
                r.handler().handle(ex, params);
                return;
            }
            Http.sendError(ex, 404, "Rota não encontrada: " + method + " " + path);
        } catch (Http.HttpError e) {
            Http.sendError(ex, e.status, e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro interno em " + method + " " + path);
            e.printStackTrace();
            Http.sendError(ex, 500, "Erro interno do servidor");
        }
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}

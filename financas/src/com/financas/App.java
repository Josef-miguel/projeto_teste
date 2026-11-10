package com.financas;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Sistema de Gerenciamento Financeiro Pessoal.
 * Servidor HTTP embutido (JDK) + JDBC/MySQL + front-end estático em web/.
 */
public final class App {
    public static void main(String[] args) {
        int port;
        Path webDir;
        try {
            // Cria o banco, o usuário e as tabelas antes de qualquer operação.
            DatabaseSetup.run();

            port = Config.getInt("server.port");
            webDir = Paths.get(Config.get("web.dir")).toAbsolutePath().normalize();

            Router api = new Router();
            AuthController.register(api);
            CategoryController.register(api);
            TransactionController.register(api);
            BillController.register(api);
            ReportController.register(api);

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api", api::dispatch);
            server.createContext("/", ex -> serveStatic(ex, webDir));
            server.setExecutor(Executors.newFixedThreadPool(20));
            server.start();
        } catch (IllegalStateException e) {
            System.err.println("==========================================================");
            System.err.println("  NÃO FOI POSSÍVEL INICIAR O SISTEMA");
            System.err.println("==========================================================");
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        } catch (IOException e) {
            System.err.println("Erro ao iniciar o servidor HTTP: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("==========================================================");
        System.out.println("  Sistema de Gerenciamento Financeiro Pessoal");
        System.out.println("  Servidor iniciado:  http://localhost:" + port);
        System.out.println("  Arquivos estáticos: " + webDir);
        System.out.println("  Para encerrar: Ctrl+C");
        System.out.println("==========================================================");
    }

    // ------------------------------------------------------------------
    // Arquivos estáticos (HTML, CSS, JS) com proteção contra path traversal
    // ------------------------------------------------------------------
    private static void serveStatic(HttpExchange ex, Path webDir) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        try {
            Path target = webDir.resolve(path.substring(1)).normalize();
            if (!target.startsWith(webDir) || !Files.isRegularFile(target)) {
                notFound(ex);
                return;
            }
            byte[] bytes = Files.readAllBytes(target);
            ex.getResponseHeaders().set("Content-Type", contentType(path));
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            System.err.println("Erro ao servir " + path + ": " + e.getMessage());
            notFound(ex);
        }
    }

    private static void notFound(HttpExchange ex) throws IOException {
        byte[] body = "<h1>404 - Página não encontrada</h1>"
                .getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(404, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static String contentType(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}

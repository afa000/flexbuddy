package com.angel.flexbuddy.testserver;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Real HTTP against the embedded server, so error dispatches, static resources and HEAD behave as on Render. */
final class TestServerHttp {

    static final String HEADER = "X-Robots-Tag";
    static final String BANNER_EN = "made-up data only";
    static final String BANNER_ES = "solo datos ficticios";
    static final String PRODUCTION_LINK = "<a href=\"https://flexbuddy.onrender.com\">flexbuddy.onrender.com</a>";

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final int port;

    TestServerHttp(int port) {
        this.port = port;
    }

    HttpResponse<String> send(String method, String path, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (headers.length > 0) request.headers(headers);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        return send("GET", path, headers);
    }

    /** Every full-page template: the files directly under templates/, fragments excluded. */
    static List<String> pageTemplates() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/templates"))) {
            return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".html")).map(name -> name.substring(0, name.length() - 5))
                    .sorted().toList();
        }
    }

    /** The requests whose responses must carry noindex on the test server and must not elsewhere. */
    static final String[] PLAIN_GETS = {"/login", "/css/styles.css", "/sw.js", "/manifest.webmanifest",
            "/icons/icon-192.png", "/actuator/health", "/offline.html"};
}

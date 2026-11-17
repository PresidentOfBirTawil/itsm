package com.itsm.desktop;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Makes sure only one copy runs per user: the running copy writes its port
 * to a small file in the data folder, and a second copy that finds the file
 * (and a server answering on that port) opens the browser instead of
 * starting again. Two copies would otherwise fight over the database file.
 */
final class RunningInstance {

    private final Path portFile;
    private final String product;

    RunningInstance(Path dataDirectory, String product) {
        this.portFile = dataDirectory.resolve("running.port");
        this.product = product;
    }

    /** The port of a copy that is running and answering, if there is one. */
    Optional<Integer> find() {
        if (!Files.exists(portFile)) {
            return Optional.empty();
        }
        try {
            int port = Integer.parseInt(Files.readString(portFile).trim());
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                            URI.create("http://localhost:" + port + "/login")).timeout(Duration.ofSeconds(3)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body().contains("content=\"" + product + "\"")) {
                return Optional.of(port);
            }
        } catch (IOException | NumberFormatException e) {
            // A file left behind by a copy that did not shut down cleanly.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        forget();
        return Optional.empty();
    }

    void remember(int port) {
        try {
            Files.writeString(portFile, String.valueOf(port));
            portFile.toFile().deleteOnExit();
        } catch (IOException e) {
            // Not fatal: a second copy would then just fail to start instead of opening this one.
        }
    }

    void forget() {
        try {
            Files.deleteIfExists(portFile);
        } catch (IOException e) {
            // Nothing to do.
        }
    }
}

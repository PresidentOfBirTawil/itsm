package com.quell.webhook;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.quell.config.Brand;
import org.springframework.stereotype.Component;

/**
 * Sends one message over HTTPS (or HTTP) and reports what happened. Messages
 * to apps carry a signature, so the receiver can check they really came
 * from this installation and were not changed or replayed:
 *
 * <pre>
 * X-Quell-Timestamp: 1763372400
 * X-Quell-Signature: sha256=hex(HMAC-SHA256(secret, timestamp + "." + body))
 * </pre>
 */
@Component
public class WebhookSender {

    static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** What happened: the status code (null if there was no answer) and, if it failed, why. */
    public record Result(boolean ok, Integer status, String error) {
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final String userAgent;

    public WebhookSender(Brand brand) {
        this.userAgent = brand.getName() + "-Webhooks/" + brand.getVersion();
    }

    public Result send(String url, String body, String secret, String event, Long deliveryId) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", userAgent)
                    .header("X-Quell-Event", event)
                    .header("X-Quell-Delivery", String.valueOf(deliveryId))
                    .header("X-Quell-Timestamp", timestamp)
                    .header("X-Quell-Signature", "sha256=" + sign(secret, timestamp + "." + body))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new Result(true, status, null);
            }
            String answer = response.body() == null ? "" : response.body().strip();
            return new Result(false, status, "The receiver answered " + status
                    + (answer.isEmpty() ? "" : ": " + (answer.length() > 200 ? answer.substring(0, 200) + "…" : answer)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, null, "Interrupted while sending.");
        } catch (IOException | IllegalArgumentException e) {
            return new Result(false, null, "Could not reach the receiver: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
        }
    }

    /** HMAC-SHA256 of the text with the secret, in hex. */
    public static String sign(String secret, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is part of every Java runtime", e);
        }
    }
}

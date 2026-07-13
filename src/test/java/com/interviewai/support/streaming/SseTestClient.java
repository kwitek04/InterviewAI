package com.interviewai.support.streaming;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SseTestClient {

    private final HttpClient httpClient;
    private final String baseUrl;

    public SseTestClient(int port) {
        this(HttpClient.newHttpClient(), "http://localhost:" + port);
    }

    SseTestClient(HttpClient httpClient, String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    public SseStream openStream(String eventsPath, String lastEventId) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + eventsPath))
                .header("Accept", "text/event-stream")
                .GET();
        if (lastEventId != null) {
            builder.header("Last-Event-ID", lastEventId);
        }

        HttpResponse<InputStream> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Expected SSE status 200 but got " + response.statusCode());
        }

        return new SseStream(response.body());
    }

    public final class SseStream implements AutoCloseable {

        private final InputStream body;
        private final BufferedReader reader;

        private SseStream(InputStream body) {
            this.body = body;
            this.reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        }

        public ReceivedSseEvent readNextEvent(Duration timeout) throws IOException {
            Instant deadline = Instant.now().plus(timeout);
            String id = null;
            String eventName = null;
            StringBuilder data = new StringBuilder();

            while (Instant.now().isBefore(deadline)) {
                if (!reader.ready()) {
                    sleepBriefly();
                    continue;
                }

                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("id:")) {
                    id = line.substring("id:".length()).trim();
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).trim());
                    continue;
                }
                if (line.isEmpty() && hasEventPayload(id, eventName, data)) {
                    return new ReceivedSseEvent(id, eventName, data.toString());
                }
            }

            throw new IOException("Timed out waiting for SSE event");
        }

        public List<ReceivedSseEvent> readEventsUntilTerminal(Duration timeoutPerEvent, int maxEvents)
                throws IOException {
            List<ReceivedSseEvent> events = new ArrayList<>();
            for (int index = 0; index < maxEvents; index++) {
                ReceivedSseEvent event = readNextEvent(timeoutPerEvent);
                events.add(event);
                if ("completed".equals(event.eventName()) || "error".equals(event.eventName())) {
                    return events;
                }
            }
            throw new IOException("Reached max SSE events without terminal event");
        }

        @Override
        public void close() throws IOException {
            reader.close();
            body.close();
        }

        private static boolean hasEventPayload(String id, String eventName, StringBuilder data) {
            return id != null || eventName != null || !data.isEmpty();
        }

        private static void sleepBriefly() {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for SSE data", exception);
            }
        }
    }

    public record ReceivedSseEvent(String id, String eventName, String data) {
    }
}

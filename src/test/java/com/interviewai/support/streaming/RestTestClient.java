package com.interviewai.support.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class RestTestClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public RestTestClient(int port, ObjectMapper objectMapper) {
        this(HttpClient.newHttpClient(), objectMapper, "http://localhost:" + port);
    }

    RestTestClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public JsonNode startSession() throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/v1/sessions"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 201) {
            throw new IOException("Expected 201 Created but got " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    public JsonNode fetchSessionJson(java.util.UUID sessionId) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/v1/sessions/" + sessionId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Expected 200 OK but got " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    public int submitAnswer(java.util.UUID sessionId, String answer) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/v1/sessions/" + sessionId + "/answers"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(java.util.Map.of("answer", answer)),
                                StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.statusCode();
    }
}

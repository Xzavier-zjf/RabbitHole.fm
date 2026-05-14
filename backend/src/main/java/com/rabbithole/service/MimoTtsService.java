package com.rabbithole.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MimoTtsService {

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;

    @Value("${mimo.api-base}")
    private String apiBase;

    @Value("${mimo.api-key}")
    private String apiKey;

    @Value("${mimo.default-model}")
    private String model;

    @Value("${mimo.default-voice}")
    private String voice;

    @Value("${mimo.default-format}")
    private String format;

    public byte[] synthesize(String styleHint, String text) throws IOException {
        String cacheKey = "mimo:tts:" + DigestUtils.md5Hex(model + voice + styleHint + text);
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return Base64.getDecoder().decode(cached);
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", java.util.List.of(
                        Map.of("role", "user", "content", styleHint),
                        Map.of("role", "assistant", "content", text)
                ),
                "audio", Map.of("format", format, "voice", voice)
        );

        Request req = new Request.Builder()
                .url(apiBase + "/v1/chat/completions")
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(mapper.writeValueAsBytes(body),
                        MediaType.parse("application/json")))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("MiMo TTS error: " + resp.code() + " - " +
                        resp.body().string());
            }
            JsonNode node = mapper.readTree(resp.body().byteStream());
            String b64 = node.path("choices").get(0).path("message").path("audio").path("data").asText();
            byte[] audio = Base64.getDecoder().decode(b64);
            redis.opsForValue().set(cacheKey, Base64.getEncoder().encodeToString(audio),
                    Duration.ofHours(24));
            return audio;
        }
    }
}

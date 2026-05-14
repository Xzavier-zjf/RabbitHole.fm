package com.rabbithole.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmDjService {

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;
    private final PromptTemplateLoader promptLoader;

    @Value("${mimo.api-base}")
    private String apiBase;

    @Value("${mimo.api-key}")
    private String apiKey;

    @Value("${mimo.chat-model:mimo-v2-flash}")
    private String chatModel;

    @Value("${mimo.llm-timeout-ms:3000}")
    private long llmTimeoutMs;

    public String generate(String scene, Map<String, String> ctx) throws IOException {
        String system = promptLoader.system();
        String userTpl = promptLoader.scene(scene);
        String userPrompt = replaceVars(userTpl, ctx);

        String cacheKey = "llm:dj:" + scene + ":" + DigestUtils.md5Hex(userPrompt);
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        Map<String, Object> body = Map.of(
                "model", chatModel,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.85,
                "max_tokens", 200
        );

        Request req = new Request.Builder()
                .url(apiBase + "/v1/chat/completions")
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(mapper.writeValueAsBytes(body),
                        MediaType.parse("application/json")))
                .build();

        OkHttpClient timeoutClient = http.newBuilder()
                .callTimeout(llmTimeoutMs, TimeUnit.MILLISECONDS)
                .build();

        try (Response resp = timeoutClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("LLM error " + resp.code() + ": " + resp.body().string());
            }
            JsonNode node = mapper.readTree(resp.body().byteStream());
            String text = node.path("choices").get(0).path("message").path("content").asText().trim();
            text = text.replaceAll("[*_`#>]", "");
            if (text.length() > 200) text = text.substring(0, 200);
            redis.opsForValue().set(cacheKey, text, Duration.ofMinutes(30));
            return text;
        }
    }

    private String replaceVars(String template, Map<String, String> vars) {
        String result = template;
        for (var entry : vars.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }
}

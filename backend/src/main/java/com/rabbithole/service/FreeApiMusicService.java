package com.rabbithole.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbithole.dto.LyricDTO;
import com.rabbithole.dto.SongDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FreeApiMusicService {

    private static final String SOURCE = "free-api";
    private static final String SOURCE_LABEL = "Free API";

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;

    @Value("${music.free-api.enabled:true}")
    private boolean enabled;

    @Value("${music.free-api.search-url:https://api.apiopen.top/searchMusic}")
    private String searchUrl;

    public List<SongDTO> search(String keywords, int limit) throws IOException {
        if (!enabled || keywords == null || keywords.isBlank()) {
            return List.of();
        }

        int safeLimit = Math.max(1, Math.min(limit, 30));
        String cacheKey = "free-api:search:" + keywords.trim().toLowerCase() + ":" + safeLimit;
        String cached = readCache(cacheKey);
        if (cached != null) {
            return mapper.readValue(cached, mapper.getTypeFactory()
                    .constructCollectionType(List.class, SongDTO.class));
        }

        String url = searchUrl + "?name=" + URLEncoder.encode(keywords.trim(), StandardCharsets.UTF_8);
        JsonNode node = doGet(url);
        List<SongDTO> list = parseResults(node).stream()
                .limit(safeLimit)
                .toList();
        writeCache(cacheKey, mapper.writeValueAsString(list), Duration.ofMinutes(10));
        return list;
    }

    public boolean checkApiStatus() {
        if (!enabled) {
            return false;
        }
        try {
            search("test", 1);
            return true;
        } catch (Exception e) {
            log.warn("Free API music source is unavailable", e);
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSearchUrl() {
        return searchUrl;
    }

    private List<SongDTO> parseResults(JsonNode node) {
        JsonNode result = node.path("result");
        if (!result.isArray()) {
            result = node.path("data");
        }
        if (!result.isArray()) {
            return List.of();
        }

        List<SongDTO> list = new ArrayList<>();
        for (JsonNode item : result) {
            SongDTO dto = toSong(item);
            if (dto != null) {
                list.add(dto);
            }
        }
        return list;
    }

    private SongDTO toSong(JsonNode item) {
        String title = firstText(item, "title", "name", "songName");
        String url = firstText(item, "url", "songUrl");
        if (title.isBlank() || url.isBlank()) {
            return null;
        }

        SongDTO dto = new SongDTO();
        dto.setId(resolveSongId(item, title, url));
        dto.setName(title);
        dto.setArtists(parseArtists(firstText(item, "author", "singer", "artist")));
        dto.setAlbum(firstText(item, "album", "type"));
        dto.setCoverUrl(firstText(item, "pic", "cover", "coverUrl"));
        dto.setSongUrl(url);
        dto.setSource(SOURCE);
        dto.setSourceLabel(SOURCE_LABEL);

        String lrc = firstText(item, "lrc", "lyric");
        if (!lrc.isBlank()) {
            LyricDTO lyric = new LyricDTO();
            lyric.setLrc(lrc);
            lyric.setTlyric("");
            dto.setLyric(lyric);
        }
        return dto;
    }

    private Long resolveSongId(JsonNode item, String title, String url) {
        JsonNode idNode = item.path("songid");
        if (idNode.isMissingNode() || idNode.isNull()) {
            idNode = item.path("id");
        }
        if (idNode.isIntegralNumber() && idNode.asLong() > 0) {
            return idNode.asLong();
        }
        String raw = idNode.asText("");
        if (!raw.isBlank()) {
            try {
                long parsed = Long.parseLong(raw);
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Fall back to a stable synthetic id.
            }
        }
        return Math.abs((long) (SOURCE + ":" + title + ":" + url).hashCode()) + 1_000_000_000L;
    }

    private List<String> parseArtists(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> artists = new ArrayList<>();
        for (String part : raw.split("[、/,|]")) {
            String name = part.trim();
            if (!name.isBlank()) {
                artists.add(name);
            }
        }
        return artists;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private JsonNode doGet(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "RabbitHole.fm/1.0")
                .get()
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String body = resp.body() != null ? resp.body().string() : "<empty body>";
                throw new IOException("Free API music source error " + resp.code() + " for " + url + ": " + body);
            }
            if (resp.body() == null) {
                throw new IOException("Free API music source returned empty body for " + url);
            }
            return mapper.readTree(resp.body().string());
        }
    }

    private String readCache(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Failed to read Redis cache for key {}", key, e);
            return null;
        }
    }

    private void writeCache(String key, String value, Duration ttl) {
        if (value == null) {
            return;
        }
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Failed to write Redis cache for key {}", key, e);
        }
    }
}

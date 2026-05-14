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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NeteaseMusicService {

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;

    @Value("${netease.api-base}")
    private String apiBase;

    @Value("${netease.cookie:}")
    private String cookie;

    public List<SongDTO> getPlaylistTracks(long playlistId, int limit, int offset) throws IOException {
        String cacheKey = "ne:tracks:" + playlistId + ":" + limit + ":" + offset;
        String cached = readCache(cacheKey);
        if (cached != null) {
            return mapper.readValue(cached, mapper.getTypeFactory()
                    .constructCollectionType(List.class, SongDTO.class));
        }

        String url = String.format("%s/playlist/track/all?id=%d&limit=%d&offset=%d",
                apiBase, playlistId, limit, offset);
        JsonNode node = doGet(url);
        List<SongDTO> list = new ArrayList<>();
        for (JsonNode s : node.path("songs")) {
            SongDTO dto = new SongDTO();
            dto.setId(s.path("id").asLong());
            dto.setName(s.path("name").asText());
            List<String> artists = new ArrayList<>();
            s.path("ar").forEach(a -> artists.add(a.path("name").asText()));
            dto.setArtists(artists);
            dto.setAlbum(s.path("al").path("name").asText());
            dto.setCoverUrl(s.path("al").path("picUrl").asText());
            dto.setDurationMs(s.path("dt").asLong());
            list.add(dto);
        }

        writeCache(cacheKey, mapper.writeValueAsString(list), Duration.ofMinutes(30));
        return list;
    }

    public String getSongUrl(long songId) throws IOException {
        String cacheKey = "ne:url:" + songId;
        String cached = readCache(cacheKey);
        if (cached != null) return cached;

        // Try multiple quality levels, starting from highest
        String[] levels = {"exhigh", "standard"};
        for (String level : levels) {
            String url = String.format("%s/song/url/v1?id=%d&level=%s", apiBase, songId, level);
            JsonNode node = doGet(url);
            JsonNode dataNode = node.path("data");
            if (dataNode.isArray() && !dataNode.isEmpty()) {
                String songUrl = dataNode.get(0).path("url").asText(null);
                if (songUrl != null && !songUrl.isEmpty()) {
                    writeCache(cacheKey, songUrl, Duration.ofMinutes(15));
                    return songUrl;
                }
            }
        }
        return null;
    }

    public LyricDTO getLyric(long songId) throws IOException {
        String cacheKey = "ne:lyric:" + songId;
        String cached = readCache(cacheKey);
        if (cached != null) {
            return mapper.readValue(cached, LyricDTO.class);
        }

        String url = String.format("%s/lyric?id=%d", apiBase, songId);
        JsonNode node = doGet(url);
        LyricDTO lyric = new LyricDTO();
        lyric.setLrc(node.path("lrc").path("lyric").asText(""));
        lyric.setTlyric(node.path("tlyric").path("lyric").asText(""));

        writeCache(cacheKey, mapper.writeValueAsString(lyric), Duration.ofHours(24));
        return lyric;
    }

    public SongDTO getSongDetail(long songId) throws IOException {
        String cacheKey = "ne:detail:" + songId;
        String cached = readCache(cacheKey);
        if (cached != null) {
            return mapper.readValue(cached, SongDTO.class);
        }

        String url = String.format("%s/song/detail?ids=%d", apiBase, songId);
        JsonNode node = doGet(url);
        JsonNode songs = node.path("songs");
        if (songs.isArray() && !songs.isEmpty()) {
            JsonNode s = songs.get(0);
            SongDTO dto = new SongDTO();
            dto.setId(s.path("id").asLong());
            dto.setName(s.path("name").asText());
            List<String> artists = new ArrayList<>();
            s.path("ar").forEach(a -> artists.add(a.path("name").asText()));
            dto.setArtists(artists);
            dto.setAlbum(s.path("al").path("name").asText());
            dto.setCoverUrl(s.path("al").path("picUrl").asText());
            dto.setDurationMs(s.path("dt").asLong());
            writeCache(cacheKey, mapper.writeValueAsString(dto), Duration.ofHours(24));
            return dto;
        }
        return null;
    }

    public List<SongDTO> search(String keywords, int limit) throws IOException {
        String url = String.format("%s/search?keywords=%s&limit=%d&type=1",
                apiBase, java.net.URLEncoder.encode(keywords, "UTF-8"), limit);
        JsonNode node = doGet(url);
        List<SongDTO> list = new ArrayList<>();
        for (JsonNode s : node.path("result").path("songs")) {
            SongDTO dto = new SongDTO();
            dto.setId(s.path("id").asLong());
            dto.setName(s.path("name").asText());
            List<String> artists = new ArrayList<>();
            s.path("ar").forEach(a -> artists.add(a.path("name").asText()));
            dto.setArtists(artists);
            dto.setAlbum(s.path("al").path("name").asText());
            dto.setCoverUrl(s.path("al").path("picUrl").asText());
            dto.setDurationMs(s.path("dt").asLong());
            list.add(dto);
        }
        return list;
    }

    public boolean checkApiStatus() {
        try {
            String url = apiBase + "/search?keywords=test&limit=1&type=1";
            doGet(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getApiBase() {
        return apiBase;
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

    private JsonNode doGet(String url) throws IOException {
        Request.Builder rb = new Request.Builder().url(url).get();
        if (cookie != null && !cookie.isEmpty()) {
            rb.header("Cookie", cookie);
        }
        try (Response resp = http.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                String body = resp.body() != null ? resp.body().string() : "<empty body>";
                if (body == null || body.isBlank()) {
                    body = "<empty body>";
                }
                throw new IOException("Netease API error " + resp.code() + " for " + url + ": " + body);
            }
            if (resp.body() == null) {
                throw new IOException("Netease API returned empty body for " + url);
            }
            String body = resp.body().string();
            if (body == null || body.isBlank()) {
                throw new IOException("Netease API returned empty body for " + url);
            }
            return mapper.readTree(body);
        }
    }
}

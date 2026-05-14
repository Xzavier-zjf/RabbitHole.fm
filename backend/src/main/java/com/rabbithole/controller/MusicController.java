package com.rabbithole.controller;

import com.rabbithole.dto.SongDTO;
import com.rabbithole.service.NeteaseMusicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
@Slf4j
public class MusicController {

    private final NeteaseMusicService musicService;
    private final OkHttpClient http;

    @GetMapping("/search")
    public List<SongDTO> search(@RequestParam String keywords,
                                @RequestParam(defaultValue = "30") int limit) throws IOException {
        return musicService.search(keywords, limit);
    }

    @GetMapping("/song/{id}")
    public SongDTO getSongDetail(@PathVariable long id) throws IOException {
        return musicService.getSongDetail(id);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean alive = musicService.checkApiStatus();
        return Map.of("apiAlive", alive, "apiUrl", musicService.getApiBase());
    }

    /**
     * Proxy Netease cover images to bypass Referer check.
     * Usage: /api/music/cover?url=https://p3.music.126.net/xxx.jpg
     */
    @GetMapping("/cover")
    public ResponseEntity<byte[]> cover(@RequestParam String url) {
        CoverPayload payload;
        try {
            payload = fetchCoverBytes(url);
        } catch (Exception e) {
            log.warn("Failed to proxy cover for url {}", url, e);
            payload = null;
        }
        if (payload == null || payload.bytes() == null || payload.bytes().length == 0) {
            payload = placeholderPayload();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, payload.contentType())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(payload.bytes());
    }

    private CoverPayload fetchCoverBytes(String url) throws IOException {
        for (String candidate : coverCandidates(url)) {
            if (!isHttpUrl(candidate)) {
                continue;
            }
            Request req = new Request.Builder()
                    .url(candidate)
                    .header("Referer", "https://music.163.com/")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .get()
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    String contentType = resp.header(HttpHeaders.CONTENT_TYPE);
                    if (contentType != null && !contentType.isBlank() && !isImageContentType(contentType)) {
                        continue;
                    }
                    byte[] bytes = resp.body().bytes();
                    if (bytes.length > 0) {
                        if (contentType == null || contentType.isBlank()) {
                            contentType = "image/jpeg";
                        }
                        return new CoverPayload(bytes, contentType);
                    }
                }
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }
        return null;
    }

    private List<String> coverCandidates(String url) {
        if (url == null || url.isBlank()) {
            return List.of();
        }
        String alt = url.startsWith("https://")
                ? url.replaceFirst("^https://", "http://")
                : url.startsWith("http://")
                ? url.replaceFirst("^http://", "https://")
                : url;
        return alt.equals(url) ? List.of(url) : List.of(url, alt);
    }

    private boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    private boolean isImageContentType(String contentType) {
        return contentType.toLowerCase().startsWith("image/");
    }

    private CoverPayload placeholderPayload() {
        return new CoverPayload(placeholderCover(), "image/svg+xml; charset=utf-8");
    }

    private byte[] placeholderCover() {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">
                  <defs>
                    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%" stop-color="#1f2233"/>
                      <stop offset="100%" stop-color="#0f0f1a"/>
                    </linearGradient>
                  </defs>
                  <rect width="256" height="256" rx="28" fill="url(#g)"/>
                  <circle cx="128" cy="128" r="52" fill="#ffffff10"/>
                  <path d="M104 88v80l56-20V68z" fill="#ffffff80"/>
                  <path d="M104 88h56v20h-56z" fill="#ffffff50"/>
                </svg>
        """;
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    private record CoverPayload(byte[] bytes, String contentType) {}
}

package com.rabbithole.service;

import com.rabbithole.dto.SongDTO;
import com.rabbithole.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MusicSearchAggregatorService {

    private final NeteaseMusicService neteaseMusicService;
    private final FreeApiMusicService freeApiMusicService;

    public List<SongDTO> search(String keywords, int limit) throws IOException {
        return search(keywords, limit, "all");
    }

    public List<SongDTO> search(String keywords, int limit, String source) throws IOException {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String normalizedSource = normalizeSource(source);
        if ("netease".equals(normalizedSource)) {
            return neteaseMusicService.search(keywords, safeLimit);
        }
        if ("free-api".equals(normalizedSource)) {
            return freeApiMusicService.search(keywords, safeLimit);
        }

        int upstreamLimit = Math.max(safeLimit, Math.min(safeLimit * 2, 50));
        Map<String, SongDTO> merged = new LinkedHashMap<>();
        IOException primaryFailure = null;

        try {
            for (SongDTO song : neteaseMusicService.search(keywords, upstreamLimit)) {
                putUnique(merged, song);
            }
        } catch (IOException e) {
            primaryFailure = e;
            log.warn("Netease search failed for keywords={}", keywords, e);
        }

        try {
            for (SongDTO song : freeApiMusicService.search(keywords, upstreamLimit)) {
                putUnique(merged, song);
            }
        } catch (Exception e) {
            log.warn("Free API search failed for keywords={}: {}", keywords, e.getMessage());
        }

        if (merged.isEmpty() && primaryFailure != null) {
            throw primaryFailure;
        }
        return new ArrayList<>(merged.values()).stream()
                .limit(safeLimit)
                .toList();
    }

    private String normalizeSource(String source) {
        String normalized = source == null || source.isBlank()
                ? "all"
                : source.trim().toLowerCase();
        if ("all".equals(normalized) || "netease".equals(normalized) || "free-api".equals(normalized)) {
            return normalized;
        }
        throw new BizException("不支持的音乐源: " + source);
    }

    public Map<String, Object> status() {
        boolean neteaseAlive = neteaseMusicService.checkApiStatus();
        boolean freeApiAlive = freeApiMusicService.checkApiStatus();
        return Map.of(
                "apiAlive", neteaseAlive || freeApiAlive,
                "apiUrl", neteaseMusicService.getApiBase(),
                "sources", List.of(
                        Map.of(
                                "key", "netease",
                                "label", "网易云",
                                "primary", true,
                                "alive", neteaseAlive,
                                "url", neteaseMusicService.getApiBase()
                        ),
                        Map.of(
                                "key", "free-api",
                                "label", "Free API",
                                "primary", false,
                                "enabled", freeApiMusicService.isEnabled(),
                                "alive", freeApiAlive,
                                "url", freeApiMusicService.getSearchUrl()
                        )
                )
        );
    }

    private void putUnique(Map<String, SongDTO> merged, SongDTO song) {
        if (song == null || song.getName() == null || song.getName().isBlank()) {
            return;
        }
        String key = normalize(song.getName()) + "|" + normalize(String.join("/", safeArtists(song)));
        merged.putIfAbsent(key, song);
    }

    private List<String> safeArtists(SongDTO song) {
        return song.getArtists() != null ? song.getArtists() : List.of();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[《》<>\\[\\]（）()]", "");
    }
}

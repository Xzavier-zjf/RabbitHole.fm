package com.rabbithole.service;

import com.rabbithole.dto.RadioItemDTO;
import com.rabbithole.dto.SongDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbithole.mapper.SongRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RadioPlaylistService {

    private final NeteaseMusicService musicService;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final SongRequestMapper requestMapper;

    @Value("${radio.default-channel}")
    private long defaultChannel;

    /**
     * Load channel by fetching playlist metadata only (fast).
     * URLs are fetched lazily by the frontend when each song starts playing.
     */
    public List<RadioItemDTO> loadChannel(Long playlistId) throws IOException {
        if (playlistId == null || playlistId == 0) {
            playlistId = defaultChannel;
        }
        List<SongDTO> songs;
        try {
            songs = musicService.getPlaylistTracks(playlistId, 50, 0);
        } catch (IOException e) {
            throw new IOException("频道加载失败，请确认网易云 API 服务已启动且 3000 端口可访问", e);
        }

        List<RadioItemDTO> queue = new ArrayList<>();

        for (int i = 0; i < songs.size(); i++) {
            SongDTO song = songs.get(i);
            RadioItemDTO songItem = RadioItemDTO.song(song, null, null);
            queue.add(songItem);

            // Insert DJ interlude every 2 songs
            if (i < songs.size() - 1 && (i + 1) % 2 == 0) {
                SongDTO prev = songs.get(i);
                SongDTO next = songs.get(i + 1);
                RadioItemDTO djItem = RadioItemDTO.dj(
                        "/api/radio/dj?prevId=" + prev.getId() + "&nextId=" + next.getId(),
                        prev.getName(), next.getName());
                queue.add(djItem);
            }
        }

        // Cache queue in Redis
        String queueKey = "radio:queue:" + playlistId;
        try {
            redis.delete(queueKey);
            if (!queue.isEmpty()) {
                List<String> jsonList = queue.stream()
                        .map(item -> {
                            try { return mapper.writeValueAsString(item); }
                            catch (Exception e) { return null; }
                        })
                        .filter(s -> s != null)
                        .toList();
                if (!jsonList.isEmpty()) {
                    redis.opsForList().rightPushAll(queueKey, jsonList);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to cache radio queue for channel {}", playlistId, e);
        }

        log.info("Loaded {} items for channel {}", queue.size(), playlistId);
        return queue;
    }

    public RadioItemDTO getNext(Long channelId) throws IOException {
        if (channelId == null || channelId == 0) {
            channelId = defaultChannel;
        }
        String queueKey = "radio:queue:" + channelId;
        String head;
        try {
            head = redis.opsForList().leftPop(queueKey);
            if (head == null) {
                refill(channelId);
                head = redis.opsForList().leftPop(queueKey);
            }
        } catch (Exception e) {
            log.warn("Redis queue pop failed for channel {}, falling back to direct load", channelId, e);
            List<RadioItemDTO> fallbackQueue = loadChannel(channelId);
            return fallbackQueue.isEmpty() ? null : fallbackQueue.get(0);
        }
        if (head == null) return null;
        RadioItemDTO item = mapper.readValue(head, RadioItemDTO.class);
        markRequestPlayed(item);
        return item;
    }

    public void refill(Long channelId) throws IOException {
        loadChannel(channelId);
    }

    private void markRequestPlayed(RadioItemDTO item) {
        if (item == null || item.getRequestId() == null || !"song".equals(item.getType())) {
            return;
        }
        try {
            var request = requestMapper.selectById(item.getRequestId());
            if (request == null || request.getStatus() == 1) {
                return;
            }
            request.setStatus(1);
            request.setPlayedAt(LocalDateTime.now());
            requestMapper.updateById(request);
        } catch (Exception e) {
            log.warn("Failed to mark request {} as played", item.getRequestId(), e);
        }
    }
}

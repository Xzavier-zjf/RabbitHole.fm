package com.rabbithole.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbithole.dto.RadioItemDTO;
import com.rabbithole.dto.SongDTO;
import com.rabbithole.entity.SongRequest;
import com.rabbithole.entity.User;
import com.rabbithole.exception.BizException;
import com.rabbithole.mapper.SongRequestMapper;
import com.rabbithole.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SongRequestService {

    private static final Duration IDEMPOTENT_WINDOW = Duration.ofMinutes(2);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final SongRequestMapper requestMapper;
    private final UserMapper userMapper;
    private final NeteaseMusicService musicService;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public record SubmitResult(Long requestId, RadioItemDTO djItem, RadioItemDTO songItem) {}

    public record MyRequestsPage(List<SongRequest> items, long total, int page, int size, boolean hasMore) {}

    public SubmitResult submit(Long userId, String username,
                               Long channelId, Long songId, String message) throws IOException {
        String normalizedMessage = normalizeMessage(message);
        User u = userMapper.selectById(userId);
        String displayName = displayName(u, username);

        SongRequest existing = findRecentPendingDuplicate(userId, channelId, songId, normalizedMessage);
        if (existing != null) {
            log.info("song_request_duplicate userId={} channelId={} songId={} requestId={}",
                    userId, channelId, songId, existing.getId());
            return rebuildSubmitResult(existing, displayName);
        }

        // 1. Quota check
        String quotaKey = "radio:request:quota:" + userId + ":" + LocalDate.now().format(DAY_FMT);
        Long used = incrementQuotaSafely(quotaKey);
        if (u != null && used != null && used > u.getRequestQuota()) {
            throw new BizException("今日点歌次数已用完");
        }

        // 2. Cooldown check
        String cdKey = "radio:request:cooldown:" + songId;
        if (hasKeySafely(cdKey)) {
            throw new BizException("这首歌刚被点过，30 分钟后再试");
        }

        // 3. Fetch song metadata
        SongDTO song = musicService.getSongDetail(songId);
        if (song == null) throw new BizException("歌曲不存在");

        // 4. Persist
        SongRequest sr = new SongRequest();
        sr.setUserId(userId);
        sr.setUsername(username);
        sr.setChannelId(channelId);
        sr.setSongId(songId);
        sr.setSongName(song.getName());
        sr.setArtists(String.join("、", song.getArtists()));
        sr.setMessage(normalizedMessage);
        sr.setStatus(0);
        requestMapper.insert(sr);

        // 5. Enqueue DJ intro + song to position 3
        String queueKey = "radio:queue:" + channelId;

        RadioItemDTO djIntro = RadioItemDTO.dj(
                "/api/radio/dj?nextId=" + songId
                        + "&requester=" + java.net.URLEncoder.encode(displayName, "UTF-8")
                        + "&message=" + java.net.URLEncoder.encode(normalizedMessage, "UTF-8"),
                "点播", song.getName());
        djIntro.setRequestId(sr.getId());
        djIntro.setRequester(displayName);
        djIntro.setMessage(normalizedMessage);
        insertAt(queueKey, 3, mapper.writeValueAsString(djIntro));

        RadioItemDTO songItem = RadioItemDTO.song(song, null, null);
        songItem.setRequestId(sr.getId());
        songItem.setRequester(displayName);
        songItem.setMessage(normalizedMessage);
        insertAt(queueKey, 4, mapper.writeValueAsString(songItem));

        // 6. Set cooldown (30 min)
        setCooldownSafely(cdKey, Duration.ofMinutes(30));
        log.info("song_request_submitted requestId={} userId={} channelId={} songId={} requester={} messageLength={}",
                sr.getId(), userId, channelId, songId, displayName, normalizedMessage.length());
        return new SubmitResult(sr.getId(), djIntro, songItem);
    }

    private void insertAt(String key, long index, String value) {
        try {
            List<String> all = redis.opsForList().range(key, 0, -1);
            if (all == null) all = new ArrayList<>();
            all.add((int) Math.min(index, all.size()), value);
            redis.delete(key);
            if (!all.isEmpty()) redis.opsForList().rightPushAll(key, all);
        } catch (Exception e) {
            log.warn("Failed to insert request item into queue {} at {}", key, index, e);
        }
    }

    public List<RadioItemDTO> getQueue(Long channelId) throws IOException {
        Map<String, RadioItemDTO> merged = new LinkedHashMap<>();
        for (SongRequest request : listPendingRequests(channelId, 12)) {
            RadioItemDTO item = toRequestQueueItem(request);
            merged.putIfAbsent(queueItemKey(item), item);
        }

        return new ArrayList<>(merged.values());
    }

    public void cancel(Long requestId, Long userId) {
        SongRequest sr = requestMapper.selectById(requestId);
        if (sr == null || !sr.getUserId().equals(userId)) {
            throw new BizException("无权取消该点歌");
        }
        if (sr.getStatus() != 0) {
            throw new BizException("点歌已播出，无法取消");
        }
        sr.setStatus(2);
        sr.setPlayedAt(LocalDateTime.now());
        requestMapper.updateById(sr);
        removeRequestFromQueue(sr.getChannelId(), sr.getId());
        log.info("song_request_cancelled requestId={} userId={} channelId={} songId={}",
                requestId, userId, sr.getChannelId(), sr.getSongId());
    }

    public void markPlayed(Long requestId) {
        if (requestId == null) {
            return;
        }
        SongRequest sr = requestMapper.selectById(requestId);
        if (sr == null || sr.getStatus() == 1) {
            return;
        }
        sr.setStatus(1);
        sr.setPlayedAt(LocalDateTime.now());
        requestMapper.updateById(sr);
    }

    public List<SongRequest> getMyRequests(Long userId) {
        return getMyRequests(userId, 20);
    }

    public List<SongRequest> getMyRequests(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return requestMapper.selectList(
                new LambdaQueryWrapper<SongRequest>()
                        .eq(SongRequest::getUserId, userId)
                        .orderByDesc(SongRequest::getCreatedAt)
                        .last("limit " + safeLimit));
    }

    public MyRequestsPage getMyRequestsPage(Long userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        Page<SongRequest> pager = new Page<>(safePage, safeSize);
        Page<SongRequest> result = requestMapper.selectPage(
                pager,
                new LambdaQueryWrapper<SongRequest>()
                        .eq(SongRequest::getUserId, userId)
                        .orderByDesc(SongRequest::getCreatedAt));
        boolean hasMore = safePage * (long) safeSize < result.getTotal();
        return new MyRequestsPage(result.getRecords(), result.getTotal(), safePage, safeSize, hasMore);
    }

    private Long incrementQuotaSafely(String quotaKey) {
        try {
            Long used = redis.opsForValue().increment(quotaKey);
            if (used != null && used == 1L) {
                redis.expire(quotaKey, Duration.ofDays(1));
            }
            return used;
        } catch (Exception e) {
            log.warn("Failed to increment request quota key {}", quotaKey, e);
            return null;
        }
    }

    private boolean hasKeySafely(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (Exception e) {
            log.warn("Failed to check Redis key {}", key, e);
            return false;
        }
    }

    private void setCooldownSafely(String key, Duration ttl) {
        try {
            redis.opsForValue().set(key, "1", ttl);
        } catch (Exception e) {
            log.warn("Failed to set cooldown key {}", key, e);
        }
    }

    private List<SongRequest> listPendingRequests(Long channelId, int limit) {
        return requestMapper.selectList(
                new LambdaQueryWrapper<SongRequest>()
                        .eq(SongRequest::getChannelId, channelId)
                        .eq(SongRequest::getStatus, 0)
                        .orderByDesc(SongRequest::getCreatedAt)
                        .last("limit " + limit));
    }

    private RadioItemDTO toRequestQueueItem(SongRequest request) throws IOException {
        SongDTO song = musicService.getSongDetail(request.getSongId());
        User user = request.getUserId() != null ? userMapper.selectById(request.getUserId()) : null;
        String requesterName = displayName(user, request.getUsername());
        RadioItemDTO item;
        if (song != null) {
            item = RadioItemDTO.song(song, null, null);
        } else {
            item = new RadioItemDTO();
            item.setType("song");
            item.setSongId(request.getSongId());
            item.setName(request.getSongName());
        }
        item.setRequestId(request.getId());
        item.setRequester(requesterName);
        item.setMessage(request.getMessage());
        if ((item.getArtists() == null || item.getArtists().isEmpty()) && request.getArtists() != null && !request.getArtists().isBlank()) {
            item.setArtists(List.of(request.getArtists().split("[、/]\\s*")));
        }
        return item;
    }

    private SongRequest findRecentPendingDuplicate(Long userId, Long channelId, Long songId, String message) {
        LocalDateTime since = LocalDateTime.now().minus(IDEMPOTENT_WINDOW);
        return requestMapper.selectOne(
                new LambdaQueryWrapper<SongRequest>()
                        .eq(SongRequest::getUserId, userId)
                        .eq(SongRequest::getChannelId, channelId)
                        .eq(SongRequest::getSongId, songId)
                        .eq(SongRequest::getStatus, 0)
                        .eq(SongRequest::getMessage, message)
                        .ge(SongRequest::getCreatedAt, since)
                        .last("limit 1"));
    }

    private SubmitResult rebuildSubmitResult(SongRequest existing, String displayName) throws IOException {
        SongDTO song = musicService.getSongDetail(existing.getSongId());
        if (song == null) {
            throw new BizException("歌曲不存在");
        }
        RadioItemDTO djIntro = RadioItemDTO.dj(
                "/api/radio/dj?nextId=" + existing.getSongId()
                        + "&requester=" + java.net.URLEncoder.encode(displayName, "UTF-8")
                        + "&message=" + java.net.URLEncoder.encode(existing.getMessage() != null ? existing.getMessage() : "", "UTF-8"),
                "点播", song.getName());
        djIntro.setRequestId(existing.getId());
        djIntro.setRequester(displayName);
        djIntro.setMessage(existing.getMessage());

        RadioItemDTO songItem = RadioItemDTO.song(song, null, null);
        songItem.setRequestId(existing.getId());
        songItem.setRequester(displayName);
        songItem.setMessage(existing.getMessage());
        return new SubmitResult(existing.getId(), djIntro, songItem);
    }

    private String displayName(User user, String fallbackUsername) {
        if (user != null && user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname().trim();
        }
        return fallbackUsername != null && !fallbackUsername.isBlank() ? fallbackUsername.trim() : "Rabbit";
    }

    private String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.trim().replace("\r\n", "\n");
    }

    private String queueItemKey(RadioItemDTO item) {
        return String.join("|",
                item.getType() != null ? item.getType() : "song",
                String.valueOf(item.getRequestId() != null ? item.getRequestId() : 0L),
                String.valueOf(item.getSongId() != null ? item.getSongId() : 0L),
                item.getRequester() != null ? item.getRequester() : "",
                item.getMessage() != null ? item.getMessage() : "",
                item.getDjUrl() != null ? item.getDjUrl() : "");
    }

    private void removeRequestFromQueue(Long channelId, Long requestId) {
        if (channelId == null || requestId == null) {
            return;
        }
        String key = "radio:queue:" + channelId;
        try {
            List<String> raw = redis.opsForList().range(key, 0, -1);
            if (raw == null || raw.isEmpty()) {
                return;
            }
            List<String> filtered = new ArrayList<>();
            for (String json : raw) {
                RadioItemDTO item = mapper.readValue(json, RadioItemDTO.class);
                if (!requestId.equals(item.getRequestId())) {
                    filtered.add(json);
                }
            }
            redis.delete(key);
            if (!filtered.isEmpty()) {
                redis.opsForList().rightPushAll(key, filtered);
            }
        } catch (Exception e) {
            log.warn("Failed to remove request {} from queue of channel {}", requestId, channelId, e);
        }
    }
}

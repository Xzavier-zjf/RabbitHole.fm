package com.rabbithole.controller;

import com.rabbithole.dto.RadioItemDTO;
import com.rabbithole.entity.SongRequest;
import com.rabbithole.service.SongRequestService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/request")
@RequiredArgsConstructor
public class RequestController {

    private final SongRequestService requestService;

    @PostMapping
    public Map<String, Object> submit(@RequestBody Map<String, Object> body,
                                      HttpServletRequest request) throws IOException {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Map.of("code", 1, "msg", "请先登录");
        }
        String username = (String) request.getAttribute("username");

        Long channelId = body.get("channelId") != null
                ? ((Number) body.get("channelId")).longValue() : null;
        Long songId = body.get("songId") != null
                ? ((Number) body.get("songId")).longValue() : null;
        if (channelId == null) channelId = 32953014L;
        if (songId == null) throw new IllegalArgumentException("songId required");

        String message = (String) body.getOrDefault("message", "");
        var result = requestService.submit(userId, username, channelId, songId, message);
        return Map.of("code", 0, "msg", "点歌成功",
                "id", result.requestId(),
                "djItem", result.djItem(),
                "songItem", result.songItem());
    }

    @GetMapping("/queue/{channelId}")
    public List<RadioItemDTO> queue(@PathVariable Long channelId) throws IOException {
        return requestService.getQueue(channelId);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> cancel(@PathVariable Long id,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Map.of("code", 1, "msg", "请先登录");
        }
        requestService.cancel(id, userId);
        return Map.of("code", 0, "msg", "已取消");
    }

    @DeleteMapping({"", "/"})
    public Map<String, Object> cancelWithoutId() {
        return Map.of("code", 400, "msg", "缺少点歌 ID");
    }

    @GetMapping("/my")
    public Object myRequests(@RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size,
                             @RequestParam(defaultValue = "20") int limit,
                             HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return page != null || size != null
                    ? Map.of("items", List.of(), "total", 0, "page", 1, "size", size != null ? size : 10, "hasMore", false)
                    : List.of();
        }
        if (page != null || size != null) {
            int safePage = page != null ? page : 1;
            int safeSize = size != null ? size : 10;
            return requestService.getMyRequestsPage(userId, safePage, safeSize);
        }
        return requestService.getMyRequests(userId, limit);
    }
}

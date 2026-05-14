package com.rabbithole.controller;

import com.rabbithole.entity.PlayHistory;
import com.rabbithole.entity.User;
import com.rabbithole.entity.UserChannel;
import com.rabbithole.entity.UserFavorite;
import com.rabbithole.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        Long id = userService.register(username, password);
        return Map.of("code", 0, "msg", "注册成功", "userId", id);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String token = userService.login(username, password);
        return Map.of("code", 0, "msg", "登录成功", "token", token);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            userService.logout(token);
        }
        return Map.of("code", 0, "msg", "已登出");
    }

    @GetMapping("/me")
    public User me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return null;
        }
        return userService.getProfile(userId);
    }

    @PostMapping("/favorite/{songId}")
    public Map<String, Object> addFavorite(@PathVariable Long songId,
                                           @RequestBody(required = false) Map<String, String> body,
                                           HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String songName = body != null ? body.getOrDefault("songName", "") : "";
        String artists = body != null ? body.getOrDefault("artists", "") : "";
        String coverUrl = body != null ? body.getOrDefault("coverUrl", "") : "";
        userService.addFavorite(userId, songId, songName, artists, coverUrl);
        return Map.of("code", 0, "msg", "已收藏");
    }

    @DeleteMapping("/favorite/{songId}")
    public Map<String, Object> removeFavorite(@PathVariable Long songId,
                                              HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.removeFavorite(userId, songId);
        return Map.of("code", 0, "msg", "已取消收藏");
    }

    @GetMapping("/favorites")
    public List<UserFavorite> favorites(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return userService.getFavorites(userId);
    }

    @PostMapping("/history/record")
    public Map<String, Object> recordPlay(@RequestBody Map<String, Object> body,
                                          HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Map.of("code", 0, "msg", "未登录，不记录");
        }
        Long songId = body.get("songId") != null ? ((Number) body.get("songId")).longValue() : 0L;
        String songName = (String) body.getOrDefault("songName", "");
        String artists = (String) body.getOrDefault("artists", "");
        Long channelId = body.get("channelId") != null ? ((Number) body.get("channelId")).longValue() : null;
        userService.recordPlay(userId, songId, songName, artists, channelId);
        return Map.of("code", 0, "msg", "已记录");
    }

    @GetMapping("/history")
    public List<PlayHistory> history(@RequestParam(defaultValue = "7") int days,
                                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return userService.getHistory(userId, days);
    }

    @PostMapping("/channel/favorite")
    public Map<String, Object> addChannelFavorite(@RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long channelId = ((Number) body.get("channelId")).longValue();
        String channelName = (String) body.getOrDefault("channelName", "");
        userService.addChannelFavorite(userId, channelId, channelName);
        return Map.of("code", 0, "msg", "已收藏频道");
    }

    @DeleteMapping("/channel/favorite/{channelId}")
    public Map<String, Object> removeChannelFavorite(@PathVariable Long channelId,
                                                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.removeChannelFavorite(userId, channelId);
        return Map.of("code", 0, "msg", "已取消收藏");
    }

    @GetMapping("/channel/favorites")
    public List<UserChannel> channelFavorites(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return userService.getChannelFavorites(userId);
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, String> body,
                                              HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Map.of("code", 1, "msg", "请先登录");
        }
        String nickname = body.get("nickname");
        String avatarUrl = body.get("avatarUrl");
        User updated = userService.updateProfile(userId, nickname, avatarUrl);
        return Map.of("code", 0, "msg", "更新成功", "user", updated);
    }

    @PostMapping("/avatar")
    public Map<String, Object> uploadAvatar(@RequestParam("file") MultipartFile file,
                                             HttpServletRequest request) throws IOException {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Map.of("code", 1, "msg", "请先登录");
        }
        if (file.isEmpty()) {
            return Map.of("code", 1, "msg", "文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf("."));
        }
        // Allow only image types
        String lower = ext.toLowerCase();
        if (!lower.equals(".png") && !lower.equals(".jpg") && !lower.equals(".jpeg") && !lower.equals(".gif") && !lower.equals(".webp")) {
            return Map.of("code", 1, "msg", "仅支持 png/jpg/jpeg/gif/webp 格式");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            return Map.of("code", 1, "msg", "文件不能超过 2MB");
        }

        // Save to user home so files survive rebuilds
        Path uploadDir = Paths.get(System.getProperty("user.home"), ".rabbithole", "avatars");
        Files.createDirectories(uploadDir);

        String filename = "avatar_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dest = uploadDir.resolve(filename);
        file.transferTo(dest.toFile());

        String avatarUrl = "/avatars/" + filename;
        userService.updateProfile(userId, null, avatarUrl);
        return Map.of("code", 0, "msg", "上传成功", "avatarUrl", avatarUrl);
    }
}

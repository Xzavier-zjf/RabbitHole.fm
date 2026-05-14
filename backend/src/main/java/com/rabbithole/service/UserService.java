package com.rabbithole.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbithole.entity.PlayHistory;
import com.rabbithole.entity.User;
import com.rabbithole.entity.UserChannel;
import com.rabbithole.entity.UserFavorite;
import com.rabbithole.exception.BizException;
import com.rabbithole.mapper.PlayHistoryMapper;
import com.rabbithole.mapper.UserChannelMapper;
import com.rabbithole.mapper.UserFavoriteMapper;
import com.rabbithole.mapper.UserMapper;
import com.rabbithole.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserFavoriteMapper favoriteMapper;
    private final PlayHistoryMapper playHistoryMapper;
    private final UserChannelMapper channelMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder encoder;

    public Long register(String username, String password) {
        if (userMapper.selectByUsername(username) != null) {
            throw new BizException("用户名已存在");
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setNickname(username);
        u.setRequestQuota(10);
        userMapper.insert(u);
        return u.getId();
    }

    public String login(String username, String password) {
        User u = userMapper.selectByUsername(username);
        if (u == null || !encoder.matches(password, u.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }
        return jwtUtil.issue(u.getId(), u.getUsername());
    }

    public void logout(String token) {
        // Blacklist the token in Redis
        jwtUtil.blacklist(token);
    }

    public User getProfile(Long userId) {
        return userMapper.selectById(userId);
    }

    public void addFavorite(Long userId, Long songId, String songName, String artists, String coverUrl) {
        UserFavorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getSongId, songId));
        if (existing != null) return;

        UserFavorite fav = new UserFavorite();
        fav.setUserId(userId);
        fav.setSongId(songId);
        fav.setSongName(songName);
        fav.setArtists(artists);
        fav.setCoverUrl(coverUrl);
        favoriteMapper.insert(fav);
    }

    public void removeFavorite(Long userId, Long songId) {
        favoriteMapper.delete(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getSongId, songId));
    }

    public List<UserFavorite> getFavorites(Long userId) {
        return favoriteMapper.selectList(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreatedAt));
    }

    public void recordPlay(Long userId, Long songId, String songName, String artists, Long channelId) {
        PlayHistory ph = new PlayHistory();
        ph.setUserId(userId);
        ph.setSongId(songId);
        ph.setSongName(songName);
        ph.setArtists(artists);
        ph.setChannelId(channelId);
        ph.setPlayedAt(LocalDateTime.now());
        playHistoryMapper.insert(ph);
    }

    public List<PlayHistory> getHistory(Long userId, int days) {
        if (userId == null) {
            return List.of();
        }
        int safeDays = Math.max(1, Math.min(days, 30));
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);
        return playHistoryMapper.selectList(
                new LambdaQueryWrapper<PlayHistory>()
                        .eq(PlayHistory::getUserId, userId)
                        .ge(PlayHistory::getPlayedAt, since)
                        .orderByDesc(PlayHistory::getPlayedAt));
    }

    public void addChannelFavorite(Long userId, Long channelId, String channelName) {
        UserChannel existing = channelMapper.selectOne(
                new LambdaQueryWrapper<UserChannel>()
                        .eq(UserChannel::getUserId, userId)
                        .eq(UserChannel::getChannelId, channelId));
        if (existing != null) return;
        UserChannel uc = new UserChannel();
        uc.setUserId(userId);
        uc.setChannelId(channelId);
        uc.setChannelName(channelName);
        channelMapper.insert(uc);
    }

    public void removeChannelFavorite(Long userId, Long channelId) {
        channelMapper.delete(
                new LambdaQueryWrapper<UserChannel>()
                        .eq(UserChannel::getUserId, userId)
                        .eq(UserChannel::getChannelId, channelId));
    }

    public List<UserChannel> getChannelFavorites(Long userId) {
        return channelMapper.selectList(
                new LambdaQueryWrapper<UserChannel>()
                        .eq(UserChannel::getUserId, userId));
    }

    public User updateProfile(Long userId, String nickname, String avatarUrl) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");
        if (nickname != null && !nickname.isBlank()) {
            u.setNickname(nickname.trim());
        }
        if (avatarUrl != null) {
            u.setAvatarUrl(avatarUrl.trim());
        }
        userMapper.updateById(u);
        return u;
    }
}

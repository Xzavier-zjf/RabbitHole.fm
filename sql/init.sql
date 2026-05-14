-- ============================================
-- RabbitHole.fm 数据库初始化脚本
-- 使用方式: mysql -u root -p < init.sql
-- ============================================

CREATE DATABASE IF NOT EXISTS `RabbitHole.fm` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `RabbitHole.fm`;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(32)  UNIQUE NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    nickname      VARCHAR(32),
    avatar_url    VARCHAR(255),
    email         VARCHAR(64),
    request_quota INT DEFAULT 10,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 用户喜欢的歌
CREATE TABLE IF NOT EXISTS t_user_favorite (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    song_id    BIGINT NOT NULL,
    song_name  VARCHAR(128),
    artists    VARCHAR(255),
    cover_url  VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_song (user_id, song_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏歌曲';

-- 播放历史
CREATE TABLE IF NOT EXISTS t_play_history (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    song_id    BIGINT NOT NULL,
    song_name  VARCHAR(128),
    artists    VARCHAR(255),
    channel_id BIGINT,
    played_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_time (user_id, played_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='播放历史';

-- 点歌记录 (v1.1 预留)
CREATE TABLE IF NOT EXISTS t_song_request (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    username    VARCHAR(32),
    channel_id  BIGINT NOT NULL,
    song_id     BIGINT NOT NULL,
    song_name   VARCHAR(128),
    artists     VARCHAR(255),
    message     VARCHAR(255),
    status      TINYINT DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    played_at   DATETIME,
    KEY idx_channel_status (channel_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点歌记录';

-- 用户收藏频道 (v1.1 预留)
CREATE TABLE IF NOT EXISTS t_user_channel (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    channel_id   BIGINT NOT NULL,
    channel_name VARCHAR(128),
    UNIQUE KEY uk_user_channel (user_id, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏频道';

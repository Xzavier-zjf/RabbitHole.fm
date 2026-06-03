-- RabbitHole.fm external music source migration
-- Run after sql/init.sql on existing databases.

USE `RabbitHole.fm`;

ALTER TABLE t_user_favorite
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'netease' AFTER song_id,
    ADD COLUMN source_song_id VARCHAR(128) NULL AFTER source,
    ADD COLUMN song_url VARCHAR(1024) NULL AFTER cover_url,
    ADD COLUMN source_payload MEDIUMTEXT NULL AFTER song_url;

UPDATE t_user_favorite
SET source_song_id = CAST(song_id AS CHAR)
WHERE source_song_id IS NULL OR source_song_id = '';

ALTER TABLE t_user_favorite
    MODIFY source_song_id VARCHAR(128) NOT NULL;

ALTER TABLE t_user_favorite
    DROP INDEX uk_user_song,
    ADD UNIQUE KEY uk_user_source_song (user_id, source, source_song_id);

ALTER TABLE t_song_request
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'netease' AFTER song_id,
    ADD COLUMN source_song_id VARCHAR(128) NULL AFTER source,
    ADD COLUMN cover_url VARCHAR(255) NULL AFTER artists,
    ADD COLUMN song_url VARCHAR(1024) NULL AFTER cover_url,
    ADD COLUMN source_payload MEDIUMTEXT NULL AFTER song_url;

UPDATE t_song_request
SET source_song_id = CAST(song_id AS CHAR)
WHERE source_song_id IS NULL OR source_song_id = '';

ALTER TABLE t_song_request
    MODIFY source_song_id VARCHAR(128) NOT NULL,
    ADD KEY idx_channel_source_status (channel_id, source, source_song_id, status);

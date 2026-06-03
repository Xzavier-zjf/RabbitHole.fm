-- RabbitHole.fm external source payload width repair
-- Run after sql/20260603_external_sources.sql when an existing database still has TEXT payload columns.

USE `RabbitHole.fm`;

ALTER TABLE t_user_favorite
    MODIFY source_payload MEDIUMTEXT NULL;

ALTER TABLE t_song_request
    MODIFY source_payload MEDIUMTEXT NULL;

package com.rabbithole.dto;

import lombok.Data;

import java.util.List;

@Data
public class SongDTO {
    private Long id;
    private String name;
    private List<String> artists;
    private String album;
    private String coverUrl;
    private Long durationMs;
    private String source;
    private String sourceLabel;
    private String sourceSongId;
    private String songUrl;
    private LyricDTO lyric;
    private String sourcePayload;
}

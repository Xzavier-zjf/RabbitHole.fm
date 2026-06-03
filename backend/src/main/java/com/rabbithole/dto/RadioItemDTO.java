package com.rabbithole.dto;

import lombok.Data;

import java.util.List;

@Data
public class RadioItemDTO {
    private Long requestId;
    private String type; // "song" or "dj"
    private Long songId;
    private String name;
    private List<String> artists;
    private String album;
    private String coverUrl;
    private Long durationMs;
    private String djUrl;
    private String songUrl;
    private LyricDTO lyric;
    private String source;
    private String sourceLabel;
    private String sourceSongId;
    private String sourcePayload;
    private String requester;
    private String message;

    public static RadioItemDTO song(SongDTO song, String url, LyricDTO lyric) {
        RadioItemDTO item = new RadioItemDTO();
        item.setType("song");
        item.setSongId(song.getId());
        item.setName(song.getName());
        item.setArtists(song.getArtists());
        item.setAlbum(song.getAlbum());
        item.setCoverUrl(song.getCoverUrl());
        item.setDurationMs(song.getDurationMs());
        item.setSongUrl(url);
        item.setLyric(lyric);
        item.setSource(song.getSource());
        item.setSourceLabel(song.getSourceLabel());
        item.setSourceSongId(song.getSourceSongId());
        item.setSourcePayload(song.getSourcePayload());
        return item;
    }

    public static RadioItemDTO dj(String djUrl, String prevName, String nextName) {
        RadioItemDTO item = new RadioItemDTO();
        item.setType("dj");
        item.setDjUrl(djUrl);
        item.setName("DJ: " + prevName + " → " + nextName);
        return item;
    }
}

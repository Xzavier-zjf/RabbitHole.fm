package com.rabbithole.controller;

import com.rabbithole.dto.RadioItemDTO;
import com.rabbithole.dto.SongDTO;
import com.rabbithole.service.DjScriptService;
import com.rabbithole.service.MimoTtsService;
import com.rabbithole.service.NeteaseMusicService;
import com.rabbithole.service.RadioPlaylistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/radio")
@RequiredArgsConstructor
@Slf4j
public class RadioController {

    private final NeteaseMusicService musicService;
    private final MimoTtsService ttsService;
    private final DjScriptService djService;
    private final RadioPlaylistService playlistService;

    @Value("${radio.dj.enabled:true}")
    private boolean djEnabled;

    @Value("${radio.dj.tts-timeout-ms:1800}")
    private long djTtsTimeoutMs;

    @GetMapping("/channel/{playlistId}")
    public List<RadioItemDTO> loadChannel(@PathVariable Long playlistId) throws IOException {
        return playlistService.loadChannel(playlistId);
    }

    @GetMapping("/song/{id}")
    public Map<String, Object> getSongData(@PathVariable long id) throws IOException {
        String url = musicService.getSongUrl(id);
        var lyric = musicService.getLyric(id);
        return Map.of("url", url != null ? url : "", "lyric", lyric);
    }

    @GetMapping(value = "/dj", produces = "audio/wav")
    public ResponseEntity<byte[]> getDjAudio(
            @RequestParam(required = false) Long prevId,
            @RequestParam(required = false) Long nextId,
            @RequestParam(required = false) String nextName,
            @RequestParam(required = false) String nextArtist,
            @RequestParam(required = false) String requester,
            @RequestParam(required = false) String message) throws IOException {
        if (!djEnabled) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .header("X-DJ-Skipped", "disabled")
                    .build();
        }

        SongDTO prev = null;
        SongDTO next = null;
        if (prevId != null) {
            try {
                prev = musicService.getSongDetail(prevId);
            } catch (IOException ignored) {
                // DJ intro can still be generated without previous song metadata.
            }
        }
        if (nextId != null) {
            try {
                next = musicService.getSongDetail(nextId);
            } catch (IOException ignored) {
                // Fall back to nextName/nextArtist below when detail lookup is unavailable.
            }
        }
        if (next == null && nextName != null && !nextName.isBlank()) {
            next = new SongDTO();
            next.setId(nextId);
            next.setName(nextName);
            next.setArtists(nextArtist != null && !nextArtist.isBlank()
                    ? List.of(nextArtist.split("[、/]\\s*"))
                    : List.of());
        }
        if (next == null) {
            next = new SongDTO();
            next.setName("下一首歌");
            next.setArtists(List.of());
        }

        DjScriptService.DjScript script = requester != null && !requester.isEmpty()
                ? djService.buildFastRequest(next, requester, message)
                : djService.buildFast(prev, next);

        byte[] audio;
        try {
            audio = ttsService.synthesize(script.styleHint(), script.text(), djTtsTimeoutMs);
        } catch (Exception e) {
            log.warn("DJ TTS skipped nextSong={} requester={} reason={}",
                    next.getName(), requester, e.toString());
            String subtitleBase64 = Base64.getEncoder()
                    .encodeToString(script.text().getBytes(StandardCharsets.UTF_8));
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .header("X-DJ-Skipped", "tts-unavailable")
                    .header("X-DJ-Subtitle-Base64", subtitleBase64)
                    .build();
        }
        String subtitleBase64 = Base64.getEncoder()
                .encodeToString(script.text().getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header("Content-Type", "audio/wav")
                .header("Cache-Control", "public, max-age=86400")
                .header("X-DJ-Subtitle-Base64", subtitleBase64)
                .body(audio);
    }

    @PostMapping("/next")
    public RadioItemDTO popNext(@RequestParam Long channelId) throws IOException {
        return playlistService.getNext(channelId);
    }
}

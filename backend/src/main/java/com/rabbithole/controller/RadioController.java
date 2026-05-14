package com.rabbithole.controller;

import com.rabbithole.dto.RadioItemDTO;
import com.rabbithole.dto.SongDTO;
import com.rabbithole.service.DjScriptService;
import com.rabbithole.service.MimoTtsService;
import com.rabbithole.service.NeteaseMusicService;
import com.rabbithole.service.RadioPlaylistService;
import lombok.RequiredArgsConstructor;
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
public class RadioController {

    private final NeteaseMusicService musicService;
    private final MimoTtsService ttsService;
    private final DjScriptService djService;
    private final RadioPlaylistService playlistService;

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
            @RequestParam Long nextId,
            @RequestParam(required = false) String requester,
            @RequestParam(required = false) String message) throws IOException {

        SongDTO prev = prevId != null ? musicService.getSongDetail(prevId) : null;
        SongDTO next = musicService.getSongDetail(nextId);

        DjScriptService.DjScript script;
        if (requester != null && !requester.isEmpty()) {
            var reqCtx = new DjScriptService.RequestContext(true, requester, message);
            script = djService.buildWithContext(prev, next, reqCtx);
        } else {
            script = djService.build(prev, next);
        }

        byte[] audio = ttsService.synthesize(script.styleHint(), script.text());
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

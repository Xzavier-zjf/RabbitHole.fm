package com.rabbithole.controller;

import com.rabbithole.service.MimoTtsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
public class TTSController {

    private final MimoTtsService ttsService;

    @PostMapping("/synthesize")
    public ResponseEntity<byte[]> synthesize(@RequestBody Map<String, String> body) throws IOException {
        String styleHint = body.getOrDefault("styleHint", "用温柔自然的女声朗读。");
        String text = body.get("text");
        if (text == null || text.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] audio = ttsService.synthesize(styleHint, text);
        return ResponseEntity.ok()
                .header("Content-Type", "audio/wav")
                .body(audio);
    }

    @GetMapping(value = "/test", produces = "audio/wav")
    public ResponseEntity<byte[]> test() throws IOException {
        String text = "(台湾腔)欢迎来到 RabbitHole.fm 深夜电台，我是你的 DJ 小糖。"
                + "接下来，让我们一起沉浸在音乐的世界里，希望你喜欢。";
        byte[] audio = ttsService.synthesize("用温柔慵懒的女声，台湾腔，深夜电台 DJ 风格。", text);
        return ResponseEntity.ok()
                .header("Content-Type", "audio/wav")
                .header("Cache-Control", "no-cache")
                .body(audio);
    }
}

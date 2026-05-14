package com.rabbithole.service;

import com.rabbithole.dto.SongDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class DjScriptService {

    private final LlmDjService llm;

    private static final String[] OPENINGS = {
            "欢迎回到 RabbitHole.fm，",
            "夜深了，",
            "是时候放松一下了，",
            "别走开，",
            "下一首送给屏幕前的你，"
    };

    private static final String STYLE_HINT =
            "用温柔自然的女声朗读，语速适中，像深夜电台 DJ，带点慵懒感。";

    public DjScript build(SongDTO previous, SongDTO next) {
        return buildWithContext(previous, next, null);
    }

    /**
     * Build DJ script with optional request context.
     */
    public DjScript buildWithContext(SongDTO previous, SongDTO next, RequestContext reqCtx) {
        String scene = decideScene(previous, next, reqCtx);
        Map<String, String> vars = buildVars(scene, previous, next, reqCtx);

        String text;
        try {
            text = llm.generate(scene, vars);
            log.debug("LLM DJ script generated for scene={}", scene);
        } catch (Exception e) {
            log.warn("LLM DJ failed for scene={}, falling back to template", scene, e);
            text = fallbackTemplate(scene, previous, next, reqCtx);
        }

        String styleHint = "用温柔慵懒的女声，台湾腔，深夜电台 DJ 风格，语速适中。";
        return new DjScript(styleHint, "(台湾腔)" + text);
    }

    private String decideScene(SongDTO prev, SongDTO next, RequestContext reqCtx) {
        if (reqCtx != null && reqCtx.isUserRequest) return "request";
        if (prev == null) return "opening";
        return "transition";
    }

    private Map<String, String> buildVars(String scene, SongDTO prev, SongDTO next, RequestContext reqCtx) {
        Map<String, String> v = new HashMap<>();
        v.put("period", periodOfDay());
        v.put("hour", String.valueOf(LocalTime.now().getHour()));

        if (prev != null) {
            v.put("prevSong", prev.getName() != null ? prev.getName() : "");
            v.put("prevArtist", joinArtists(prev.getArtists()));
        }
        if (next != null) {
            v.put("nextSong", next.getName() != null ? next.getName() : "");
            v.put("nextArtist", joinArtists(next.getArtists()));
        }
        if (reqCtx != null) {
            v.put("requester", reqCtx.requester != null ? reqCtx.requester : "听众");
            v.put("message", sanitize(reqCtx.message));
        }
        return v;
    }

    private String fallbackTemplate(String scene, SongDTO prev, SongDTO next, RequestContext reqCtx) {
        if ("request".equals(scene) && reqCtx != null && next != null) {
            return "接下来是" + (reqCtx.requester != null ? reqCtx.requester : "听众")
                    + "点播的《" + next.getName() + "》，"
                    + (reqCtx.message != null && !reqCtx.message.isEmpty()
                    ? "Ta 说：" + sanitize(reqCtx.message) + "。" : "")
                    + "希望你喜欢。";
        }
        if ("opening".equals(scene) && next != null) {
            return "欢迎来到 RabbitHole.fm，接下来为你带来 "
                    + joinArtists(next.getArtists()) + " 的《" + next.getName() + "》。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(台湾腔)");
        if (prev != null) {
            sb.append("刚刚为大家带来的是 ")
                    .append(joinArtists(prev.getArtists()))
                    .append(" 的《").append(prev.getName()).append("》。");
        }
        sb.append(OPENINGS[ThreadLocalRandom.current().nextInt(OPENINGS.length)]);
        if (next != null) {
            sb.append("接下来是 ")
                    .append(joinArtists(next.getArtists()))
                    .append(" 的《").append(next.getName()).append("》，希望你喜欢。");
        }
        return sb.toString();
    }

    private String periodOfDay() {
        int hour = LocalTime.now().getHour();
        if (hour < 6) return "凌晨";
        if (hour < 9) return "清晨";
        if (hour < 12) return "上午";
        if (hour < 14) return "中午";
        if (hour < 18) return "下午";
        if (hour < 22) return "晚上";
        return "深夜";
    }

    private String sanitize(String msg) {
        if (msg == null) return "";
        if (msg.length() > 80) msg = msg.substring(0, 80);
        return msg.replaceAll("[\\r\\n]+", " ");
    }

    private String joinArtists(java.util.List<String> artists) {
        if (artists == null || artists.isEmpty()) return "未知歌手";
        StringJoiner joiner = new StringJoiner("、");
        artists.forEach(joiner::add);
        return joiner.toString();
    }

    public record DjScript(String styleHint, String text) {}

    public record RequestContext(boolean isUserRequest, String requester, String message) {}
}

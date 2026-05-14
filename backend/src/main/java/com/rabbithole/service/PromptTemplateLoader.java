package com.rabbithole.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

@Component
@Slf4j
public class PromptTemplateLoader {

    @Value("${dj.prompt-file:classpath:prompts/dj.yml}")
    private Resource promptFile;

    private String systemPrompt;
    private Map<String, String> sceneTemplates;

    @PostConstruct
    public void init() {
        try (InputStream in = promptFile.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);
            systemPrompt = (String) data.get("system");
            @SuppressWarnings("unchecked")
            Map<String, String> scenes = (Map<String, String>) data.get("scenes");
            sceneTemplates = scenes;
            log.info("Loaded {} DJ prompt scenes", sceneTemplates.size());
        } catch (Exception e) {
            log.error("Failed to load DJ prompts, using defaults", e);
            systemPrompt = "你是 RabbitHole.fm 的电台 DJ，温柔自然。";
            sceneTemplates = Map.of("transition", "串场播报: {prevSong} → {nextSong}");
        }
    }

    public String system() {
        return systemPrompt;
    }

    public String scene(String name) {
        return sceneTemplates.getOrDefault(name, sceneTemplates.get("transition"));
    }
}

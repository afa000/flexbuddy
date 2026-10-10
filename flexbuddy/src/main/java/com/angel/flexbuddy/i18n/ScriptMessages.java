package com.angel.flexbuddy.i18n;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.MessageSource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import tools.jackson.databind.ObjectMapper;

/**
 * The text the browser scripts show, as JSON for the page to carry. Every key that starts with {@code js.} is sent
 * with its value as written in the message file ({@code {0}} and {@code ''} still in place), because the scripts fill
 * in the arguments themselves. The JSON is built once per language. The page controllers' advice owns one, so a
 * controller test slice needs no extra bean.
 */
public class ScriptMessages {

    static final String PREFIX = "js.";

    private final MessageSource messages;
    private final ObjectMapper mapper;
    private final List<String> keys;
    private final Map<Locale, String> cache = new ConcurrentHashMap<>();

    public ScriptMessages(MessageSource messages, ObjectMapper mapper) throws IOException {
        this.messages = messages;
        this.mapper = mapper;
        this.keys = PropertiesLoaderUtils.loadAllProperties("i18n/messages.properties").stringPropertyNames().stream()
                .filter(key -> key.startsWith(PREFIX)).sorted().toList();
    }

    /** JSON safe to place inside a {@code <script type="application/json">} element. */
    public String json(Locale locale) {
        return cache.computeIfAbsent(locale, this::build);
    }

    private String build(Locale locale) {
        Map<String, String> texts = new TreeMap<>();
        for (String key : keys) {
            texts.put(key, messages.getMessage(key, null, locale));
        }
        // A "<" is written as < so the text can never close the script element or open an HTML comment.
        return mapper.writeValueAsString(texts).replace("<", "\\u003c");
    }
}

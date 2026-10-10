package com.angel.flexbuddy.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The text the pages hand to the browser scripts. */
class ScriptMessagesTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private ScriptMessages scriptMessages() throws Exception {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return new ScriptMessages(source, mapper);
    }

    @Test
    void theEnglishJsonIsValidAndHoldsTheScriptText() throws Exception {
        JsonNode json = mapper.readTree(scriptMessages().json(Locale.ENGLISH));

        assertThat(json.get("js.home.recentEmpty").asString()).isEqualTo("No blocks yet. Add one from the + button.");
        assertThat(json.get("js.common.blocks.one").asString()).isEqualTo("{0} block");
        assertThat(json.get("js.common.blocks.other").asString()).isEqualTo("{0} blocks");
    }

    @Test
    void onlyTheScriptKeysAreSent() throws Exception {
        JsonNode json = mapper.readTree(scriptMessages().json(Locale.ENGLISH));

        assertThat(json.propertyNames()).isNotEmpty().allMatch(name -> name.startsWith("js."));
    }

    @Test
    void valuesKeepTheirPlaceholdersAndDoubledApostrophesForTheScriptToFill() throws Exception {
        JsonNode json = mapper.readTree(scriptMessages().json(Locale.ENGLISH));

        assertThat(json.get("js.app.savedOfflineAdded").asString()).isEqualTo("{0} on {1} will be added when you''re back online.");
    }

    @Test
    void theJsonCanNeverCloseTheScriptElementOrOpenAComment() throws Exception {
        String json = scriptMessages().json(Locale.ENGLISH);

        assertThat(json).doesNotContain("</script").doesNotContain("<!--").doesNotContain("<");
        // Markup in a value still reads back as markup once the page parses the JSON.
        assertThat(mapper.readTree(json).get("js.evaluate.basedOnAll").asString()).contains("<strong>");
    }

    @Test
    void theJsonIsBuiltOncePerLanguage() throws Exception {
        ScriptMessages messages = scriptMessages();

        assertThat(messages.json(Locale.ENGLISH)).isSameAs(messages.json(Locale.ENGLISH));
        assertThat(messages.json(Locale.FRENCH)).isEqualTo(messages.json(Locale.ENGLISH));
    }
}

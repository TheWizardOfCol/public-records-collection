package com.publicrecords.collection.cryptid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.publicrecords.collection.lobby.Wire.MAPPER;

/**
 * Loads and caches the Cryptid Cinema episodes shipped inside the Element archive at
 * {@code content/cryptid/episodes.json}. Read once on first use, held for the process
 * lifetime.
 */
@Singleton
public class CryptidCinemaContent {

    private static final Logger logger = LoggerFactory.getLogger(CryptidCinemaContent.class);

    private static final String EPISODES_RESOURCE = "content/cryptid/episodes.json";

    private volatile List<Episode> episodes;

    public List<Episode> episodes() {
        List<Episode> local = episodes;
        if (local == null) {
            synchronized (this) {
                if (episodes == null) {
                    episodes = readEpisodes();
                }
                local = episodes;
            }
        }
        return local;
    }

    public Optional<Episode> episode(final String id) {
        if (id == null) {
            return Optional.empty();
        }
        return episodes().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    public Optional<Episode> first() {
        final List<Episode> all = episodes();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    private List<Episode> readEpisodes() {
        final List<Episode> result = new ArrayList<>();
        for (final JsonNode node : readArray(EPISODES_RESOURCE)) {
            final List<Round> rounds = new ArrayList<>();
            for (final JsonNode r : node.path("rounds")) {
                final List<String> prompts = new ArrayList<>();
                for (final JsonNode p : r.path("prompts")) {
                    prompts.add(p.asText());
                }
                final List<Choice> choices = new ArrayList<>();
                for (final JsonNode c : r.path("choices")) {
                    choices.add(new Choice(c.path("id").asText(), c.path("label").asText(), null));
                }
                final List<Choice> deck = new ArrayList<>();
                for (final JsonNode c : r.path("deck")) {
                    deck.add(new Choice(c.path("id").asText(), c.path("label").asText(), null));
                }
                rounds.add(new Round(
                        r.path("index").asInt(),
                        RoundType.valueOf(r.path("type").asText()),
                        r.path("seeStart").asDouble(),
                        r.path("seeEnd").asDouble(),
                        r.path("recordStart").asDouble(),
                        r.path("recordEnd").asDouble(),
                        r.path("maxSeconds").asDouble(),
                        r.path("directorNotes").asText(""),
                        prompts,
                        choices,
                        deck));
            }
            result.add(new Episode(
                    node.path("id").asText(),
                    node.path("title").asText(),
                    node.path("blurb").asText(""),
                    node.path("video").asText(""),
                    node.path("videoNoMusic").asText(""),
                    node.path("hasFoundtrack").asBoolean(false),
                    rounds));
        }
        logger.info("loaded {} Cryptid Cinema episodes", result.size());
        return List.copyOf(result);
    }

    private List<JsonNode> readArray(final String resource) {
        try (InputStream in = open(resource)) {
            final JsonNode root = MAPPER.readTree(in);
            if (!root.isArray()) {
                throw new IllegalStateException(resource + " is not a JSON array");
            }
            final List<JsonNode> nodes = new ArrayList<>(root.size());
            root.forEach(nodes::add);
            return nodes;
        } catch (final IOException e) {
            throw new UncheckedIOException("failed to read " + resource, e);
        }
    }

    private InputStream open(final String resource) throws IOException {
        final InputStream in = CryptidCinemaContent.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("missing resource: " + resource);
        }
        return in;
    }

}

package com.publicrecords.collection.mallrats;

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
 * Loads and caches the Mall Rats stores shipped inside the Element archive at
 * {@code content/mall-rats/gifts.json}:
 *
 * <pre>{"stores":[{"id","name","meter","gifts":[{"id","name","image"}]}]}</pre>
 *
 * <p>The gift pictures live in the Element's served static tree; only the
 * paths are carried in the JSON.</p>
 */
@Singleton
public class MallRatsContent {

    private static final Logger logger = LoggerFactory.getLogger(MallRatsContent.class);

    private static final String STORES_RESOURCE = "content/mall-rats/gifts.json";

    /** How many gifts are drawn into a cycle's pool. */
    public static final int POOL_SIZE = 9;

    private volatile List<Store> stores;

    public List<Store> stores() {
        List<Store> local = stores;
        if (local == null) {
            synchronized (this) {
                if (stores == null) {
                    stores = readStores();
                }
                local = stores;
            }
        }
        return local;
    }

    public Optional<Store> store(final String id) {
        if (id == null) {
            return Optional.empty();
        }
        return stores().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public Optional<Store> first() {
        final List<Store> all = stores();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    private List<Store> readStores() {
        final List<Store> result = new ArrayList<>();
        try (InputStream in = open(STORES_RESOURCE)) {
            final JsonNode root = MAPPER.readTree(in);
            final JsonNode array = root.path("stores");
            if (!array.isArray()) {
                throw new IllegalStateException(STORES_RESOURCE + " has no stores array");
            }
            for (final JsonNode node : array) {
                final List<Gift> gifts = new ArrayList<>();
                for (final JsonNode g : node.path("gifts")) {
                    gifts.add(new Gift(
                            g.path("id").asText(),
                            g.path("name").asText(),
                            g.path("image").asText()));
                }
                result.add(new Store(
                        node.path("id").asText(),
                        node.path("name").asText(),
                        node.path("meter").asText("$"),
                        gifts));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("failed to read " + STORES_RESOURCE, e);
        }
        logger.info("loaded {} Mall Rats stores", result.size());
        return List.copyOf(result);
    }

    private InputStream open(final String resource) throws IOException {
        final InputStream in = MallRatsContent.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("missing resource: " + resource);
        }
        return in;
    }

}

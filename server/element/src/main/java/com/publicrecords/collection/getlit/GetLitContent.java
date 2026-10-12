package com.publicrecords.collection.getlit;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import static com.publicrecords.collection.lobby.Wire.MAPPER;

/**
 * Loads and caches Get Lit content shipped inside the Element archive: the book library and
 * prompt list (JSON under {@code content/get-lit}) and each book's full text (gzipped under
 * {@code content/books}).
 *
 * <p>Content is read once on first use and held for the process lifetime. Book texts are
 * loaded lazily per work, since a game only ever touches the handful of works its players
 * picked.</p>
 */
@Singleton
public class GetLitContent {

    private static final Logger logger = LoggerFactory.getLogger(GetLitContent.class);

    private static final String BOOKS_RESOURCE = "content/get-lit/books.json";
    private static final String PROMPTS_RESOURCE = "content/get-lit/prompts.json";
    private static final String BOOK_TEXT_RESOURCE = "content/books/%s.txt.gz";

    private static final int DEFAULT_PASSAGE_LIMIT = 1500;
    private static final int MAX_PASSAGE_LIMIT = 5000;

    private volatile List<Book> books;
    private volatile List<Prompt> prompts;

    /** Normalized full text per book id. */
    private final Map<String, String> textCache = new ConcurrentHashMap<>();

    public List<Book> books() {
        List<Book> local = books;
        if (local == null) {
            synchronized (this) {
                if (books == null) {
                    books = readBooks();
                }
                local = books;
            }
        }
        return local;
    }

    public List<Prompt> prompts() {
        List<Prompt> local = prompts;
        if (local == null) {
            synchronized (this) {
                if (prompts == null) {
                    prompts = readPrompts();
                }
                local = prompts;
            }
        }
        return local;
    }

    public Optional<Book> book(final String id) {
        if (id == null) {
            return Optional.empty();
        }
        return books().stream().filter(book -> book.id().equals(id)).findFirst();
    }

    /**
     * The whitespace-normalized full text of a book, loaded from its gzipped resource.
     */
    public String bookText(final Book book) {
        return textCache.computeIfAbsent(book.id(), id -> normalize(readBookText(book.slug())));
    }

    /**
     * The selectable works offered to players, filtered for family-friendliness.
     */
    public List<WorkView> works(final boolean familyFriendly) {
        final List<WorkView> result = new ArrayList<>();
        for (final Book book : books()) {
            if (book.allowed(familyFriendly)) {
                result.add(new WorkView(book.id(), book.title(), book.author()));
            }
        }
        return result;
    }

    /**
     * A slice of a work's normalized full text, for paging clients.
     *
     * @return the passage, or empty if no such work exists
     */
    public Optional<PassageView> passage(final String bookId, final int offset, final int limit) {
        return book(bookId).map(book -> {
            final String text = bookText(book);
            final int total = text.length();
            final int start = Math.max(0, Math.min(offset, total));
            final int length = Math.max(1, Math.min(limit <= 0 ? DEFAULT_PASSAGE_LIMIT : limit, MAX_PASSAGE_LIMIT));
            final int end = Math.min(total, start + length);
            final String slice = text.substring(start, end);
            return new PassageView(
                    book.id(), book.title(), book.author(), start, slice.length(), total, end >= total, slice);
        });
    }

    private List<Book> readBooks() {
        final List<Book> result = new ArrayList<>();
        for (final JsonNode node : readArray(BOOKS_RESOURCE)) {
            result.add(new Book(
                    node.path("id").asText(),
                    node.path("title").asText(),
                    node.path("author").asText(""),
                    node.path("category").asText(""),
                    node.path("isSpicy").asBoolean(false),
                    node.path("isActive").asBoolean(true),
                    node.path("slug").asText()));
        }
        logger.info("loaded {} Get Lit books", result.size());
        return List.copyOf(result);
    }

    private List<Prompt> readPrompts() {
        final List<Prompt> result = new ArrayList<>();
        for (final JsonNode node : readArray(PROMPTS_RESOURCE)) {
            result.add(new Prompt(
                    node.path("id").asText(),
                    node.path("text").asText(),
                    node.path("isSpicy").asBoolean(false),
                    node.path("isActive").asBoolean(true)));
        }
        logger.info("loaded {} Get Lit prompts", result.size());
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

    private String readBookText(final String slug) {
        final String resource = BOOK_TEXT_RESOURCE.formatted(slug);
        try (InputStream raw = open(resource);
             GZIPInputStream gz = new GZIPInputStream(raw)) {
            return new String(gz.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException("failed to read book text " + resource, e);
        }
    }

    private InputStream open(final String resource) throws IOException {
        final InputStream in = GetLitContent.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("missing resource: " + resource);
        }
        return in;
    }

    /** Collapse all whitespace runs to single spaces and trim, so passage matching is robust. */
    static String normalize(final String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

}

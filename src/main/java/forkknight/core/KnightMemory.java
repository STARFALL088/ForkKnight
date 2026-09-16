package forkknight.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The knight's memory: a tiny key=value vault persisted under the home
 * directory (.forkknight/memory). Hand-rolled on purpose - the realm
 * carries no JSON libraries, and the format is deliberately simple:
 *
 *   key=value        (one per line)
 *   # comments and blank lines are ignored
 *   \\n and \\\\ escapes decode to newline and backslash
 *
 * Writes are atomic-ish (write-then-move) so a crash mid-save can never
 * leave a half-written memory behind.
 */
public final class KnightMemory {

    private static final String DIRECTORY = ".forkknight";
    private static final String FILE_NAME = "memory";

    private final Path file;

    public KnightMemory() {
        this(Path.of(System.getProperty("user.home", "."), DIRECTORY, FILE_NAME));
    }

    public KnightMemory(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    /** Reads the whole memory; missing or broken files read as empty. */
    public Map<String, String> recallAll() {
        Map<String, String> memory = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return memory;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;   // no '=' or empty key: skip the scribble
                }
                memory.put(trimmed.substring(0, eq), unescape(trimmed.substring(eq + 1)));
            }
        } catch (IOException ignored) {
            // unreadable memory: start with a blank slate rather than fail
        }
        return memory;
    }

    public Optional<String> recall(String key) {
        return Optional.ofNullable(recallAll().get(key));
    }

    public Optional<String> recall(String key, String fallback) {
        return Optional.ofNullable(recallAll().getOrDefault(key, fallback));
    }

    /** Adds or updates one key while preserving every other entry. */
    public void remember(String key, String value) throws IOException {
        Map<String, String> memory = recallAll();
        memory.put(key, value == null ? "" : value);
        writeAll(memory);
    }

    /** Remembers a note for a feat hash; passing null or blank forgets the note. */
    public void rememberNote(String hash, String note) throws IOException {
        if (hash == null || hash.isBlank()) {
            return;
        }
        if (note == null || note.isBlank()) {
            forget("note." + hash);
        } else {
            remember("note." + hash, note);
        }
    }

    /** Recalls a feat note if present. */
    public Optional<String> recallNote(String hash) {
        if (hash == null || hash.isBlank()) {
            return Optional.empty();
        }
        return recall("note." + hash);
    }

    /** Forgets one key; missing keys are a no-op. */
    public void forget(String key) throws IOException {
        Map<String, String> memory = recallAll();
        if (memory.remove(key) != null) {
            writeAll(memory);
        }
    }

    /** Replaces the whole memory. */
    public void writeAll(Map<String, String> memory) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# ForkKnight's memory - knight keeps what matters\n");
        for (Map.Entry<String, String> entry : memory.entrySet()) {
            out.append(entry.getKey()).append('=')
                    .append(escape(entry.getValue())).append('\n');
        }
        Files.createDirectories(file.getParent());
        Path staging = file.resolveSibling(file.getFileName() + ".staging");
        Files.writeString(staging, out.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Files.move(staging, file,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    // ------------------------------------------------------------------
    // Escaping: only \n and \\ need it for a line-based format
    // ------------------------------------------------------------------

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String unescape(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(++i);
                if (next == 'n') {
                    out.append('\n');
                    continue;
                }
                if (next == '\\') {
                    out.append('\\');
                    continue;
                }
                out.append('\\').append(next);
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}

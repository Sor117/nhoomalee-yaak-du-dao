package ocs.persistence;

import java.util.ArrayList;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.io.UncheckedIOException;
import java.io.Writer;

/**
 * One directory of .properties files under data/; created on first run.
 */
public final class PropertyFileStore {
    private static final Path DATA_DIR = Paths.get("data");
    private final Path directory;

    public PropertyFileStore(String directoryName) {
        this.directory = DATA_DIR.resolve(directoryName);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save(String key, Properties values) {
        Path file = directory.resolve(key + ".properties");
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            values.store(out, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Properties load(String key) {
        Properties values = new Properties();
        Path file = directory.resolve(key + ".properties");
        if (!Files.exists(file)) {
            return values;
        }
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return values;
    }

    public List<String> keys() {
        List<String> keys = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".properties"))
                    .forEach(name -> keys.add(name.replace(".properties", "")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(keys);
    }

    public boolean contains(String key) {
        return Files.exists(directory.resolve(key + ".properties"));
    }
}

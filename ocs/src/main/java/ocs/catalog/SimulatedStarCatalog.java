package ocs.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.time.Month;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.io.UncheckedIOException;

import ocs.domain.Quadrant;
import ocs.domain.StarCatalog;
import ocs.domain.StarSystemInfo;

/**
 * Simulated external star catalogue, loaded from a bundled resource on first use.
 */
public final class SimulatedStarCatalog implements StarCatalog {
    private static final String RESOURCE = "/star_systems.csv";
    private final Map<String, StarSystemInfo> systems;

    public SimulatedStarCatalog() {
        this.systems = loadSystems();
    }

    @Override
    public Optional<StarSystemInfo> lookup(String constellation) {
        return Optional.ofNullable(systems.get(constellation));
    }

    public Map<String, StarSystemInfo> loadSystems() {
        Map<String, StarSystemInfo> loaded = new LinkedHashMap<>();
        try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            reader.readLine();
            String line = reader.readLine();
            while (line != null) {
                if (!line.isBlank()) {
                    String[] parts = line.split(";");
                    loaded.put(parts[0], new StarSystemInfo(parts[0],
                            Quadrant.valueOf(parts[1]),
                            Month.valueOf(parts[2].toUpperCase(Locale.ROOT))));
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return loaded;
    }
}

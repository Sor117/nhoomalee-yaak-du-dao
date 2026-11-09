package ocs.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.time.LocalDate;
import java.util.Map;

import ocs.domain.OcsException;

public final class ArgumentValues {
    private final Map<String, String> values;

    public ArgumentValues(String[] args) {
        this.values = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            String value = "true";
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[i + 1];
                i++;
            }
            if (values.containsKey(key)) {
                values.put(key, values.get(key) + ";" + value);
            } else {
                values.put(key, value);
            }
        }
    }

    public String getString(String key) {
        String value = values.get(key);
        if (value == null) {
            throw new OcsException("Missing required option --" + key + ".");
        }
        return value;
    }

    public int getInt(String key) {
        return Integer.parseInt(getString(key));
    }

    public double getDouble(String key) {
        return Double.parseDouble(getString(key));
    }

    public LocalDate getDate(String key) {
        return LocalDate.parse(getString(key));
    }

    public List<String> getAll(String key) {
        return List.of(getString(key).split(";"));
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }
}

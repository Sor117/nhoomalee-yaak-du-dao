package ocs.domain;

import java.util.Optional;

public interface StarCatalog {
    public Optional<StarSystemInfo> lookup(String constellation);
}

package org.mg94c18.alanford;

import java.util.HashMap;
import java.util.Map;

public class EpisodeIdMigration {
    private static Map<String, String> MIGRATION_MAP = new HashMap<>();

    public synchronized static Map<String, String> getMigrationMap() {
        return MIGRATION_MAP;
    }
}

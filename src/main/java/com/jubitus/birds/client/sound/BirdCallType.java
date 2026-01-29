package com.jubitus.birds.client.sound;

import java.util.Locale;

public enum BirdCallType {
    SINGLE("single", "call_single", "sounds_single"),
    FLOCK("flock", "call_flock", "sounds_flock");

    /**
     * Subfolder used inside the sounds path: sounds/default_species/<default_species>/<subdir>/<file>.ogg
     */
    public final String soundsSubdir;

    /**
     * Event suffix: default_species.<default_species>.<eventSuffix>
     */
    public final String eventSuffix;

    /**
     * Config root folder: config/jubitusbirds/default_species/<configRoot>/<default_species>/<file>.ogg
     */
    public final String configRootFolder;

    BirdCallType(String soundsSubdir, String eventSuffix, String configRootFolder) {
        this.soundsSubdir = soundsSubdir;
        this.eventSuffix = eventSuffix;
        this.configRootFolder = configRootFolder;
    }

    /**
     * For commands/debug: "single" / "flock"
     */
    public static BirdCallType fromString(String s) {
        if (s == null) return SINGLE;
        String x = s.trim().toLowerCase(Locale.ROOT);
        if ("flock".equals(x)) return FLOCK;
        return SINGLE;
    }
}

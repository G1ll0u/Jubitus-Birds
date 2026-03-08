package com.jubitus.birds.client.sound;

import java.util.Locale;

public enum BirdCallType {
    SINGLE("single", "call_single", "sounds_single"),
    FLOCK("flock", "call_flock", "sounds_flock");

    /**
     * Subfolder used inside the sounds path: sounds/species/<species>/<subdir>/<file>.ogg
     */
    public final String soundsSubdir;

    /**
     * Event suffix: species.<species>.<eventSuffix>
     */
    public final String eventSuffix;

    /**
     * Config root folder: config/jubitusbirds/species/<configRoot>/<species>/<file>.ogg
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

package com.jubitus.birds.client.sound;

import com.jubitus.birds.JubitusBirds;
import com.jubitus.jubitusbirds.Tags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BirdSoundSystem {
    // Cache SoundEvent objects for sounds.json-defined events (not Forge-registered)
    private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, SoundEvent> EVENT_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final int DEFAULT_ATTENUATION_DISTANCE = 128;
    /**
     * One default_species -> two pools (single + flock)
     */
    private static final Map<String, SpeciesPools> SPECIES_SOUNDS = new LinkedHashMap<>();
    /**
     * Active moving sounds by bird id
     */
    private static final Map<Long, ActiveEntry> ACTIVE_BY_BIRD = new ConcurrentHashMap<>();
    private static final int STARTUP_GRACE_TICKS = 5;     // ~0.25s for engine to actually start
    private static final int FLOCK_SPACING_TICKS = 8;     // ~0.4s between flock starts (per flockId)
    /**
     * Per-flock "don’t start too many at once" guard (tick of last started flock call)
     */
    private static final Map<Long, Integer> LAST_FLOCK_START_TICK = new ConcurrentHashMap<>();
    private static final Map<String, EnumMap<BirdCallType, Integer>> ATTENUATION_BY_SPECIES =
            new ConcurrentHashMap<>();
    private static final java.util.Queue<DelayedPlay> DELAYED = new java.util.concurrent.ConcurrentLinkedQueue<>();
    // simple global throttle: limit how many calls can start per tick
    private static int lastTick = -1;
    private static int startedThisTick = 0;

    private static IResourcePack installedPack = null;

    private BirdSoundSystem() {
    }

    public static SoundEvent getOrCreateEvent(ResourceLocation rl) {
        // In 1.12: events defined in sounds.json are NOT in SoundEvent.REGISTRY.
        // Creating a SoundEvent with the correct RL is enough for SoundHandler to resolve it.
        return EVENT_CACHE.computeIfAbsent(rl, SoundEvent::new);
    }

    /**
     * Call once during client init. Safe to call multiple times.
     */
    public static void installResourcePackOnce() {
        if (installedPack != null) return;

        installedPack = new ConfigSoundsResourcePack();
        injectPackIntoMinecraft(installedPack);

        // 🔥 IMPORTANT: make ResourceManager rebuild pack list so sounds.json is discovered
        Minecraft mc = Minecraft.getMinecraft();
        try {
            mc.refreshResources(); // heavy, but do it only once
        } catch (Exception e) {
            JubitusBirds.LOGGER.error("[JubitusBirds] refreshResources failed after pack install", e);
        }

        JubitusBirds.LOGGER.info("[JubitusBirds] Installed ConfigSoundsResourcePack for config-based .ogg sounds.");
    }

    /**
     * 1.12.2: Minecraft has a private List<IResourcePack> defaultResourcePacks.
     * We inject our pack via reflection.
     */
    @SuppressWarnings("unchecked")
    private static void injectPackIntoMinecraft(IResourcePack pack) {
        Minecraft mc = Minecraft.getMinecraft();

        Field f = null;
        try {
            // Dev name (MCP)
            f = Minecraft.class.getDeclaredField("defaultResourcePacks");
        } catch (NoSuchFieldException ignored) {
        }

        try {
            // Reobf/SRG name commonly used in 1.12.2
            if (f == null) f = Minecraft.class.getDeclaredField("field_110449_ao");
        } catch (NoSuchFieldException ignored) {
        }

        if (f == null) {
            JubitusBirds.LOGGER.error("[JubitusBirds] Could not find Minecraft.defaultResourcePacks field (dev or SRG). Sounds pack not installed.");
            return;
        }

        try {
            f.setAccessible(true);
            List<IResourcePack> list = (List<IResourcePack>) f.get(mc);

            if (!list.contains(pack)) {
                list.add(pack);
                JubitusBirds.LOGGER.info("[JubitusBirds] Injected ConfigSoundsResourcePack into defaultResourcePacks (size now {}).", list.size());
            }

        } catch (Throwable t) {
            JubitusBirds.LOGGER.error("[JubitusBirds] Failed to inject ConfigSoundsResourcePack. Sounds will not work.", t);
        }
    }

    /**
     * Backward-compat helper (treat as SINGLE).
     */
    public static void setSpeciesSounds(String soundKey, List<String> soundNamesNoExt) {
        setSpeciesSounds(soundKey, BirdCallType.SINGLE, soundNamesNoExt);
    }

    /**
     * Called by BirdSpeciesLoader while loading default_species.
     */
    public static void setSpeciesSounds(String soundKey, BirdCallType type, List<String> soundNamesNoExt) {
        String key = safe(soundKey);
        if (soundNamesNoExt == null) soundNamesNoExt = Collections.emptyList();

        SpeciesPools pools = SPECIES_SOUNDS.computeIfAbsent(key, k -> new SpeciesPools());

        if (type == BirdCallType.FLOCK) {
            pools.flock = new ArrayList<>(soundNamesNoExt);
        } else {
            pools.single = new ArrayList<>(soundNamesNoExt);
        }
    }

    private static String safe(String s) {
        if (s == null) return "unknown";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    /**
     * Clear all mapped sounds (called on reload before re-registering).
     */
    public static void clearAllSpeciesSounds() {
        SPECIES_SOUNDS.clear();
    }

    /**
     * Used by the resource pack to generate sounds.json
     */
    public static String buildSoundsJson() {
        // sounds.json format: keys are "path.with.dots": { "category":"ambient", "sounds":[ "modid:path", ... ] }
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        boolean firstEvent = true;

        for (Map.Entry<String, SpeciesPools> e : SPECIES_SOUNDS.entrySet()) {
            String speciesKey = e.getKey();
            SpeciesPools pools = e.getValue();
            if (pools == null) continue;

            // SINGLE event
            if (pools.single != null && !pools.single.isEmpty()) {
                if (!firstEvent) sb.append(",\n");
                firstEvent = false;
                appendEvent(sb, speciesKey, BirdCallType.SINGLE, pools.single);
            }

            // FLOCK event
            if (pools.flock != null && !pools.flock.isEmpty()) {
                if (!firstEvent) sb.append(",\n");
                firstEvent = false;
                appendEvent(sb, speciesKey, BirdCallType.FLOCK, pools.flock);
            }
        }

        sb.append("\n}\n");
        return sb.toString();
    }

    private static void appendEvent(StringBuilder sb, String speciesKey, BirdCallType type, List<String> sounds) {
        String eventKey = "default_species." + speciesKey + "." + type.eventSuffix;

        int attenuation = getAttenuationDistance(speciesKey, type);

        sb.append("  \"").append(eventKey).append("\": {\n");
        sb.append("    \"category\": \"ambient\",\n");
        sb.append("    \"sounds\": [\n");

        for (int i = 0; i < sounds.size(); i++) {
            String soundNoExt = sounds.get(i);

            sb.append("      {\n");
            sb.append("        \"name\": \"")
                    .append(Tags.MOD_ID)
                    .append(":default_species/")
                    .append(speciesKey)
                    .append("/")
                    .append(type.soundsSubdir)
                    .append("/")
                    .append(soundNoExt)
                    .append("\",\n");
            sb.append("        \"attenuation_distance\": ")
                    .append(attenuation)
                    .append("\n");
            sb.append("      }");

            if (i < sounds.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("    ]\n");
        sb.append("  }");
    }

    private static int getAttenuationDistance(String soundKey, BirdCallType type) {
        EnumMap<BirdCallType, Integer> m = ATTENUATION_BY_SPECIES.get(safe(soundKey));
        if (m == null) return 128;
        Integer v = m.get(type);
        return (v != null) ? v : 128;
    }

    /**
     * Do we have any sounds for this default_species + type?
     */
    public static boolean hasSounds(String soundKey, BirdCallType type) {
        SpeciesPools pools = SPECIES_SOUNDS.get(safe(soundKey));
        if (pools == null) return false;

        List<String> l = (type == BirdCallType.FLOCK) ? pools.flock : pools.single;
        return l != null && !l.isEmpty();
    }

    /**
     * ResourceLocation for the call event for a default_species + type.
     */
    public static ResourceLocation getCallEvent(String soundKey, BirdCallType type) {
        return new ResourceLocation(Tags.MOD_ID, "default_species." + safe(soundKey) + "." + type.eventSuffix);
    }

    /**
     * Debug: keys loaded.
     */
    public static java.util.Set<String> getAllSpeciesKeys() {
        return new java.util.LinkedHashSet<>(SPECIES_SOUNDS.keySet());
    }

    /**
     * Debug: sounds list for key+type.
     */
    public static java.util.List<String> getSoundsForSpecies(String soundKey, BirdCallType type) {
        SpeciesPools pools = SPECIES_SOUNDS.get(safe(soundKey));
        if (pools == null) return java.util.Collections.emptyList();
        java.util.List<String> l = (type == BirdCallType.FLOCK) ? pools.flock : pools.single;
        return (l != null) ? l : java.util.Collections.emptyList();
    }

    /**
     * Attempt to start a call sound for a bird (respects global throttle).
     * Adds a tiny per-flock start spacing so big flocks don’t “machine-gun” together.
     */
    public static void playCallIfAllowed(long birdId, long flockId, BirdCallType type, BirdCallSound sound) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.getSoundHandler() == null) return;

        SoundHandler sh = mc.getSoundHandler();

        int tick = (int) mc.world.getTotalWorldTime();
        if (tick != lastTick) {
            lastTick = tick;
            startedThisTick = 0;
        }

        if (startedThisTick >= 20) return;

        // --- Per-flock spacing (prevents a chorus burst that steals channels) ---
        if (type == BirdCallType.FLOCK && flockId != 0L) {
            Integer last = LAST_FLOCK_START_TICK.get(flockId);
            if (last != null && (tick - last) < FLOCK_SPACING_TICKS) {
                return;
            }
        }

        // --- Already tracked? ---
        ActiveEntry entry = ACTIVE_BY_BIRD.get(birdId);
        if (entry != null) {
            BirdCallSound cur = entry.sound;

            // If it's playing, don't start another.
            if (sh.isSoundPlaying(cur)) return;

            // If it's NOT playing yet but still within startup grace, also don't start another.
            if ((tick - entry.startTick) < STARTUP_GRACE_TICKS && !cur.isDonePlaying()) return;

            // Otherwise it's stale -> stop it and replace
            stopForBird(birdId);
        }

        startedThisTick++;

        // track BEFORE playing (so we don't double-start the same tick)
        ACTIVE_BY_BIRD.put(birdId, new ActiveEntry(sound, tick));

        JubitusBirds.LOGGER.info("[JubitusBirds] PLAY request bird={} flock={} type={} event={}",
                birdId, flockId, type, sound.getSoundLocation());

        sh.playSound(sound);

        // mark per-flock start tick only if we actually attempted start
        if (type == BirdCallType.FLOCK && flockId != 0L) {
            LAST_FLOCK_START_TICK.put(flockId, tick);
        }

        JubitusBirds.LOGGER.info("[JubitusBirds] PLAY called; isSoundPlayingNow={}",
                sh.isSoundPlaying(sound));
    }

    // -----------------------
    // Helpers
    // -----------------------

    public static void stopForBird(long birdId) {
        Minecraft mc = Minecraft.getMinecraft();
        SoundHandler sh = (mc != null) ? mc.getSoundHandler() : null;

        ActiveEntry old = ACTIVE_BY_BIRD.remove(birdId);
        if (old != null && sh != null) {
            sh.stopSound(old.sound);
        }

    }

    public static void stopAll() {
        Minecraft mc = Minecraft.getMinecraft();
        SoundHandler sh = (mc != null) ? mc.getSoundHandler() : null;

        for (ActiveEntry e : ACTIVE_BY_BIRD.values()) {
            if (sh != null && e != null) sh.stopSound(e.sound);
        }

        ACTIVE_BY_BIRD.clear();
    }

    public static void tickCleanup() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getSoundHandler() == null) return;

        SoundHandler sh = mc.getSoundHandler();

        Iterator<Map.Entry<Long, ActiveEntry>> it = ACTIVE_BY_BIRD.entrySet().iterator();
        while (it.hasNext()) {
            ActiveEntry e = it.next().getValue();
            if (e == null || e.sound == null) {
                it.remove();
                continue;
            }

            BirdCallSound s = e.sound;
            boolean playing = sh.isSoundPlaying(s);

            // Important: allow startup grace before declaring it "not playing"
            if (!playing) {
                int tick = (mc.world != null) ? (int) mc.world.getTotalWorldTime() : 0;
                if ((tick - e.startTick) < STARTUP_GRACE_TICKS && !s.isDonePlaying()) {
                    continue; // still starting/loading; don't drop tracking yet
                }

                JubitusBirds.LOGGER.warn("[JubitusBirds] SOUND NOT PLAYING (rejected/ended) bird={} event={}",
                        s.getBirdId(), s.getSoundLocation());
                it.remove();
                continue;
            }

            if (s.isDonePlaying()) {
                JubitusBirds.LOGGER.info("[JubitusBirds] SOUND DONE bird={} event={}",
                        s.getBirdId(), s.getSoundLocation());
                it.remove();
            }
            // ---- Delayed sound plays (used for fake reflections) ----
            if (mc != null && mc.world != null && sh != null) {
                int tickNow = (int) mc.world.getTotalWorldTime();

                while (true) {
                    DelayedPlay dp = DELAYED.peek();
                    if (dp == null) break;
                    if (dp.playTick > tickNow) break;

                    DELAYED.poll();
                    if (dp.sound != null) {
                        sh.playSound(dp.sound);
                    }
                }
            }

        }


        // Light cleanup for the flock spacing map (prevent long-running sessions from accumulating ids)
        int tick = (mc.world != null) ? (int) mc.world.getTotalWorldTime() : 0;
        if (tick % 200 == 0 && LAST_FLOCK_START_TICK.size() > 256) {
            // drop old entries (older than 2 minutes)
            final int MAX_AGE = 20 * 120;
            LAST_FLOCK_START_TICK.entrySet().removeIf(e -> (tick - e.getValue()) > MAX_AGE);
        }
    }

    public static void setAttenuationDistance(String soundKey, BirdCallType type, double dist) {
        String key = safe(soundKey);
        int d = (int) Math.round(Math.max(4.0, Math.min(1024.0, dist)));
        ATTENUATION_BY_SPECIES
                .computeIfAbsent(key, k -> new EnumMap<>(BirdCallType.class))
                .put(type, d);
    }

    public static void reloadSoundsOnly() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getSoundHandler() == null || mc.getResourceManager() == null) return;

        // This reloads ONLY the sound registry from the current resource packs (including your injected one).
        mc.getSoundHandler().onResourceManagerReload(mc.getResourceManager());

        JubitusBirds.LOGGER.info("[JubitusBirds] Reloaded sound system only (no full resource refresh).");
    }

    public static void schedulePlay(ISound sound, int delayTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) return;
        int now = (int) mc.world.getTotalWorldTime();
        DELAYED.add(new DelayedPlay(sound, now + Math.max(0, delayTicks)));
    }

    private static class ActiveEntry {
        final BirdCallSound sound;
        final int startTick;

        ActiveEntry(BirdCallSound sound, int startTick) {
            this.sound = sound;
            this.startTick = startTick;
        }
    }

    private static class SpeciesPools {
        List<String> single = Collections.emptyList();
        List<String> flock = Collections.emptyList();
    }

    private static class DelayedPlay {
        final ISound sound;
        final int playTick;

        DelayedPlay(ISound sound, int playTick) {
            this.sound = sound;
            this.playTick = playTick;
        }
    }
}

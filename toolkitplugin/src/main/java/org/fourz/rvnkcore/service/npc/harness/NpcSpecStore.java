package org.fourz.rvnkcore.service.npc.harness;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Spec files under {@code plugins/RVNKCore/npc/} (#2248). A spec is named without a path and
 * without {@code .yml}; the name is checked so no command can read or write outside that folder.
 *
 * @since 1.5.100-alpha
 */
public final class NpcSpecStore {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final File folder;

    /** @param folder the spec folder, normally {@code <dataFolder>/npc} */
    public NpcSpecStore(File folder) {
        this.folder = folder;
    }

    public File folder() {
        return folder;
    }

    /**
     * @return the spec name with {@code .yml} stripped, or null when it is not a valid name
     */
    public static String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            name = name.substring(0, name.length() - 4);
        }
        return VALID_NAME.matcher(name).matches() ? name : null;
    }

    /** @return the file for a valid name; null for an invalid one */
    public File file(String rawName) {
        String name = normalizeName(rawName);
        return name == null ? null : new File(folder, name + ".yml");
    }

    /** @return spec names (no extension), sorted; empty when the folder does not exist */
    public List<String> list() {
        File[] files = folder.listFiles((dir, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (File f : files) {
            String name = normalizeName(f.getName());
            if (name != null) {
                names.add(name);
            }
        }
        Collections.sort(names);
        return names;
    }

    /** @throws IOException when the name is invalid, the file is missing, or it cannot be read */
    public String read(String rawName) throws IOException {
        File file = file(rawName);
        if (file == null) {
            throw new IOException("invalid spec name '" + rawName + "' (use A-Z a-z 0-9 _ -, 1-64 characters)");
        }
        if (!file.isFile()) {
            throw new IOException("no spec file " + file.getPath());
        }
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    /**
     * Writes a spec, creating the folder.
     *
     * @param overwrite false refuses to replace an existing file
     * @return the file written
     * @throws IOException when the name is invalid, the file exists and overwrite is false, or the write fails
     */
    public File write(String rawName, String text, boolean overwrite) throws IOException {
        File file = file(rawName);
        if (file == null) {
            throw new IOException("invalid spec name '" + rawName + "' (use A-Z a-z 0-9 _ -, 1-64 characters)");
        }
        if (file.exists() && !overwrite) {
            throw new IOException(file.getPath() + " exists; add --force to overwrite it");
        }
        Files.createDirectories(folder.toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        return file;
    }
}

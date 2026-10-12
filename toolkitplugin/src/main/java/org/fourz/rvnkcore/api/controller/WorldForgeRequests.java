package org.fourz.rvnkcore.api.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.fourz.rvnkcore.api.model.response.FieldError;
import org.fourz.rvnkcore.api.model.worlds.CreateGroupRequest;
import org.fourz.rvnkcore.api.model.worlds.CreateSkyStackRequest;
import org.fourz.rvnkcore.api.model.worlds.CreateWorldV2Request;
import org.fourz.rvnkcore.api.model.worlds.GroupWorldRequest;
import org.fourz.rvnkcore.api.model.worlds.PresetDTO;
import org.fourz.rvnkcore.api.model.worlds.PreviewRequest;
import org.fourz.rvnkcore.api.model.worlds.SkyStackSettingsRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses and shape-validates World Forge request bodies for {@link RVNKWorldsController} (#2200).
 *
 * <p>Parsing is done by hand from a {@link JsonObject} rather than {@code gson.fromJson(body, X.class)}
 * so a wrong type is reported as a field error ({@code seed: must be an integer}) instead of one
 * opaque parse failure, and so numbers inside {@code settings} keep their integer-ness
 * ({@link Long} for integral values, {@link Double} otherwise) instead of all becoming doubles.</p>
 *
 * <p>Only the shape is checked here. Whether a generator, preset or setting value is acceptable is
 * RVNKWorlds' call — it owns the schema.</p>
 */
final class WorldForgeRequests {

    /** Body keys that route {@code POST /worlds} to the v2 path when present and non-null. */
    static final List<String> V2_KEYS = List.of("generator", "preset", "settings");

    private WorldForgeRequests() {
    }

    /** Parse result: exactly one of {@code value} / {@code errors} is meaningful. */
    record Parsed<T>(T value, List<FieldError> errors) {
        boolean ok() {
            return errors.isEmpty();
        }
    }

    /**
     * Parses a body as a JSON object, or returns {@code null} when it is empty, malformed or not an
     * object. Never throws.
     */
    static JsonObject parseObject(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonElement el = JsonParser.parseString(body);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * True when a {@code POST /worlds} body selects the v2 path: a JSON object carrying a non-null
     * {@code generator}, {@code preset} or {@code settings}. Anything else — including a body that
     * fails to parse — stays on the legacy path, which reports its own errors as it always has.
     */
    static boolean isV2CreateBody(JsonObject obj) {
        if (obj == null) return false;
        for (String key : V2_KEYS) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) return true;
        }
        return false;
    }

    static Parsed<CreateWorldV2Request> parseCreateV2(JsonObject obj) {
        List<FieldError> errors = new ArrayList<>();
        CreateWorldV2Request r = new CreateWorldV2Request();
        r.setName(requiredString(obj, "name", errors));
        r.setSeed(optionalLong(obj, "seed", errors));
        r.setGenerator(optionalString(obj, "generator", errors));
        r.setPreset(optionalString(obj, "preset", errors));
        r.setSettings(optionalObject(obj, "settings", errors));
        r.setGroupName(optionalString(obj, "groupName", errors));
        Boolean autoLoad = optionalBoolean(obj, "autoLoad", errors);
        r.setAutoLoad(autoLoad != null && autoLoad);
        if (r.getGenerator() == null && r.getPreset() == null && !hasError(errors, "generator", "preset")) {
            errors.add(new FieldError("generator", "generator or preset is required"));
        }
        return new Parsed<>(r, errors);
    }

    static Parsed<PresetDTO> parsePreset(JsonObject obj) {
        List<FieldError> errors = new ArrayList<>();
        PresetDTO p = new PresetDTO();
        p.setName(requiredString(obj, "name", errors));
        p.setGenerator(requiredString(obj, "generator", errors));
        p.setDisplayName(optionalString(obj, "displayName", errors));
        p.setDescription(optionalString(obj, "description", errors));
        p.setAuthor(optionalString(obj, "author", errors));
        p.setSettings(optionalObject(obj, "settings", errors));
        // Server-owned: a client must not be able to mint a "built-in" or back-date one.
        p.setBuiltIn(false);
        p.setCreatedAt(null);
        return new Parsed<>(p, errors);
    }

    static Parsed<PreviewRequest> parsePreview(JsonObject obj) {
        List<FieldError> errors = new ArrayList<>();
        PreviewRequest r = new PreviewRequest();
        r.setGenerator(optionalString(obj, "generator", errors));
        r.setPreset(optionalString(obj, "preset", errors));
        r.setSeed(optionalLong(obj, "seed", errors));
        r.setSettings(optionalObject(obj, "settings", errors));
        Integer centerX = optionalInt(obj, "centerX", errors);
        Integer centerZ = optionalInt(obj, "centerZ", errors);
        Integer size = optionalInt(obj, "size", errors);
        Integer step = optionalInt(obj, "step", errors);

        r.setCenterX(centerX != null ? centerX : 0);
        r.setCenterZ(centerZ != null ? centerZ : 0);
        r.setSize(size != null ? size : PreviewRequest.DEFAULT_SIZE);
        r.setStep(step != null ? step : PreviewRequest.DEFAULT_STEP);

        if (r.getGenerator() == null && r.getPreset() == null && !hasError(errors, "generator", "preset")) {
            errors.add(new FieldError("generator", "generator or preset is required"));
        }
        checkRange(errors, "centerX", r.getCenterX(), -PreviewRequest.MAX_ABS_CENTER, PreviewRequest.MAX_ABS_CENTER);
        checkRange(errors, "centerZ", r.getCenterZ(), -PreviewRequest.MAX_ABS_CENTER, PreviewRequest.MAX_ABS_CENTER);
        boolean sizeOk = checkRange(errors, "size", r.getSize(), 1, PreviewRequest.MAX_SIZE);
        boolean stepOk = checkRange(errors, "step", r.getStep(), 1, PreviewRequest.MAX_SIZE);
        if (sizeOk && stepOk && !hasError(errors, "size", "step")) {
            long side = r.samplesPerSide();
            if (side * side > PreviewRequest.MAX_SAMPLES) {
                int maxSide = (int) Math.sqrt(PreviewRequest.MAX_SAMPLES);
                errors.add(new FieldError("step", "size/step gives " + side + "x" + side
                        + " samples; the limit is " + maxSide + "x" + maxSide
                        + " (raise step to at least " + ((r.getSize() + maxSide - 1) / maxSide) + ")"));
            }
        }
        return new Parsed<>(r, errors);
    }

    // ── control plane (#2218): groups and sky stacks ─────────────────────────────────────────

    /** Keys {@code PUT /skystacks/{group}} accepts: the {@code skyStack} block of worlds.yml. */
    static final List<String> SKYSTACK_SETTING_KEYS = List.of("enabled", "landingMode", "ascendCooldownSeconds",
            "descentTriggerYOffset", "deepTriggerY", "deepAscendOffset");

    /** {@code POST /groups}: {@code {name, inventoryLink?, portalEmulation?}}. */
    static Parsed<CreateGroupRequest> parseCreateGroup(JsonObject obj) {
        List<FieldError> errors = new ArrayList<>();
        CreateGroupRequest r = new CreateGroupRequest();
        r.setName(requiredString(obj, "name", errors));
        Boolean link = optionalBoolean(obj, "inventoryLink", errors);
        Boolean portal = optionalBoolean(obj, "portalEmulation", errors);
        r.setInventoryLink(link != null && link);
        r.setPortalEmulation(portal != null && portal);
        return new Parsed<>(r, errors);
    }

    /** {@code POST /groups/{name}/worlds}: {@code {world, force?}}. */
    static Parsed<GroupWorldRequest> parseGroupWorld(JsonObject obj) {
        List<FieldError> errors = new ArrayList<>();
        GroupWorldRequest r = new GroupWorldRequest();
        r.setWorld(requiredString(obj, "world", errors));
        Boolean force = optionalBoolean(obj, "force", errors);
        r.setForce(force != null && force);
        return new Parsed<>(r, errors);
    }

    /** {@code PUT /groups/{name}/permission}: {@code {requiresPermission}}. */
    static Parsed<Boolean> parsePermission(JsonObject obj) {
        List<FieldError> errors = new ArrayList<>();
        Boolean v = optionalBoolean(obj, "requiresPermission", errors);
        if (v == null && !hasError(errors, "requiresPermission")) {
            errors.add(new FieldError("requiresPermission", "is required (true = set the gate, false = clear it)"));
        }
        return new Parsed<>(v, errors);
    }

    /**
     * {@code POST /skystacks}: {@code {group | bottomWorld, direction?, count, template?, generator?, preset?,
     * settings?}}. Shape only; the count cap and every name are RVNKWorlds' to check.
     */
    static Parsed<CreateSkyStackRequest> parseCreateSkyStack(JsonObject obj) {
        List<FieldError> errors = new ArrayList<>();
        CreateSkyStackRequest r = new CreateSkyStackRequest();
        r.setGroup(optionalString(obj, "group", errors));
        r.setBottomWorld(optionalString(obj, "bottomWorld", errors));
        if (r.getGroup() == null && r.getBottomWorld() == null && !hasError(errors, "group", "bottomWorld")) {
            errors.add(new FieldError("group", "group or bottomWorld is required"));
        }
        String direction = optionalString(obj, "direction", errors);
        if (direction != null) {
            try {
                r.setDirection(CreateSkyStackRequest.Direction.valueOf(direction.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError("direction", "must be up, down or both"));
            }
        }
        Integer count = optionalInt(obj, "count", errors);
        if (count == null) {
            if (!hasError(errors, "count")) errors.add(new FieldError("count", "is required"));
        } else if (count < 1) {
            errors.add(new FieldError("count", "must be at least 1"));
        } else {
            r.setCount(count);
        }
        r.setTemplate(optionalString(obj, "template", errors));
        r.setGenerator(optionalString(obj, "generator", errors));
        r.setPreset(optionalString(obj, "preset", errors));
        r.setSettings(optionalObject(obj, "settings", errors));
        return new Parsed<>(r, errors);
    }

    /**
     * {@code PUT /skystacks/{group}}: a partial {@code skyStack} block. Unknown keys are refused, and roof,
     * floor and seal-band keys get a message that says why: they are frozen per world at creation.
     */
    static Parsed<SkyStackSettingsRequest> parseSkyStackSettings(JsonObject obj) {
        List<FieldError> errors = new ArrayList<>();
        SkyStackSettingsRequest r = new SkyStackSettingsRequest();
        for (String key : obj.keySet()) {
            if (SKYSTACK_SETTING_KEYS.contains(key)) continue;
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.startsWith("roof") || lower.startsWith("floor") || lower.contains("seal")) {
                errors.add(new FieldError(key, "is a frozen generation setting of each world; it is set when the"
                        + " layer is created (POST /skystacks settings) and cannot change after"));
            } else {
                errors.add(new FieldError(key, "unknown sky-stack setting; supported: "
                        + String.join(", ", SKYSTACK_SETTING_KEYS)));
            }
        }
        r.setEnabled(optionalBoolean(obj, "enabled", errors));
        r.setLandingMode(optionalString(obj, "landingMode", errors));
        r.setAscendCooldownSeconds(optionalInt(obj, "ascendCooldownSeconds", errors));
        r.setDescentTriggerYOffset(optionalInt(obj, "descentTriggerYOffset", errors));
        r.setDeepTriggerY(optionalInt(obj, "deepTriggerY", errors));
        r.setResetDeepTriggerY(obj.has("deepTriggerY") && obj.get("deepTriggerY").isJsonNull());
        r.setDeepAscendOffset(optionalInt(obj, "deepAscendOffset", errors));
        boolean any = false;
        for (String key : SKYSTACK_SETTING_KEYS) any |= obj.has(key);
        if (!any && errors.isEmpty()) {
            errors.add(new FieldError("settings", "no setting to change; supported: "
                    + String.join(", ", SKYSTACK_SETTING_KEYS)));
        }
        return new Parsed<>(r, errors);
    }

    // ── field readers ────────────────────────────────────────────────────────────────────────

    private static boolean hasError(List<FieldError> errors, String... fields) {
        for (FieldError e : errors) {
            for (String f : fields) {
                if (f.equals(e.field())) return true;
            }
        }
        return false;
    }

    private static boolean checkRange(List<FieldError> errors, String field, int value, int min, int max) {
        if (hasError(errors, field)) return false;
        if (value < min || value > max) {
            errors.add(new FieldError(field, "must be between " + min + " and " + max));
            return false;
        }
        return true;
    }

    private static JsonElement present(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        return el == null || el.isJsonNull() ? null : el;
    }

    private static String requiredString(JsonObject obj, String key, List<FieldError> errors) {
        String v = optionalString(obj, key, errors);
        if (v == null && !hasError(errors, key)) {
            errors.add(new FieldError(key, "is required"));
        }
        return v;
    }

    /** Trimmed string, {@code null} when absent or blank. */
    private static String optionalString(JsonObject obj, String key, List<FieldError> errors) {
        JsonElement el = present(obj, key);
        if (el == null) return null;
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            errors.add(new FieldError(key, "must be a string"));
            return null;
        }
        String s = el.getAsString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Boolean optionalBoolean(JsonObject obj, String key, List<FieldError> errors) {
        JsonElement el = present(obj, key);
        if (el == null) return null;
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
            errors.add(new FieldError(key, "must be true or false"));
            return null;
        }
        return el.getAsBoolean();
    }

    /** Integral JSON number within long range. Strings are refused (a text seed is ambiguous). */
    private static Long optionalLong(JsonObject obj, String key, List<FieldError> errors) {
        JsonElement el = present(obj, key);
        if (el == null) return null;
        Long v = integral(el);
        if (v == null) {
            errors.add(new FieldError(key, "must be an integer"));
        }
        return v;
    }

    private static Integer optionalInt(JsonObject obj, String key, List<FieldError> errors) {
        JsonElement el = present(obj, key);
        if (el == null) return null;
        Long v = integral(el);
        if (v == null || v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            errors.add(new FieldError(key, "must be an integer"));
            return null;
        }
        return v.intValue();
    }

    private static Long integral(JsonElement el) {
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) return null;
        try {
            return new BigDecimal(el.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Object> optionalObject(JsonObject obj, String key, List<FieldError> errors) {
        JsonElement el = present(obj, key);
        if (el == null) return new LinkedHashMap<>();
        if (!el.isJsonObject()) {
            errors.add(new FieldError(key, "must be a JSON object"));
            return new LinkedHashMap<>();
        }
        return toMap(el.getAsJsonObject());
    }

    // ── JSON → plain Java ────────────────────────────────────────────────────────────────────

    /** Object → LinkedHashMap (key order kept), recursively. */
    static Map<String, Object> toMap(JsonObject obj) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            out.put(e.getKey(), toJava(e.getValue()));
        }
        return out;
    }

    /**
     * Converts a JSON value to Map / List / String / Boolean / Long / Double / null. Integral numbers
     * become {@link Long} so an int-typed option is not handed over as {@code 3.0}.
     */
    static Object toJava(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonObject()) return toMap(el.getAsJsonObject());
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            List<Object> list = new ArrayList<>(arr.size());
            for (JsonElement item : arr) list.add(toJava(item));
            return list;
        }
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isString()) return p.getAsString();
        Long l = integral(p);
        return l != null ? (Object) l : (Object) p.getAsDouble();
    }
}

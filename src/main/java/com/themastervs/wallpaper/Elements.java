package com.themastervs.wallpaper;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Registro de elementos de la GUI detectados por pantalla y cuales estan desactivados. */
public final class Elements {
    /** Clase de la pantalla que se esta dibujando ahora (null = no rastrear). */
    public static String current;
    /** true mientras dibuja este mod, para no rastrearse a si mismo. */
    public static boolean mute;

    public static final Map<String, Set<String>> SEEN = new HashMap<>();
    public static final Map<String, Set<String>> OFF = new HashMap<>();
    static final Set<String> FOREIGN = new HashSet<>();
    static final Set<String> WIDGET_TEXTS = new HashSet<>();
    static final Path FILE = WallpaperMod.DIR.resolve("elements.json");

    private Elements() {}

    public static boolean active() {
        return current != null && !mute;
    }

    /** Registra el elemento y dice si esta desactivado (hay que cancelar su dibujo). */
    public static boolean blocked(String key) {
        if (!active()) return false;
        Set<String> s = SEEN.computeIfAbsent(current, k -> new HashSet<>());
        if (s.size() < 300) s.add(key);
        Set<String> o = OFF.get(current);
        return o != null && o.contains(key);
    }

    public static boolean blockedText(String t) {
        if (!active() || t == null) return false;
        t = t.strip();
        if (t.isEmpty() || WIDGET_TEXTS.contains(t)) return false;
        if (t.length() > 60) t = t.substring(0, 60);
        return blocked("text:" + t);
    }

    public static boolean isOff(String cls, String key) {
        Set<String> o = OFF.get(cls);
        return o != null && o.contains(key);
    }

    public static void toggle(String cls, String key) {
        Set<String> o = OFF.computeIfAbsent(cls, k -> new HashSet<>());
        if (!o.remove(key)) o.add(key);
        save();
    }

    public static void enableAll(String cls) {
        OFF.remove(cls);
        save();
    }

    /** Desactiva todos los botones detectados que no sean de vanilla. */
    public static void disableForeign(String cls) {
        Set<String> seen = SEEN.get(cls);
        if (seen == null) return;
        Set<String> o = OFF.computeIfAbsent(cls, k -> new HashSet<>());
        for (String k : seen) if (FOREIGN.contains(k)) o.add(k);
        save();
    }

    public static List<String> entries(String cls) {
        Set<String> all = new TreeSet<>((a, b) -> {
            int ra = rank(a), rb = rank(b);
            return ra != rb ? Integer.compare(ra, rb) : a.compareTo(b);
        });
        if (SEEN.containsKey(cls)) all.addAll(SEEN.get(cls));
        if (OFF.containsKey(cls)) all.addAll(OFF.get(cls));
        return new ArrayList<>(all);
    }

    static int rank(String k) {
        if (k.equals("logo")) return 0;
        if (k.equals("splash")) return 1;
        if (k.startsWith("btn:")) return 2;
        if (k.startsWith("tex:")) return 3;
        return 4;
    }

    public static String pretty(String k) {
        if (k.equals("logo")) return "Logo de Minecraft";
        if (k.equals("splash")) return "Splash (texto amarillo)";
        if (k.startsWith("btn:")) return "Boton: " + k.substring(4);
        if (k.startsWith("tex:")) return "Textura: " + k.substring(4).replace("minecraft:textures/", "");
        if (k.startsWith("text:")) return "Texto: " + k.substring(5);
        return k;
    }

    /** Registra los botones de la pantalla y quita los desactivados. */
    public static void scanWidgets(Screen screen, JsonObject c, String cls) {
        List<String> keep = new ArrayList<>();
        if (c.has("title_keep")) c.getAsJsonArray("title_keep").forEach(e -> keep.add(e.getAsString()));
        else { keep.add("menu."); keep.add("narrator.button."); keep.add("title."); }
        WIDGET_TEXTS.clear();
        Set<String> seen = SEEN.computeIfAbsent(cls, k -> new HashSet<>());
        for (AbstractWidget b : new ArrayList<>(Screens.getButtons(screen))) {
            String txt = b.getMessage().getString().strip();
            if (txt.isEmpty()) continue;
            WIDGET_TEXTS.add(txt);
            String key = "btn:" + txt;
            String tk = b.getMessage().getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
            boolean vanilla = false;
            for (String k : keep) if (tk.startsWith(k) || txt.contains(k)) { vanilla = true; break; }
            if (!vanilla) FOREIGN.add(key);
            if (seen.size() < 300) seen.add(key);
            if (isOff(cls, key)) Screens.getButtons(screen).remove(b);
        }
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            for (String cls : o.keySet()) {
                Set<String> s = new HashSet<>();
                o.getAsJsonArray(cls).forEach(e -> s.add(e.getAsString()));
                OFF.put(cls, s);
            }
        } catch (Exception e) {
            WallpaperMod.LOG.error("elements.json ilegible", e);
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, Set<String>> e : OFF.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                JsonArray a = new JsonArray();
                e.getValue().forEach(a::add);
                o.add(e.getKey(), a);
            }
            Files.createDirectories(WallpaperMod.DIR);
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception e) {
            WallpaperMod.LOG.error("No se pudo guardar elements.json", e);
        }
    }
}

package com.themastervs.wallpaper;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class WallpaperMod implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("themastervs");
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("themastervs");
    static final Path CFG = DIR.resolve("config.json");
    static final Map<String, Wallpaper> CACHE = new HashMap<>();
    static final String[] EXT = {".png", ".jpg", ".jpeg", ".bmp", ".gif", ".mp4", ".webm", ".mkv", ".mov", ".avi"};
    static int counter = 0;

    @Override
    public void onInitializeClient() {
        cfg();
        Elements.load();
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            String key = keyOf(screen);
            boolean ours = screen instanceof WallpaperScreen || screen instanceof ElementsScreen;
            String cls = screen.getClass().getName();
            boolean track = !ours && (key != null || Elements.OFF.containsKey(cls));
            if (key != null) get(key);
            if (track) Elements.scanWidgets(screen, cfg(), cls);
            if (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen) {
                Screens.getButtons(screen).add(
                        Button.builder(Component.literal("Fondos"), b -> client.setScreen(new WallpaperScreen(screen)))
                                .bounds(4, 4, 60, 20).build());
            }
            if (key != null) {
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, dt) -> {
                    Wallpaper wp = CACHE.get(key);
                    if (wp == null) return;
                    Elements.mute = true;
                    try { wp.draw(g, s.width, s.height); } finally { Elements.mute = false; }
                });
                ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> {
                    Elements.mute = true;
                    try {
                        Wallpaper wp = CACHE.get(key);
                        if (wp != null && wp.cr >= 0) styleButtons(s, g, wp);
                        Font f = Minecraft.getInstance().font;
                        String t = "themastervs";
                        int y = s.height - (s instanceof TitleScreen ? 22 : 12);
                        g.drawString(f, t, s.width - f.width(t) - 4, y, 0xFFFFFFFF, true);
                    } finally { Elements.mute = false; }
                });
            }
            if (track) {
                ScreenEvents.beforeRender(screen).register((s, g, mx, my, dt) -> Elements.current = cls);
                ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> Elements.current = null);
            }
        });
    }

    static String keyOf(Screen s) {
        if (s instanceof TitleScreen) return "title";
        if (s instanceof JoinMultiplayerScreen) return "multiplayer";
        if (s instanceof ConnectScreen) return "connecting";
        if (s instanceof WallpaperScreen ws) return ws.parentKey;
        if (s instanceof ElementsScreen es) return es.bgKey;
        return null;
    }

    static JsonObject cfg() {
        try {
            Files.createDirectories(DIR);
            if (!Files.exists(CFG)) {
                JsonObject d = new JsonObject();
                d.addProperty("ffmpeg", "ffmpeg");
                d.addProperty("title", "");
                d.addProperty("multiplayer", "");
                d.addProperty("connecting", "");
                d.addProperty("video_width", 1920);
                d.addProperty("video_height", 1080);
                d.addProperty("video_fps", 0);
                d.addProperty("scale_flags", "bicubic");
                JsonArray keep = new JsonArray();
                keep.add("menu.");
                keep.add("narrator.button.");
                keep.add("title.");
                d.add("title_keep", keep);
                Files.writeString(CFG, new GsonBuilder().setPrettyPrinting().create().toJson(d));
            }
            return JsonParser.parseString(Files.readString(CFG)).getAsJsonObject();
        } catch (Exception e) {
            LOG.error("Config ilegible", e);
            return new JsonObject();
        }
    }

    static void save(String key, String val) {
        try {
            JsonObject c = cfg();
            c.addProperty(key, val);
            Files.writeString(CFG, new GsonBuilder().setPrettyPrinting().create().toJson(c));
        } catch (IOException e) {
            LOG.error("No se pudo guardar config", e);
        }
    }

    static List<String> listFiles() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> st = Files.list(DIR)) {
            st.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).sorted().forEach(n -> {
                String l = n.toLowerCase(Locale.ROOT);
                for (String e : EXT) if (l.endsWith(e)) { out.add(n); break; }
            });
        } catch (IOException ignored) {}
        return out;
    }

    static Wallpaper get(String key) {
        JsonObject c = cfg();
        String name = c.has(key) ? c.get(key).getAsString().trim() : "";
        Wallpaper old = CACHE.get(key);
        if (name.isEmpty()) {
            if (old != null) { old.close(); CACHE.remove(key); }
            return null;
        }
        Path p = Path.of(name);
        if (!p.isAbsolute()) p = DIR.resolve(name);
        if (old != null && old.path.equals(p)) return old;
        if (old != null) { old.close(); CACHE.remove(key); }
        try {
            Wallpaper w = new Wallpaper(key, p, c);
            CACHE.put(key, w);
            return w;
        } catch (Exception e) {
            LOG.error("No se pudo cargar el fondo '{}'", p, e);
            return null;
        }
    }

    static void styleButtons(Screen s, GuiGraphics g, Wallpaper wp) {
        int rgb = wp.color();
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        float sat = hsb[1] < 0.12f ? 0f : Math.max(0.4f, Math.min(0.9f, hsb[1]));
        int base = 0xE0000000 | (Color.HSBtoRGB(hsb[0], sat, 0.28f) & 0xFFFFFF);
        int hover = 0xE8000000 | (Color.HSBtoRGB(hsb[0], sat, 0.46f) & 0xFFFFFF);
        int edge = 0xFF000000 | (Color.HSBtoRGB(hsb[0], sat * 0.8f, 0.92f) & 0xFFFFFF);
        int off = 0xE0202020;
        Font font = Minecraft.getInstance().font;
        for (AbstractWidget b : Screens.getButtons(s)) {
            if (!(b instanceof Button) || !b.visible || b.getWidth() < 40) continue;
            int x = b.getX(), y = b.getY(), x2 = x + b.getWidth(), y2 = y + b.getHeight();
            g.fill(x, y, x2, y2, !b.active ? off : (b.isHoveredOrFocused() ? hover : base));
            int e = b.active ? edge : 0xFF505050;
            g.fill(x, y, x2, y + 1, e);
            g.fill(x, y2 - 1, x2, y2, e);
            g.fill(x, y, x + 1, y2, e);
            g.fill(x2 - 1, y, x2, y2, e);
            g.drawCenteredString(font, b.getMessage(), (x + x2) / 2, y + (b.getHeight() - 8) / 2,
                    b.active ? 0xFFFFFFFF : 0xFFA0A0A0);
        }
    }

    /** Seleccion de fondos: clic en una fila = siguiente archivo de la carpeta (o ninguno). */
    static final class WallpaperScreen extends Screen {
        final Screen parent;
        final String parentKey;
        static final String[] KEYS = {"title", "multiplayer", "connecting"};
        static final String[] LABELS = {"Menu principal", "Multijugador", "Conectando"};

        WallpaperScreen(Screen parent) {
            super(Component.literal("Fondos"));
            this.parent = parent;
            String k = keyOf(parent);
            this.parentKey = k == null ? "title" : k;
        }

        Component label(int i) {
            JsonObject c = cfg();
            String v = c.has(KEYS[i]) ? c.get(KEYS[i]).getAsString() : "";
            return Component.literal(LABELS[i] + ": " + (v.isEmpty() ? "ninguno" : v));
        }

        void cycle(int i) {
            List<String> opts = new ArrayList<>();
            opts.add("");
            opts.addAll(listFiles());
            JsonObject c = cfg();
            String cur = c.has(KEYS[i]) ? c.get(KEYS[i]).getAsString() : "";
            int next = (opts.indexOf(cur) + 1) % opts.size();
            save(KEYS[i], opts.get(next));
            get(KEYS[i]);
        }

        @Override
        protected void init() {
            int cx = width / 2;
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                addRenderableWidget(Button.builder(label(i), b -> {
                    cycle(idx);
                    b.setMessage(label(idx));
                }).bounds(cx - 150, height / 2 - 40 + i * 28, 300, 20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Elementos de esta pantalla"),
                    b -> minecraft.setScreen(new ElementsScreen(this, parent.getClass().getName(), parentKey)))
                    .bounds(cx - 150, height / 2 + 44, 300, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Hecho"), b -> onClose())
                    .bounds(cx - 100, height / 2 + 72, 200, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            super.render(g, mx, my, dt);
            g.drawCenteredString(font, Component.literal("Fondos (clic = siguiente archivo)"), width / 2, height / 2 - 70, 0xFFFFFFFF);
            g.drawCenteredString(font, Component.literal("Carpeta: " + DIR), width / 2, height / 2 + 100, 0xFFAAAAAA);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }

    /** Lista de elementos detectados en una pantalla; clic = activar/desactivar. */
    static final class ElementsScreen extends Screen {
        static final int PER = 7;
        final Screen back;
        final String cls, bgKey;
        int page = 0;

        ElementsScreen(Screen back, String cls, String bgKey) {
            super(Component.literal("Elementos"));
            this.back = back;
            this.cls = cls;
            this.bgKey = bgKey;
        }

        Component label(String key) {
            String t = (Elements.isOff(cls, key) ? "[OFF] " : "[ON]  ") + Elements.pretty(key);
            if (t.length() > 46) t = t.substring(0, 45) + "~";
            return Component.literal(t);
        }

        int pages(int n) {
            return Math.max(1, (n + PER - 1) / PER);
        }

        @Override
        protected void init() {
            int cx = width / 2;
            List<String> all = Elements.entries(cls);
            page = Math.max(0, Math.min(page, pages(all.size()) - 1));
            int y0 = 30;
            for (int i = 0; i < PER; i++) {
                int idx = page * PER + i;
                if (idx >= all.size()) break;
                final String key = all.get(idx);
                addRenderableWidget(Button.builder(label(key), b -> {
                    Elements.toggle(cls, key);
                    b.setMessage(label(key));
                }).bounds(cx - 150, y0 + i * 22, 300, 20).build());
            }
            int ny = y0 + PER * 22 + 4;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); })
                    .bounds(cx - 150, ny, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); })
                    .bounds(cx + 110, ny, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Solo vanilla"), b -> {
                Elements.disableForeign(cls);
                rebuildWidgets();
            }).bounds(cx - 150, ny + 24, 98, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Activar todo"), b -> {
                Elements.enableAll(cls);
                rebuildWidgets();
            }).bounds(cx - 49, ny + 24, 98, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Hecho"), b -> onClose())
                    .bounds(cx + 52, ny + 24, 98, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            super.render(g, mx, my, dt);
            List<String> all = Elements.entries(cls);
            g.drawCenteredString(font, Component.literal("Elementos (clic = activar/desactivar, se aplica al volver)"),
                    width / 2, 12, 0xFFFFFFFF);
            int ny = 30 + PER * 22 + 4;
            g.drawCenteredString(font, Component.literal("Pagina " + (page + 1) + "/" + pages(all.size())),
                    width / 2, ny + 6, 0xFFFFFFFF);
            if (all.isEmpty()) {
                g.drawCenteredString(font, Component.literal("Nada detectado aun: vuelve a la pantalla y entra otra vez"),
                        width / 2, 60, 0xFFAAAAAA);
            }
        }

        @Override
        public void onClose() {
            minecraft.setScreen(back);
        }
    }

    /** Un fondo: imagen estatica o video decodificado con ffmpeg externo (triple buffer, sin copias en el hilo de render). */
    static final class Wallpaper {
        final Path path;
        final String ffmpeg, scaleFlags, hwaccel;
        final boolean video;
        final int vw, vh, fpsCap, n;
        int w, h;
        String vf;
        final NativeImage[] imgs;
        final DynamicTexture[] texs;
        final Identifier[] ids;
        final Object lock = new Object();
        int ready = -1, disp = -1;
        boolean hasFrame;
        volatile int avgRgb = 0x303050;
        volatile boolean running, alive = true, failed;
        volatile long lastUsed = System.nanoTime();
        Process proc;
        Thread th;
        volatile float cr = -1;
        float cg, cb;

        Wallpaper(String key, Path path, JsonObject c) throws Exception {
            this.path = path;
            this.ffmpeg = c.has("ffmpeg") ? c.get("ffmpeg").getAsString() : "ffmpeg";
            this.hwaccel = c.has("hwaccel") ? c.get("hwaccel").getAsString() : "auto";
            this.scaleFlags = c.has("scale_flags") ? c.get("scale_flags").getAsString() : "bicubic";
            this.fpsCap = c.has("video_fps") ? c.get("video_fps").getAsInt() : 0;
            vw = Math.max(320, c.has("video_width") ? c.get("video_width").getAsInt() : 1920);
            vh = Math.max(180, c.has("video_height") ? c.get("video_height").getAsInt() : 1080);
            String nm = path.getFileName().toString().toLowerCase(Locale.ROOT);
            video = nm.endsWith(".mp4") || nm.endsWith(".webm") || nm.endsWith(".mkv")
                    || nm.endsWith(".mov") || nm.endsWith(".gif") || nm.endsWith(".avi");
            byte[] still = null;
            if (video) {
                // Nunca reescalar por encima del tamano original del video: la GPU ya lo estira gratis
                // y asi viajan menos datos por frame (misma calidad visible, menos CPU y ancho de banda).
                int[] sz = probe(ffmpeg, path);
                if (sz != null) {
                    double f = Math.min(1.0, Math.min((double) vw / sz[0], (double) vh / sz[1]));
                    w = Math.max(2, (int) Math.round(sz[0] * f));
                    h = Math.max(2, (int) Math.round(sz[1] * f));
                    vf = "scale=" + w + ":" + h + ":flags=" + scaleFlags;
                } else {
                    w = vw; h = vh;
                    vf = "scale=" + vw + ":" + vh + ":force_original_aspect_ratio=increase:flags=" + scaleFlags
                            + ",crop=" + vw + ":" + vh;
                }
                LOG.info("themastervs: video {} -> textura {}x{}", path.getFileName(), w, h);
            } else {
                BufferedImage bi = ImageIO.read(path.toFile());
                if (bi == null) throw new IOException("Formato de imagen no soportado");
                w = bi.getWidth(); h = bi.getHeight();
                int[] px = bi.getRGB(0, 0, w, h, null, 0, w);
                still = new byte[w * h * 4];
                for (int i = 0; i < px.length; i++) {
                    int p = px[i];
                    still[i * 4] = (byte) (p >> 16);
                    still[i * 4 + 1] = (byte) (p >> 8);
                    still[i * 4 + 2] = (byte) p;
                    still[i * 4 + 3] = (byte) 255;
                }
                avgRgb = average(still);
            }
            n = video ? 3 : 1;
            imgs = new NativeImage[n];
            texs = new DynamicTexture[n];
            ids = new Identifier[n];
            for (int i = 0; i < n; i++) {
                imgs[i] = new NativeImage(NativeImage.Format.RGBA, w, h, true);
                final int ii = i;
                texs[i] = new DynamicTexture(() -> "themastervs_" + key + "_" + ii, imgs[i]);
                ids[i] = Identifier.fromNamespaceAndPath("themastervs", "wp_" + key + "_" + (counter++));
                Minecraft.getInstance().getTextureManager().register(ids[i], texs[i]);
            }
            if (still != null) {
                MemoryUtil.memByteBuffer(imgs[0].getPointer(), still.length).put(still);
                ready = 0;
            }
        }

        static String ffprobePath(String ff) {
            String l = ff.toLowerCase(Locale.ROOT);
            if (l.endsWith("ffmpeg.exe")) return ff.substring(0, ff.length() - 10) + "ffprobe.exe";
            if (l.endsWith("ffmpeg")) return ff.substring(0, ff.length() - 6) + "ffprobe";
            return "ffprobe";
        }

        /** Tamano real del video (ya con la rotacion aplicada) o null si no se puede leer. */
        static int[] probe(String ffmpeg, Path path) {
            try {
                Process p = new ProcessBuilder(ffprobePath(ffmpeg), "-v", "error", "-select_streams", "v:0",
                        "-show_entries", "stream=width,height:stream_side_data=rotation:stream_tags=rotate",
                        "-of", "json", path.toString())
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                p.waitFor(5, TimeUnit.SECONDS);
                JsonObject st = JsonParser.parseString(out).getAsJsonObject()
                        .getAsJsonArray("streams").get(0).getAsJsonObject();
                int w = st.get("width").getAsInt(), h = st.get("height").getAsInt();
                int rot = 0;
                if (st.has("side_data_list")) {
                    for (JsonElement e : st.getAsJsonArray("side_data_list")) {
                        JsonObject o = e.getAsJsonObject();
                        if (o.has("rotation")) rot = (int) Math.round(o.get("rotation").getAsDouble());
                    }
                }
                if (rot == 0 && st.has("tags") && st.getAsJsonObject("tags").has("rotate")) {
                    rot = Integer.parseInt(st.getAsJsonObject("tags").get("rotate").getAsString().trim());
                }
                if (Math.abs(rot) % 180 == 90) { int t = w; w = h; h = t; }
                return w > 0 && h > 0 ? new int[]{w, h} : null;
            } catch (Exception e) {
                LOG.warn("ffprobe no disponible; uso {}x{} fijo (pon ffprobe junto a ffmpeg)", "video_width", "video_height");
                return null;
            }
        }

        static int average(byte[] d) {
            long r = 0, g = 0, b = 0, wsum = 0;
            for (int i = 0; i + 3 < d.length; i += 4 * 37) {
                int rr = d[i] & 255, gg = d[i + 1] & 255, bb = d[i + 2] & 255;
                int wt = Math.max(rr, Math.max(gg, bb)) - Math.min(rr, Math.min(gg, bb)) + 16;
                r += (long) rr * wt; g += (long) gg * wt; b += (long) bb * wt; wsum += wt;
            }
            if (wsum == 0) return 0x303050;
            return ((int) (r / wsum) << 16) | ((int) (g / wsum) << 8) | (int) (b / wsum);
        }

        static int average(ByteBuffer d) {
            long r = 0, g = 0, b = 0, wsum = 0;
            int lim = d.limit();
            for (int i = 0; i + 3 < lim; i += 4 * 37) {
                int rr = d.get(i) & 255, gg = d.get(i + 1) & 255, bb = d.get(i + 2) & 255;
                int wt = Math.max(rr, Math.max(gg, bb)) - Math.min(rr, Math.min(gg, bb)) + 16;
                r += (long) rr * wt; g += (long) gg * wt; b += (long) bb * wt; wsum += wt;
            }
            if (wsum == 0) return 0x303050;
            return ((int) (r / wsum) << 16) | ((int) (g / wsum) << 8) | (int) (b / wsum);
        }

        int color() {
            return ((int) cr << 16) | ((int) cg << 8) | (int) cb;
        }

        int freeSlot() {
            for (int i = 0; i < n; i++) if (i != ready && i != disp) return i;
            return 0;
        }

        void draw(GuiGraphics g, int sw, int sh) {
            lastUsed = System.nanoTime();
            if (video && !running && !failed) start();
            int t = avgRgb;
            if (cr < 0) { cg = (t >> 8) & 255; cb = t & 255; cr = (t >> 16) & 255; }
            else {
                cr += (((t >> 16) & 255) - cr) * 0.05f;
                cg += (((t >> 8) & 255) - cg) * 0.05f;
                cb += ((t & 255) - cb) * 0.05f;
            }
            int up = -1;
            synchronized (lock) {
                if (ready >= 0) { disp = ready; ready = -1; up = disp; }
            }
            if (up >= 0) {
                texs[up].upload();
                hasFrame = true;
            }
            if (!hasFrame || disp < 0) return;
            float sa = (float) sw / sh, ia = (float) w / h;
            int rw, rh, u = 0, v = 0;
            if (ia > sa) { rh = h; rw = Math.round(h * sa); u = (w - rw) / 2; }
            else { rw = w; rh = Math.round(w / sa); v = (h - rh) / 2; }
            g.blit(RenderPipelines.GUI_TEXTURED, ids[disp], 0, 0, (float) u, (float) v, sw, sh, rw, rh, w, h);
        }

        void start() {
            running = true;
            ServerSocketChannel srv = null;
            try {
                // ffmpeg -> socket local (mucho mas rapido que la tuberia del proceso, sobre todo en Windows)
                srv = ServerSocketChannel.open();
                srv.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0));
                int port = ((InetSocketAddress) srv.getLocalAddress()).getPort();
                List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-v", "quiet"));
                if (!hwaccel.equalsIgnoreCase("none")) { cmd.add("-hwaccel"); cmd.add(hwaccel); }
                cmd.addAll(List.of("-re", "-stream_loop", "-1", "-i", path.toString(), "-an", "-sn",
                        "-vf", vf));
                if (fpsCap > 0) { cmd.add("-r"); cmd.add(String.valueOf(fpsCap)); }
                cmd.addAll(List.of("-f", "rawvideo", "-pix_fmt", "rgba",
                        "tcp://127.0.0.1:" + port + "?send_buffer_size=8388608"));
                Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                proc = p;
                final ServerSocketChannel server = srv;
                Thread t = new Thread(() -> {
                    SocketChannel ch = null;
                    try {
                        server.configureBlocking(false);
                        long deadline = System.nanoTime() + 10_000_000_000L;
                        while (alive && ch == null && System.nanoTime() < deadline) {
                            ch = server.accept();
                            if (ch == null) Thread.sleep(10);
                        }
                        if (ch == null) {
                            if (alive) {
                                failed = true;
                                LOG.error("ffmpeg no conecto (revisa que el archivo y ffmpeg funcionan): {}", path);
                            }
                            return;
                        }
                        ch.configureBlocking(true);
                        try { ch.setOption(StandardSocketOptions.SO_RCVBUF, 8 * 1024 * 1024); } catch (Exception ignored) {}
                        int len = w * h * 4, cnt = 0, nf = 0;
                        long t0 = System.nanoTime();
                        while (alive && System.nanoTime() - lastUsed < 3_000_000_000L) {
                            int slot;
                            synchronized (lock) { slot = freeSlot(); }
                            ByteBuffer bb = MemoryUtil.memByteBuffer(imgs[slot].getPointer(), len);
                            boolean ok = true;
                            while (bb.hasRemaining()) {
                                if (ch.read(bb) < 0) { ok = false; break; }
                            }
                            if (!ok) break;
                            if (cnt++ % 30 == 0) avgRgb = average(bb);
                            synchronized (lock) { ready = slot; }
                            nf++;
                            long now = System.nanoTime();
                            if (now - t0 >= 5_000_000_000L) {
                                LOG.info("themastervs video: {} fps", Math.round(nf * 1e9 / (now - t0)));
                                nf = 0; t0 = now;
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        try { if (ch != null) ch.close(); } catch (IOException ignored) {}
                        try { server.close(); } catch (IOException ignored) {}
                        p.destroy();
                        running = false;
                    }
                }, "themastervs-video");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY + 2);
                th = t;
                t.start();
            } catch (IOException e) {
                failed = true;
                running = false;
                try { if (srv != null) srv.close(); } catch (IOException ignored) {}
                LOG.error("No se pudo lanzar ffmpeg ('{}'). Ponlo en el PATH o indica la ruta en config.json", ffmpeg);
            }
        }

        void close() {
            alive = false;
            if (proc != null) proc.destroy();
            try { if (th != null) th.join(1500); } catch (InterruptedException ignored) {}
            if (th != null && th.isAlive()) return; // no liberar memoria que aun se escribe
            for (Identifier i : ids) {
                try { Minecraft.getInstance().getTextureManager().release(i); } catch (Exception ignored) {}
            }
        }
    }
}

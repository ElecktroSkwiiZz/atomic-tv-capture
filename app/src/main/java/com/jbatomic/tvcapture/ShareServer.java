/* ATOMIC TV Capture — JB ATOMIC INDUSTRIES — https://jbatomic.com/
 * © 2026 JB Atomic Industries. Product branding and attribution.
 * User screenshots and recordings remain unwatermarked.
 */
package com.jbatomic.tvcapture;

import android.content.Context;
import android.content.Intent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** LAN-only HTTP portal. Four digit PIN, rate-limited login, session cookie, CSRF-protected mutations. */
public final class ShareServer {
    private static final int DEFAULT_PORT = 8766;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static ShareServer singleton;
    private final Context app;
    private final ExecutorService clients = Executors.newFixedThreadPool(6);
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private volatile ServerSocket server;
    private volatile boolean running;
    private volatile int port;
    private volatile String pin;

    private static class Attempt { int failures; long lockedUntil; }
    private static class Session { String csrf = randomHex(20); long expires = System.currentTimeMillis() + 12L * 3600_000; }

    private ShareServer(Context context) {
        app = context.getApplicationContext();
        pin = String.format(Locale.US, "%04d", RANDOM.nextInt(10000));
    }

    public static synchronized ShareServer get(Context context) {
        if (singleton == null) singleton = new ShareServer(context);
        if (!singleton.running) singleton.start();
        return singleton;
    }

    private static String randomHex(int count) {
        byte[] bytes = new byte[count]; RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder(count * 2);
        for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b & 255));
        return out.toString();
    }

    public String getPin() { return pin; }
    public int getPort() { return port; }
    public boolean isRunning() { return running; }
    public String getAddress() { return "http://" + getLocalIp() + ":" + port + "/"; }

    public synchronized void regeneratePin() {
        pin = String.format(Locale.US, "%04d", RANDOM.nextInt(10000));
        sessions.clear(); attempts.clear();
    }

    public synchronized void changePort(int desired) {
        if (desired < 1024 || desired > 65535) return;
        app.getSharedPreferences("tv_capture", Context.MODE_PRIVATE).edit().putInt("server_port", desired).apply();
        stop(); start();
    }

    private synchronized void stop() {
        running = false;
        ServerSocket old = server;
        server = null;
        if (old != null) try { old.close(); } catch (IOException ignored) { }
    }

    private synchronized void start() {
        if (running) return;
        int requested = app.getSharedPreferences("tv_capture", Context.MODE_PRIVATE)
            .getInt("server_port", DEFAULT_PORT);
        for (int offset = 0; offset <= 24 && requested + offset <= 65535; offset++) {
            int candidate = requested + offset;
            try {
                ServerSocket socket = new ServerSocket();
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(candidate), 16);
                server = socket;
                port = candidate;
                running = true;
                Thread worker = new Thread(() -> acceptLoop(socket), "tv-capture-lan");
                worker.setDaemon(true); worker.start();
                return;
            } catch (IOException ignored) { }
        }
        running = false;
        port = requested;
    }

    private void acceptLoop(ServerSocket socket) {
        while (running && socket == server) {
            try {
                Socket incoming = socket.accept();
                incoming.setSoTimeout(15000);
                try { clients.execute(() -> handle(incoming)); }
                catch (RuntimeException ex) { incoming.close(); }
            } catch (IOException ignored) { break; }
        }
        if (socket == server) running = false;
    }

    private void handle(Socket client) {
        try (Socket socket = client) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            String requestLine = line(in, 8192);
            if (requestLine == null) return;
            String[] first = requestLine.split(" ");
            if (first.length != 3 || !(first[0].equals("GET") || first[0].equals("POST"))) {
                sendText(out, 405, "text/plain; charset=utf-8", "Méthode non autorisée", null); return;
            }
            boolean isPost = first[0].equals("POST");
            String target = first[1];
            if (!target.startsWith("/") || target.length() > 4096) return;
            String path = target.split("\\?", 2)[0];
            String rawQuery = target.contains("?") ? target.substring(target.indexOf('?') + 1) : "";
            String cookie = "";
            int size = 0;
            for (int i = 0; i < 60; i++) {
                String h = line(in, 8192);
                if (h == null || h.isEmpty()) break;
                int colon = h.indexOf(':'); if (colon < 0) continue;
                String name = h.substring(0, colon).trim().toLowerCase(Locale.US);
                String value = h.substring(colon + 1).trim();
                if (name.equals("cookie")) cookie = value;
                if (name.equals("content-length")) try { size = Integer.parseInt(value); } catch (Exception ignored) { size = -1; }
                if (name.equals("transfer-encoding")) { sendText(out, 400, "text/plain", "Corps non pris en charge", null); return; }
            }
            if (size < 0 || size > 8192) { sendText(out, 413, "text/plain", "Requête trop volumineuse", null); return; }
            byte[] body = new byte[size];
            for (int off = 0; off < size;) {
                int n = in.read(body, off, size - off);
                if (n < 0) return;
                off += n;
            }
            Map<String, String> params = query(isPost ? new String(body, StandardCharsets.UTF_8) : rawQuery);
            String ip = socket.getInetAddress().getHostAddress();
            if (path.equals("/brand/logo.webp") && !isPost) {
                sendBrandAsset(out, R.drawable.jb_atomic_company); return;
            }
            if (path.equals("/brand/app.webp") && !isPost) {
                sendBrandAsset(out, R.drawable.atomic_app_icon); return;
            }
            if (path.equals("/login") && isPost) {
                login(out, ip, params.getOrDefault("code", "")); return;
            }
            Session session = sessionFor(cookie);
            if (session == null) {
                if (path.equals("/")) sendText(out, 200, "text/html; charset=utf-8", shell("Connexion", loginPage(ip)), null);
                else sendText(out, 401, "text/plain; charset=utf-8", "Non authentifié", null);
                return;
            }
            if (path.equals("/") && !isPost) {
                sendText(out, 200, "text/html; charset=utf-8", shell("Mes captures", gallery(session, params.get("notice"))), null);
            } else if (path.equals("/zip") && !isPost) {
                sendArchive(out);
            } else if (path.startsWith("/file/") && !isPost) {
                String name = URLDecoder.decode(path.substring(6), "UTF-8");
                File file = CaptureStore.resolve(app, name);
                if (file == null || !file.exists()) sendText(out, 404, "text/plain", "Fichier introuvable", null);
                else sendFile(out, file);
            } else if (path.equals("/action") && isPost) {
                if (!session.csrf.equals(params.get("csrf"))) { sendText(out, 403, "text/plain", "Requête refusée", null); return; }
                String action = params.getOrDefault("do", "");
                String notice;
                switch (action) {
                    case "screenshot":
                        notice = RemoteAccessibilityService.requestScreenshot() ? "Capture demandée sur la TV" : "Activez l'accessibilité sur la TV";
                        break;
                    case "record-start":
                        if (RecordingService.isRecording()) { notice = "Vidéo déjà active"; break; }
                        Intent launch = new Intent(app, MainActivity.class)
                            .setAction(MainActivity.ACTION_REQUEST_RECORD)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        try { app.startActivity(launch); notice = "Autorisez la capture sur votre TV"; }
                        catch (Exception e) { notice = "Ouvrez ATOMIC TV Capture sur votre TV"; }
                        break;
                    case "record-stop":
                        if (!RecordingService.isRecording()) { notice = "Pas de vidéo en cours"; break; }
                        app.startService(new Intent(app, RecordingService.class).setAction(RecordingService.ACTION_STOP));
                        notice = "Arrêt de la vidéo demandé"; break;
                    case "delete-one":
                        if (RecordingService.isRecording()) { notice = "Arrêtez la vidéo avant suppression"; break; }
                        notice = CaptureStore.delete(app, params.get("name")) ? "Fichier supprimé" : "Suppression impossible"; break;
                    case "delete-all":
                        if (RecordingService.isRecording()) { notice = "Arrêtez la vidéo avant suppression"; break; }
                        if (!"SUPPRIMER".equals(params.get("confirm"))) { notice = "Confirmation manquante"; break; }
                        notice = CaptureStore.deleteAll(app) + " fichier(s) supprimé(s)"; break;
                    case "logout":
                        sessions.entrySet().removeIf(e -> e.getValue() == session);
                        redirect(out, "/", "sid=; Max-Age=0; HttpOnly; SameSite=Strict; Path=/"); return;
                    default: notice = "Action inconnue";
                }
                redirect(out, "/?notice=" + URLEncoder.encode(notice, "UTF-8"), null);
            } else {
                sendText(out, 404, "text/plain", "Page inexistante", null);
            }
        } catch (Exception ignored) { }
    }

    private static String line(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') { if (buff.size() >= limit) throw new IOException("Ligne trop longue"); buff.write(c); }
        }
        if (c == -1 && buff.size() == 0) return null;
        return buff.toString("ISO-8859-1");
    }

    private static Map<String, String> query(String value) {
        Map<String, String> map = new java.util.HashMap<>();
        if (value == null || value.length() > 8192) return map;
        for (String p : value.split("&")) {
            int i = p.indexOf('=');
            if (i < 0) continue;
            try { map.put(URLDecoder.decode(p.substring(0, i), "UTF-8"), URLDecoder.decode(p.substring(i + 1), "UTF-8")); }
            catch (Exception ignored) { }
        }
        return map;
    }

    private Session sessionFor(String cookie) {
        for (String part : cookie.split(";")) {
            String item = part.trim();
            if (item.startsWith("sid=")) {
                Session s = sessions.get(item.substring(4));
                if (s != null && s.expires > System.currentTimeMillis()) return s;
            }
        }
        return null;
    }

    private void login(OutputStream out, String ip, String attempt) throws IOException {
        Attempt state = attempts.computeIfAbsent(ip, k -> new Attempt());
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (now < state.lockedUntil) {
                sendText(out, 429, "text/html; charset=utf-8", shell("Connexion bloquée", "<div class='panel'><h2>Accès temporairement verrouillé</h2><p>Nouvel essai dans 10 minutes.</p></div>"), null); return;
            }
            if (!attempt.matches("[0-9]{4}") || !pin.equals(attempt)) {
                state.failures++;
                if (state.failures >= 5) { state.lockedUntil = now + 10 * 60_000; state.failures = 0; }
                sendText(out, 403, "text/html; charset=utf-8", shell("Code incorrect", loginPage(ip)), null); return;
            }
            state.failures = 0;
        }
        String sid = randomHex(32);
        sessions.put(sid, new Session());
        redirect(out, "/", "sid=" + sid + "; Max-Age=43200; HttpOnly; SameSite=Strict; Path=/");
    }

    private String loginPage(String ip) {
        return "<div class='panel login'><span class='eyebrow'>CONNEXION LOCALE</span><h2>Accéder à votre TV</h2>" +
            "<p>Entrez le code à quatre chiffres visible sur la télévision. Après cinq échecs, l'accès est bloqué pendant dix minutes.</p>" +
            "<form method='post' action='/login'><input type='password' name='code' inputmode='numeric' autocomplete='off' maxlength='4' pattern='[0-9]{4}' placeholder='••••' autofocus required>" +
            "<button class='btn primary' type='submit'>Se connecter</button></form><p class='hint'>Connexion limitée à votre réseau local. Ne rendez pas ce port accessible depuis Internet.</p></div>";
    }

    private String gallery(Session session, String notice) {
        File[] files = CaptureStore.list(app);
        String csrf = session.csrf;
        StringBuilder html = new StringBuilder(16384);
        html.append("<div class='heading'><div><span class='eyebrow'>TABLEAU DE BORD</span><h1>Bibliothèque & commandes</h1><p>")
            .append(files.length).append(" fichier(s) · ").append(humanSize(CaptureStore.totalBytes(app))).append(" sur la TV</p></div>")
            .append(form(csrf, "logout", "Déconnexion", "btn ghost", "", "")).append("</div>");
        if (notice != null && !notice.isEmpty()) html.append("<div class='notice'>").append(escape(notice)).append("</div>");
        html.append("<section class='panel'><h2>Capturer à distance</h2><p>La capture d'écran nécessite le service d'accessibilité. La vidéo demande une autorisation sur la TV.</p><div class='actions'>")
            .append(form(csrf, "screenshot", "📷 Capture d'écran", "btn primary", "", ""))
            .append(form(csrf, "record-start", "● Démarrer vidéo", "btn", "", ""))
            .append(form(csrf, "record-stop", "■ Arrêter vidéo", "btn", "", ""))
            .append("</div></section>");
        html.append("<section class='panel'><div class='heading'><div><h2>Fichiers</h2><p>Enregistrez une copie sur votre PC avant de supprimer les originaux.</p></div><div class='actions'>")
            .append("<a class='btn primary' href='/zip'>⬇ Tout télécharger (.zip)</a>")
            .append("<a class='btn ghost' href='/'>Actualiser</a>")
            .append(form(csrf, "delete-all", "Tout effacer", "btn danger", "confirm", "SUPPRIMER",
                 "Supprimer définitivement toutes les images et vidéos de la TV ?"))
            .append("</div></div><div class='grid'>");
        for (File file : files) {
            String name = file.getName();
            String uri = "/file/" + url(name);
            boolean video = name.endsWith(".mp4");
            html.append("<article class='item'>");
            if (video) html.append("<div class='preview video'>▶ Vidéo MP4</div>");
            else html.append("<a href='").append(uri).append("' download><img loading='lazy' src='").append(uri).append("' alt='Capture'></a>");
            html.append("<div class='item-title'>").append(escape(name)).append("</div><div class='meta'>")
                .append(humanSize(file.length())).append(" · ")
                .append(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(new Date(file.lastModified())))
                .append("</div><div class='item-actions'><a class='btn small' href='").append(uri).append("' download>Télécharger</a>")
                .append(form(csrf, "delete-one", "Supprimer", "btn danger small", "name", name,
                    "Supprimer définitivement ce fichier ?"))
                .append("</div></article>");
        }
        if (files.length == 0) html.append("<div class='empty'>Aucune capture enregistrée pour le moment.</div>");
        html.append("</div></section>");
        return html.toString();
    }

    private static String form(String csrf, String action, String title, String cls, String key, String value) {
        return form(csrf, action, title, cls, key, value, "");
    }
    private static String form(String csrf, String action, String title, String cls, String key, String value, String confirm) {
        String onsubmit = confirm.isEmpty() ? "" : " onsubmit=\"return confirm('" + escape(confirm).replace("'", "&#39;") + "')\"";
        return "<form method='post' action='/action'" + onsubmit + "><input type='hidden' name='csrf' value='" + escape(csrf) + "'>" +
            "<input type='hidden' name='do' value='" + escape(action) + "'>" +
            (key.isEmpty() ? "" : "<input type='hidden' name='" + escape(key) + "' value='" + escape(value) + "'>") +
            "<button class='" + cls + "' type='submit'>" + escape(title) + "</button></form>";
    }

    private static String shell(String title, String body) {
        return "<!doctype html><html lang='fr'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<meta name='referrer' content='no-referrer'><title>ATOMIC TV Capture · " + escape(title) + "</title><meta name='theme-color' content='#0b1120'><link rel='icon' type='image/webp' href='/brand/app.webp'><style>" +
            ":root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#0b1120;color:#edf6ff;font-family:system-ui,-apple-system,Segoe UI,sans-serif;font-size:14px}" +
            "header{border-bottom:1px solid #273751;background:#101a2a;padding:18px max(20px,calc((100vw - 1240px)/2))}" +
            ".brand{display:flex;align-items:center;gap:12px;flex-wrap:wrap;font-weight:800;font-size:19px;letter-spacing:.3px}.brand img{width:154px;height:46px;object-fit:contain}.brand img.app-mark{width:40px;height:40px;border-radius:9px}.brand span{color:#50c7e7}.brand .brand-caption{border-left:1px solid #315579;padding-left:14px}main{max-width:1240px;margin:30px auto;padding:0 20px}" +
            ".eyebrow{font-size:11px;font-weight:800;color:#66d7ed;letter-spacing:1.4px}h1{font-size:25px;margin:4px 0 5px}h2{font-size:18px;margin:0 0 7px}p{color:#a8bcd1;line-height:1.6;margin:5px 0 17px}" +
            ".panel{position:relative;isolation:isolate;overflow:hidden;border:1px solid #2b4059;background:#162235;padding:22px;border-radius:14px;margin-bottom:18px}.panel::before{content:'';position:absolute;right:14px;bottom:12px;width:180px;height:58px;background:url('/brand/logo.webp') center / contain no-repeat;opacity:.07;z-index:0;pointer-events:none}.panel>*{position:relative;z-index:1}.heading,.actions,.item-actions{display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap}.actions{justify-content:flex-start}" +
            ".btn{border:1px solid #355069;border-radius:8px;background:#253851;color:#e8f4ff;padding:10px 14px;text-decoration:none;cursor:pointer;font-size:13px;font-weight:700;white-space:nowrap}" +
            ".btn:hover{background:#395676}.btn.primary{background:#158cb3;border-color:#158cb3;color:white}.btn.danger{background:transparent;border-color:#874a52;color:#ffbcbf}" +
            ".btn.ghost{background:transparent}.btn.small{padding:8px 10px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(215px,1fr));gap:15px;margin-top:16px}" +
            ".item{padding:10px;border-radius:12px;border:1px solid #30445e;background:#101b2c;min-width:0}.item img,.preview{width:100%;height:135px;object-fit:cover;border-radius:8px;background:#273951}" +
            ".preview{display:grid;place-items:center;color:#7dd8ee;font-weight:700}.item-title{font-weight:700;font-size:12px;margin-top:10px;overflow-wrap:anywhere}" +
            ".meta{font-size:12px;color:#a3b8cf;margin:4px 0 13px}.item-actions{justify-content:flex-start;gap:6px}.item-actions form{margin:0}form{display:inline-block}" +
            ".login{max-width:440px;margin:65px auto}.login input{background:#edf8ff;color:#0b1425;border:0;border-radius:7px;font-size:22px;width:150px;padding:8px 12px;margin-right:10px}" +
            ".hint{margin-top:24px;font-size:12px}.notice{padding:12px;border-radius:8px;background:#183e45;margin-bottom:16px;color:#aef5e6}.empty{padding:35px;color:#a6b9d0}" +
            "footer{text-align:center;color:#8196b2;font-size:12px;padding:12px 20px 35px}@media(max-width:600px){h1{font-size:22px}.panel{padding:15px}}" +
            "</style></head><body><header><div class='brand'><img class='app-mark' src='/brand/app.webp' alt='ATOMIC TV Capture'><img src='/brand/logo.webp' alt='JB Atomic Industries'><div class='brand-caption'>ATOMIC <span>TV CAPTURE</span><small style='display:block;color:#8caac2;font-size:11px'>BY JB ATOMIC INDUSTRIES · v" + BuildConfig.VERSION_NAME + "</small></div></div></header>" +
            "<main>" + body + "</main><footer>© 2026 JB ATOMIC INDUSTRIES · <a style='color:#66d7ed' href='https://jbatomic.com/' target='_blank' rel='noopener noreferrer'>jbatomic.com</a> · ATOMIC TV Capture v" + BuildConfig.VERSION_NAME + " · Accès local</footer></body></html>";
    }

    /** Public branding asset only — never exposes screenshots or user content. */
    private void sendBrandAsset(OutputStream out, int drawableId) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream(65536);
        try (InputStream in = app.getResources().openRawResource(drawableId)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (data.size() + n > 256_000) throw new IOException("Logo trop volumineux");
                data.write(buffer, 0, n);
            }
        }
        byte[] image = data.toByteArray();
        String header = "HTTP/1.1 200 OK\r\nContent-Type: image/webp\r\nContent-Length: " + image.length +
            "\r\nCache-Control: public, max-age=3600\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(image);
        out.flush();
    }

    private void sendArchive(OutputStream out) throws IOException {
        String headers = "HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Disposition: attachment; filename=\"ATOMIC-TV-Capture-Export.zip\"\r\n" +
            "Transfer-Encoding: chunked\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        ChunkedOutputStream stream = new ChunkedOutputStream(out);
        // Stream ZIP directly: no temporary file, no copy of the whole gallery, bounded memory use.
        try (ZipOutputStream archive = new ZipOutputStream(stream)) {
            byte[] buffer = new byte[65536];
            for (File file : CaptureStore.list(app)) {
                if (!file.exists()) continue;
                archive.putNextEntry(new ZipEntry(file.getName()));
                try (FileInputStream input = new FileInputStream(file)) {
                    int n; while ((n = input.read(buffer)) != -1) archive.write(buffer, 0, n);
                }
                archive.closeEntry();
            }
            archive.finish();
        } finally { stream.finish(); }
    }

    private static final class ChunkedOutputStream extends OutputStream {
        private final OutputStream out;
        private boolean finished;
        ChunkedOutputStream(OutputStream out) { this.out = out; }
        @Override public void write(int b) throws IOException { write(new byte[] {(byte) b}, 0, 1); }
        @Override public void write(byte[] data, int offset, int length) throws IOException {
            if (finished) throw new IOException("Flux déjà fermé");
            if (length == 0) return;
            out.write((Integer.toHexString(length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(data, offset, length);
            out.write(new byte[] {'\r','\n'});
        }
        @Override public void flush() throws IOException { out.flush(); }
        @Override public void close() throws IOException { finish(); }
        void finish() throws IOException {
            if (!finished) { finished = true; out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); out.flush(); }
        }
    }

    private static void sendFile(OutputStream out, File file) throws IOException {
        String type = file.getName().endsWith(".mp4") ? "video/mp4" : "image/png";
        String disposition = type.equals("image/png") ? "inline" : "attachment";
        String header = "HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + file.length() +
            "\r\nContent-Disposition: " + disposition + "; filename=\"" + file.getName() + "\"\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n; while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        out.flush();
    }

    private static void redirect(OutputStream out, String path, String cookie) throws IOException {
        String message = "HTTP/1.1 303 See Other\r\nLocation: " + path + "\r\nCache-Control: no-store\r\n" +
            (cookie == null ? "" : "Set-Cookie: " + cookie + "\r\n") + "Content-Length: 0\r\nConnection: close\r\n\r\n";
        out.write(message.getBytes(StandardCharsets.US_ASCII)); out.flush();
    }

    private static void sendText(OutputStream out, int status, String type, String text, String cookie) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String description = status == 200 ? "OK" : status == 401 ? "Unauthorized" : status == 403 ? "Forbidden" : status == 404 ? "Not Found" : status == 405 ? "Method Not Allowed" : status == 429 ? "Too Many Requests" : "Error";
        String headers = "HTTP/1.1 " + status + " " + description + "\r\nContent-Type: " + type + "\r\nContent-Length: " + bytes.length +
            "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer\r\n" +
            "Content-Security-Policy: default-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'; img-src 'self' data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'\r\nConnection: close\r\n" +
            (cookie == null ? "" : "Set-Cookie: " + cookie + "\r\n") + "\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII)); out.write(bytes); out.flush();
    }

    private static String url(String text) {
        try { return URLEncoder.encode(text, "UTF-8").replace("+", "%20"); }
        catch (Exception ignored) { return text; }
    }
    private static String escape(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " o";
        if (bytes < 1048576) return String.format(Locale.FRANCE, "%.1f Ko", bytes / 1024.0);
        if (bytes < 1073741824) return String.format(Locale.FRANCE, "%.1f Mo", bytes / 1048576.0);
        return String.format(Locale.FRANCE, "%.2f Go", bytes / 1073741824.0);
    }
    public static String getLocalIp() {
        try {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            while (networks != null && networks.hasMoreElements()) {
                NetworkInterface n = networks.nextElement();
                if (!n.isUp() || n.isLoopback()) continue;
                Enumeration<InetAddress> addresses = n.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress() && !address.isLoopbackAddress()) {
                        if (n.getName().startsWith("wlan") || n.getName().startsWith("eth")) return address.getHostAddress();
                        if (fallback == null) fallback = address.getHostAddress();
                    }
                }
            }
            return fallback == null ? "IP INDISPONIBLE" : fallback;
        } catch (Exception ignored) { return "IP INDISPONIBLE"; }
    }
}

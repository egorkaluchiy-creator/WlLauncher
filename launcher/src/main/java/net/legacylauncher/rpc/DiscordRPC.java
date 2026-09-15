package net.legacylauncher.rpc;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.OS;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DiscordRPC {
    public static final String CLIENT_ID = "1215354921503195196";
    private static final DiscordRPC INSTANCE = new DiscordRPC();

    public static final long INITIAL_RETRY_DELAY_MS = 5000;
    public static final long MAX_RETRY_DELAY_MS = 120000;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "WlLauncher-DiscordRPC");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private RandomAccessFile pipe;
    private long launcherStartTime;
    private long gameStartTime;
    private boolean inGame = false;
    private String currentGameVersion = "";
    private String currentPlayerName = "";

    private long currentRetryDelayMs = INITIAL_RETRY_DELAY_MS;
    private long nextAllowedConnectTimeMs = 0;

    public static DiscordRPC getInstance() {
        return INSTANCE;
    }

    DiscordRPC() {
    }

    public synchronized void init() {
        if (running.getAndSet(true)) {
            return;
        }
        launcherStartTime = System.currentTimeMillis();
        executor.scheduleWithFixedDelay(this::tick, 1, 5, TimeUnit.SECONDS);
    }

    public synchronized void setInLauncher() {
        this.inGame = false;
        this.currentGameVersion = "";
        this.currentPlayerName = "";
        executor.execute(() -> {
            if (ensureConnected()) {
                sendCurrentPresence();
            }
        });
    }

    public synchronized void setInGame(String version, String playerName) {
        this.inGame = true;
        this.gameStartTime = System.currentTimeMillis();
        this.currentGameVersion = version != null ? version : "Minecraft";
        this.currentPlayerName = playerName != null ? playerName : "";
        executor.execute(() -> {
            if (ensureConnected()) {
                sendCurrentPresence();
            }
        });
    }

    public synchronized void shutdown() {
        running.set(false);
        executor.execute(() -> {
            closePipe();
            executor.shutdown();
        });
    }

    public boolean isConnected() {
        return connected.get() && pipe != null;
    }

    public boolean isRunning() {
        return running.get();
    }

    private void tick() {
        if (!running.get()) return;

        if (!connected.get() || pipe == null) {
            long now = System.currentTimeMillis();
            if (now >= nextAllowedConnectTimeMs) {
                if (tryConnect()) {
                    sendCurrentPresence();
                }
            }
        }
    }

    private boolean ensureConnected() {
        if (connected.get() && pipe != null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now >= nextAllowedConnectTimeMs) {
            return tryConnect();
        }
        return false;
    }

    private synchronized boolean tryConnect() {
        closePipe();
        for (int i = 0; i < 10; i++) {
            try {
                String pipePath = getPipePath(i);
                File file = new File(pipePath);
                if (OS.WINDOWS.isCurrent() || file.exists()) {
                    pipe = new RandomAccessFile(pipePath, "rw");
                    if (sendHandshake()) {
                        connected.set(true);
                        currentRetryDelayMs = INITIAL_RETRY_DELAY_MS;
                        nextAllowedConnectTimeMs = 0;
                        log.info("Connected to Discord IPC on pipe {}", i);
                        return true;
                    }
                }
            } catch (Exception ignored) {
                closePipe();
            }
        }

        // Connection failed - apply exponential backoff with full jitter
        currentRetryDelayMs = calculateNextBackoff(currentRetryDelayMs);
        nextAllowedConnectTimeMs = System.currentTimeMillis() + currentRetryDelayMs;
        log.debug("Discord not available, next retry in {} ms", currentRetryDelayMs);
        return false;
    }

    public static long calculateNextBackoff(long currentDelay) {
        double multiplier = 1.5;
        double jitter = Math.random() * 2000;
        long next = (long) (currentDelay * multiplier + jitter);
        return Math.min(MAX_RETRY_DELAY_MS, Math.max(INITIAL_RETRY_DELAY_MS, next));
    }

    public static String getPipePath(int index) {
        if (OS.WINDOWS.isCurrent()) {
            return "\\\\.\\pipe\\discord-ipc-" + index;
        }
        String xdg = System.getenv("XDG_RUNTIME_DIR");
        if (xdg != null && !xdg.isEmpty()) {
            return xdg + "/discord-ipc-" + index;
        }
        String tmp = System.getenv("TMPDIR");
        if (tmp != null && !tmp.isEmpty()) {
            return tmp + "/discord-ipc-" + index;
        }
        return "/tmp/discord-ipc-" + index;
    }

    private boolean sendHandshake() {
        try {
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", CLIENT_ID);
            writeFrame(0, handshake.toString());
            return true;
        } catch (Exception e) {
            log.debug("Failed to send Discord handshake: {}", e.getMessage());
            return false;
        }
    }

    public JsonObject buildPresencePayload(boolean inGame, String version, String player, long startTimestamp, long pid) {
        JsonObject activity = new JsonObject();
        JsonObject timestamps = new JsonObject();
        JsonObject assets = new JsonObject();

        if (inGame) {
            String ver = (version != null && !version.isEmpty()) ? version : "Minecraft";
            activity.addProperty("details", "Играет в " + ver);
            activity.addProperty("state", (player == null || player.isEmpty()) ? "В игре" : "Игрок: " + player);
            timestamps.addProperty("start", startTimestamp / 1000L);
            assets.addProperty("large_image", "logo");
            assets.addProperty("large_text", "Minecraft " + ver);
            assets.addProperty("small_image", "wllauncher");
            assets.addProperty("small_text", "WlLauncher");
        } else {
            activity.addProperty("details", "WlLauncher");
            activity.addProperty("state", "В лаунчере");
            timestamps.addProperty("start", startTimestamp / 1000L);
            assets.addProperty("large_image", "logo");
            assets.addProperty("large_text", "WlLauncher — Быстрый лаунчер Minecraft");
        }

        activity.add("timestamps", timestamps);
        activity.add("assets", assets);

        JsonObject args = new JsonObject();
        args.addProperty("pid", pid);
        args.add("activity", activity);

        JsonObject payload = new JsonObject();
        payload.addProperty("cmd", "SET_ACTIVITY");
        payload.add("args", args);
        payload.addProperty("nonce", UUID.randomUUID().toString());
        return payload;
    }

    private void sendCurrentPresence() {
        if (!connected.get() || pipe == null) {
            return;
        }

        try {
            long startTime = inGame ? gameStartTime : launcherStartTime;
            JsonObject payload = buildPresencePayload(inGame, currentGameVersion, currentPlayerName, startTime, getProcessId());
            writeFrame(1, payload.toString());
        } catch (Exception e) {
            log.debug("Failed to send presence frame: {}", e.getMessage());
            connected.set(false);
            closePipe();
        }
    }

    private void writeFrame(int opcode, String json) throws Exception {
        if (pipe == null) return;
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        pipe.write(buffer.array());
    }

    private void closePipe() {
        if (pipe != null) {
            try {
                pipe.close();
            } catch (Exception ignored) {
            }
            pipe = null;
        }
        connected.set(false);
    }

    private long getProcessId() {
        try {
            String jvmName = ManagementFactory.getRuntimeMXBean().getName();
            int index = jvmName.indexOf('@');
            if (index > 0) {
                return Long.parseLong(jvmName.substring(0, index));
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }
}


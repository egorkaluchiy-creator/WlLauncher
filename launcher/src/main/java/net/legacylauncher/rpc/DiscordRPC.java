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
    private static final String CLIENT_ID = "1215354921503195196";
    private static final DiscordRPC INSTANCE = new DiscordRPC();

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

    public static DiscordRPC getInstance() {
        return INSTANCE;
    }

    private DiscordRPC() {
    }

    public synchronized void init() {
        if (running.getAndSet(true)) {
            return;
        }
        launcherStartTime = System.currentTimeMillis();
        executor.scheduleWithFixedDelay(this::connectAndSync, 0, 10, TimeUnit.SECONDS);
    }

    public synchronized void setInLauncher() {
        this.inGame = false;
        this.currentGameVersion = "";
        this.currentPlayerName = "";
        executor.execute(this::sendCurrentPresence);
    }

    public synchronized void setInGame(String version, String playerName) {
        this.inGame = true;
        this.gameStartTime = System.currentTimeMillis();
        this.currentGameVersion = version != null ? version : "Minecraft";
        this.currentPlayerName = playerName != null ? playerName : "";
        executor.execute(this::sendCurrentPresence);
    }

    public synchronized void shutdown() {
        running.set(false);
        executor.execute(() -> {
            closePipe();
            executor.shutdown();
        });
    }

    private void connectAndSync() {
        if (!running.get()) return;

        if (!connected.get() || pipe == null) {
            if (tryConnect()) {
                sendCurrentPresence();
            }
        }
    }

    private boolean tryConnect() {
        closePipe();
        for (int i = 0; i < 10; i++) {
            try {
                String pipePath = getPipePath(i);
                File file = new File(pipePath);
                if (OS.WINDOWS.isCurrent() || file.exists()) {
                    pipe = new RandomAccessFile(pipePath, "rw");
                    if (sendHandshake()) {
                        connected.set(true);
                        log.info("Connected to Discord IPC on pipe {}", i);
                        return true;
                    }
                }
            } catch (Exception ignored) {
                closePipe();
            }
        }
        return false;
    }

    private String getPipePath(int index) {
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

    private void sendCurrentPresence() {
        if (!connected.get() || pipe == null) {
            return;
        }

        try {
            JsonObject activity = new JsonObject();
            JsonObject timestamps = new JsonObject();
            JsonObject assets = new JsonObject();

            if (inGame) {
                activity.addProperty("details", "Играет в " + currentGameVersion);
                activity.addProperty("state", (currentPlayerName.isEmpty() ? "В игре" : "Игрок: " + currentPlayerName));
                timestamps.addProperty("start", gameStartTime / 1000L);
                assets.addProperty("large_image", "logo");
                assets.addProperty("large_text", "Minecraft " + currentGameVersion);
                assets.addProperty("small_image", "wllauncher");
                assets.addProperty("small_text", "WlLauncher");
            } else {
                activity.addProperty("details", "WlLauncher");
                activity.addProperty("state", "В лаунчере");
                timestamps.addProperty("start", launcherStartTime / 1000L);
                assets.addProperty("large_image", "logo");
                assets.addProperty("large_text", "WlLauncher — Быстрый лаунчер Minecraft");
            }

            activity.add("timestamps", timestamps);
            activity.add("assets", assets);

            JsonObject args = new JsonObject();
            args.addProperty("pid", getProcessId());
            args.add("activity", activity);

            JsonObject payload = new JsonObject();
            payload.addProperty("cmd", "SET_ACTIVITY");
            payload.add("args", args);
            payload.addProperty("nonce", UUID.randomUUID().toString());

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

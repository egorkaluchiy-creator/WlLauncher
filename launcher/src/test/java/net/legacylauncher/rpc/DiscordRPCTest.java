package net.legacylauncher.rpc;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscordRPCTest {

    @Test
    void testCalculateNextBackoff() {
        long initial = DiscordRPC.INITIAL_RETRY_DELAY_MS;
        long next = DiscordRPC.calculateNextBackoff(initial);
        assertTrue(next >= (long)(initial * 1.5), "Next backoff should grow exponentially");
        assertTrue(next <= DiscordRPC.MAX_RETRY_DELAY_MS, "Next backoff should not exceed max");

        long capped = DiscordRPC.calculateNextBackoff(DiscordRPC.MAX_RETRY_DELAY_MS);
        assertEquals(DiscordRPC.MAX_RETRY_DELAY_MS, capped, "Capped at MAX_RETRY_DELAY_MS");
    }

    @Test
    void testConnectionRetryWithBackoff() {
        long delay = DiscordRPC.INITIAL_RETRY_DELAY_MS;
        for (int i = 0; i < 10; i++) {
            long prevDelay = delay;
            delay = DiscordRPC.calculateNextBackoff(delay);
            assertTrue(delay >= prevDelay || delay == DiscordRPC.MAX_RETRY_DELAY_MS,
                    "Delay should monotonically increase until max cap");
            assertTrue(delay >= DiscordRPC.INITIAL_RETRY_DELAY_MS, "Delay should never be below initial");
            assertTrue(delay <= DiscordRPC.MAX_RETRY_DELAY_MS, "Delay should never exceed max");
        }
        assertEquals(DiscordRPC.MAX_RETRY_DELAY_MS, delay, "After multiple retries, delay should reach MAX_RETRY_DELAY_MS");
    }

    @Test
    void testGracefulFallbackWhenDiscordUnavailable() {
        DiscordRPC rpc = DiscordRPC.getInstance();

        // Ensure init does not throw when Discord is unavailable
        assertDoesNotThrow(rpc::init);
        assertTrue(rpc.isRunning(), "RPC should be marked as running");

        // Toggling state while disconnected should gracefully fallback without exception
        assertDoesNotThrow(rpc::setInLauncher);
        assertDoesNotThrow(() -> rpc.setInGame("1.20.4", "Steve"));
        assertDoesNotThrow(rpc::setInLauncher);

        // When Discord is not running locally, isConnected should remain false
        assertFalse(rpc.isConnected(), "Should be false when no IPC pipe is active");
    }

    @Test
    void testPipeConnectionResilience() {
        // Test pipe format for all standard Discord IPC indices (0..9)
        for (int i = 0; i < 10; i++) {
            String path = DiscordRPC.getPipePath(i);
            assertNotNull(path, "Pipe path should not be null");
            assertTrue(path.contains("discord-ipc-" + i), "Pipe path should contain index " + i);
        }

        DiscordRPC rpc = DiscordRPC.getInstance();

        // Resilience against null/empty strings in presence payloads
        assertDoesNotThrow(() -> {
            JsonObject nullVersion = rpc.buildPresencePayload(true, null, null, 0L, 0L);
            assertNotNull(nullVersion);
            JsonObject activity = nullVersion.getAsJsonObject("args").getAsJsonObject("activity");
            assertEquals("Играет в Minecraft", activity.get("details").getAsString());
            assertEquals("В игре", activity.get("state").getAsString());
        });

        assertDoesNotThrow(() -> {
            JsonObject emptyPayload = rpc.buildPresencePayload(false, "", "", 0L, 0L);
            assertNotNull(emptyPayload);
            JsonObject activity = emptyPayload.getAsJsonObject("args").getAsJsonObject("activity");
            assertEquals("WlLauncher", activity.get("details").getAsString());
            assertEquals("В лаунчере", activity.get("state").getAsString());
        });
    }

    @Test
    void testPipePathFormat() {
        String pipe0 = DiscordRPC.getPipePath(0);
        assertNotNull(pipe0);
        assertTrue(pipe0.contains("discord-ipc-0"));
    }

    @Test
    void testBuildPresencePayloadInLauncher() {
        DiscordRPC rpc = DiscordRPC.getInstance();
        JsonObject payload = rpc.buildPresencePayload(false, "", "", 1000000L, 1234L);

        assertNotNull(payload);
        assertEquals("SET_ACTIVITY", payload.get("cmd").getAsString());

        JsonObject args = payload.getAsJsonObject("args");
        assertEquals(1234L, args.get("pid").getAsLong());

        JsonObject activity = args.getAsJsonObject("activity");
        assertEquals("WlLauncher", activity.get("details").getAsString());
        assertEquals("В лаунчере", activity.get("state").getAsString());
    }

    @Test
    void testBuildPresencePayloadInGame() {
        DiscordRPC rpc = DiscordRPC.getInstance();
        JsonObject payload = rpc.buildPresencePayload(true, "1.20.4", "Steve", 2000000L, 5678L);

        assertNotNull(payload);
        JsonObject activity = payload.getAsJsonObject("args").getAsJsonObject("activity");
        assertEquals("Играет в 1.20.4", activity.get("details").getAsString());
        assertEquals("Игрок: Steve", activity.get("state").getAsString());
        assertEquals(2000L, activity.getAsJsonObject("timestamps").get("start").getAsLong());
        assertTrue(activity.has("buttons"), "Payload should contain Discord action buttons");
    }

    @Test
    void testBuildPresencePayloadWithMods() {
        DiscordRPC rpc = DiscordRPC.getInstance();
        JsonObject payload = rpc.buildPresencePayload(true, "Fabric 1.21.1", "Steve", 2000000L, 5678L, 35);

        assertNotNull(payload);
        JsonObject activity = payload.getAsJsonObject("args").getAsJsonObject("activity");
        assertEquals("Играет в Fabric 1.21.1 (35 модов)", activity.get("details").getAsString());
        assertEquals("Игрок: Steve", activity.get("state").getAsString());
    }

    @Test
    void testCountModsNullAndEmpty() {
        assertEquals(0, DiscordRPC.countMods(null));
        assertEquals(0, DiscordRPC.countMods(new java.io.File("non_existent_folder_xyz")));
    }
}
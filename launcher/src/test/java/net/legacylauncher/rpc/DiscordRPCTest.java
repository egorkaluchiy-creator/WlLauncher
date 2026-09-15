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
    }
}
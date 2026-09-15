package net.legacylauncher.modrinth;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthParsingTest {

    @Test
    void testModrinthProjectModel() {
        String json = "{\n" +
                "  \"project_id\": \"AANobbMI\",\n" +
                "  \"slug\": \"sodium\",\n" +
                "  \"title\": \"Sodium\",\n" +
                "  \"description\": \"Modern rendering engine for Minecraft\",\n" +
                "  \"author\": \"jellysquid\",\n" +
                "  \"downloads\": 15000000,\n" +
                "  \"categories\": [\"optimization\", \"fabric\"]\n" +
                "}";

        Gson gson = new Gson();
        ModrinthProject project = gson.fromJson(json, ModrinthProject.class);

        assertNotNull(project);
        assertEquals("sodium", project.getSlug());
        assertEquals("Sodium", project.getTitle());
        assertEquals("Modern rendering engine for Minecraft", project.getDescription());
        assertEquals("jellysquid", project.getAuthor());
        assertEquals(15000000L, project.getDownloads());
        assertNotNull(project.getCategories());
        assertTrue(project.getCategories().contains("optimization"));
    }

    @Test
    void testModrinthVersionParsing() {
        String json = "{\n" +
                "  \"id\": \"ver_123\",\n" +
                "  \"project_id\": \"sodium\",\n" +
                "  \"name\": \"Sodium 0.5.8 for 1.20.4\",\n" +
                "  \"version_number\": \"0.5.8\",\n" +
                "  \"version_type\": \"release\",\n" +
                "  \"game_versions\": [\"1.20.4\"],\n" +
                "  \"loaders\": [\"fabric\", \"quilt\"],\n" +
                "  \"files\": [\n" +
                "    {\n" +
                "      \"url\": \"https://cdn.modrinth.com/data/sodium/versions/0.5.8/sodium-fabric-0.5.8%2Bmc1.20.4.jar\",\n" +
                "      \"filename\": \"sodium-fabric-0.5.8+mc1.20.4.jar\",\n" +
                "      \"primary\": true,\n" +
                "      \"size\": 1024000\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        Gson gson = new Gson();
        ModrinthVersion version = gson.fromJson(json, ModrinthVersion.class);

        assertNotNull(version);
        assertEquals("ver_123", version.getId());
        assertEquals("0.5.8", version.getVersionNumber());
        assertEquals("release", version.getVersionType());
        assertTrue(version.getLoaders().contains("fabric"));
        assertTrue(version.getGameVersions().contains("1.20.4"));

        assertNotNull(version.getFiles());
        assertEquals(1, version.getFiles().size());

        ModrinthVersion.ModrinthFile primary = version.getPrimaryFile();
        assertNotNull(primary);
        assertEquals("sodium-fabric-0.5.8+mc1.20.4.jar", primary.getFilename());
        assertEquals(1024000L, primary.getSize());
        assertTrue(primary.isPrimary());
    }
}
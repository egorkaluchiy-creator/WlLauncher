package net.legacylauncher.modrinth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class ModrinthClient {
    private static final Logger log = LoggerFactory.getLogger(ModrinthClient.class);
    private static final String BASE_URL = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "WlLauncher/1.0.0 (https://github.com/egorkaluchiy-creator/WlLauncher)";
    private static final Gson gson = new Gson();

    public static List<ModrinthProject> searchMods(String query, String loader, String gameVersion, int limit) {
        try {
            StringBuilder urlBuilder = new StringBuilder(BASE_URL).append("/search?");
            List<String> params = new ArrayList<>();
            if (query != null && !query.trim().isEmpty()) {
                params.add("query=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name()));
            }

            List<String> facetsList = new ArrayList<>();
            facetsList.add("[\"project_type:mod\"]");
            if (loader != null && !loader.trim().isEmpty() && !loader.equalsIgnoreCase("all")) {
                facetsList.add("[\"categories:" + loader.toLowerCase().trim() + "\"]");
            }
            if (gameVersion != null && !gameVersion.trim().isEmpty() && !gameVersion.equalsIgnoreCase("all")) {
                facetsList.add("[\"versions:" + gameVersion.trim() + "\"]");
            }

            StringBuilder facets = new StringBuilder("[");
            for (int i = 0; i < facetsList.size(); i++) {
                if (i > 0) facets.append(",");
                facets.append(facetsList.get(i));
            }
            facets.append("]");

            params.add("facets=" + URLEncoder.encode(facets.toString(), StandardCharsets.UTF_8.name()));
            params.add("limit=" + Math.max(1, Math.min(50, limit)));

            for (int i = 0; i < params.size(); i++) {
                if (i > 0) urlBuilder.append("&");
                urlBuilder.append(params.get(i));
            }

            String json = httpGet(urlBuilder.toString());
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray hits = root.getAsJsonArray("hits");
            if (hits == null || hits.size() == 0) {
                return Collections.emptyList();
            }

            Type listType = new TypeToken<List<ModrinthProject>>() {}.getType();
            return gson.fromJson(hits, listType);
        } catch (Exception e) {
            log.error("Failed to search Modrinth mods for query '{}'", query, e);
            return Collections.emptyList();
        }
    }

    public static ModrinthVersion getLatestCompatibleVersion(String projectSlugOrId, String loader, String gameVersion) {
        try {
            StringBuilder urlBuilder = new StringBuilder(BASE_URL).append("/project/").append(projectSlugOrId).append("/version?");
            List<String> params = new ArrayList<>();
            if (loader != null && !loader.trim().isEmpty() && !loader.equalsIgnoreCase("all")) {
                params.add("loaders=" + URLEncoder.encode("[\"" + loader.toLowerCase().trim() + "\"]", StandardCharsets.UTF_8.name()));
            }
            if (gameVersion != null && !gameVersion.trim().isEmpty() && !gameVersion.equalsIgnoreCase("all")) {
                params.add("game_versions=" + URLEncoder.encode("[\"" + gameVersion.trim() + "\"]", StandardCharsets.UTF_8.name()));
            }

            for (int i = 0; i < params.size(); i++) {
                if (i > 0) urlBuilder.append("&");
                urlBuilder.append(params.get(i));
            }

            String json = httpGet(urlBuilder.toString());
            JsonArray versions = JsonParser.parseString(json).getAsJsonArray();
            if (versions == null || versions.size() == 0) {
                return null;
            }

            Type listType = new TypeToken<List<ModrinthVersion>>() {}.getType();
            List<ModrinthVersion> versionList = gson.fromJson(versions, listType);
            return versionList.isEmpty() ? null : versionList.get(0);
        } catch (Exception e) {
            log.error("Failed to get version for project '{}'", projectSlugOrId, e);
            return null;
        }
    }

    public static File downloadMod(ModrinthVersion.ModrinthFile file, File targetDir, Consumer<Integer> progressListener) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        File targetFile = new File(targetDir, file.getFilename());
        File tempFile = new File(targetDir, file.getFilename() + ".download");

        HttpURLConnection conn = (HttpURLConnection) new URL(file.getUrl()).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.connect();

        int responseCode = conn.getResponseCode();
        if (responseCode >= 300 && responseCode <= 308) {
            String newUrl = conn.getHeaderField("Location");
            if (newUrl != null) {
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.connect();
            }
        }

        long totalBytes = conn.getContentLengthLong();
        long downloaded = 0;

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (totalBytes > 0 && progressListener != null) {
                    int percent = (int) ((downloaded * 100) / totalBytes);
                    progressListener.accept(percent);
                }
            }
        }

        if (targetFile.exists()) {
            targetFile.delete();
        }
        if (!tempFile.renameTo(targetFile)) {
            throw new IOException("Failed to rename temp download file to " + targetFile.getName());
        }

        if (progressListener != null) {
            progressListener.accept(100);
        }
        log.info("Successfully downloaded mod: {}", targetFile.getAbsolutePath());
        return targetFile;
    }

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP request failed with status " + code + ": " + urlStr);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}

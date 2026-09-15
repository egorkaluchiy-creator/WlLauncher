package net.legacylauncher.modrinth;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ModrinthVersion {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("version_number")
    private String versionNumber;

    @SerializedName("game_versions")
    private List<String> gameVersions;

    @SerializedName("loaders")
    private List<String> loaders;

    @SerializedName("files")
    private List<ModrinthFile> files;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public List<String> getGameVersions() {
        return gameVersions;
    }

    public List<String> getLoaders() {
        return loaders;
    }

    public List<ModrinthFile> getFiles() {
        return files;
    }

    public ModrinthFile getPrimaryFile() {
        if (files == null || files.isEmpty()) {
            return null;
        }
        for (ModrinthFile f : files) {
            if (f.isPrimary()) {
                return f;
            }
        }
        return files.get(0);
    }

    public static class ModrinthFile {
        @SerializedName("url")
        private String url;

        @SerializedName("filename")
        private String filename;

        @SerializedName("primary")
        private boolean primary;

        @SerializedName("size")
        private long size;

        public String getUrl() {
            return url;
        }

        public String getFilename() {
            return filename;
        }

        public boolean isPrimary() {
            return primary;
        }

        public long getSize() {
            return size;
        }
    }
}

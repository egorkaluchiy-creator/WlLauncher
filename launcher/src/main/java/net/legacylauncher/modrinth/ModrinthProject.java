package net.legacylauncher.modrinth;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ModrinthProject {
    @SerializedName("project_id")
    private String projectId;

    @SerializedName("slug")
    private String slug;

    @SerializedName("title")
    private String title;

    @SerializedName("description")
    private String description;

    @SerializedName("author")
    private String author;

    @SerializedName("downloads")
    private long downloads;

    @SerializedName("icon_url")
    private String iconUrl;

    @SerializedName("categories")
    private List<String> categories;

    @SerializedName("versions")
    private List<String> versions;

    public String getProjectId() {
        return projectId;
    }

    public String getSlug() {
        return slug != null ? slug : projectId;
    }

    public String getTitle() {
        return title != null ? title : slug;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public String getAuthor() {
        return author != null ? author : "";
    }

    public long getDownloads() {
        return downloads;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<String> getVersions() {
        return versions;
    }
}

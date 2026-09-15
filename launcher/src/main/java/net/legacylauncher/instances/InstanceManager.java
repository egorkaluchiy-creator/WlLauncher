package net.legacylauncher.instances;

import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.configuration.Configuration;
import net.legacylauncher.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InstanceManager {
    private static final Logger log = LoggerFactory.getLogger(InstanceManager.class);
    private static final InstanceManager INSTANCE = new InstanceManager();

    public static InstanceManager getInstance() {
        return INSTANCE;
    }

    private InstanceManager() {
    }

    public File getInstancesDir() {
        File root = getRootDir();
        File instances = new File(root, "instances");
        if (!instances.exists()) {
            instances.mkdirs();
        }
        return instances;
    }

    public File getRootDir() {
        LegacyLauncher launcher = LegacyLauncher.getInstance();
        if (launcher != null && launcher.getSettings() != null) {
            String dir = launcher.getSettings().get("minecraft.gamedir");
            if (dir != null && !dir.trim().isEmpty()) {
                return new File(dir);
            }
        }
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            return new File(appdata, ".minecraft");
        }
        return new File(System.getProperty("user.home"), ".minecraft");
    }

    public List<String> listInstances() {
        File dir = getInstancesDir();
        File[] files = dir.listFiles(File::isDirectory);
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (File f : files) {
            list.add(f.getName());
        }
        Collections.sort(list);
        return list;
    }

    public File createInstance(String name) throws IOException {
        String cleanName = sanitizeName(name);
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("Имя инстанса не может быть пустым");
        }
        File instanceDir = new File(getInstancesDir(), cleanName);
        if (instanceDir.exists()) {
            throw new IOException("Инстанс с таким именем уже существует: " + cleanName);
        }
        FileUtil.createFolder(instanceDir);
        FileUtil.createFolder(new File(instanceDir, "mods"));
        FileUtil.createFolder(new File(instanceDir, "saves"));
        FileUtil.createFolder(new File(instanceDir, "config"));
        FileUtil.createFolder(new File(instanceDir, "resourcepacks"));
        FileUtil.createFolder(new File(instanceDir, "shaderpacks"));
        log.info("Created new instance directory: {}", instanceDir.getAbsolutePath());
        return instanceDir;
    }

    public boolean deleteInstance(String name) {
        File instanceDir = new File(getInstancesDir(), name);
        if (instanceDir.exists() && instanceDir.isDirectory()) {
            try {
                FileUtil.deleteDirectory(instanceDir);
                if (getSelectedInstance().equals(name)) {
                    setSelectedInstance("default");
                }
                log.info("Deleted instance: {}", name);
                return true;
            } catch (Exception e) {
                log.error("Failed to delete instance {}", name, e);
            }
        }
        return false;
    }

    public String getSelectedInstance() {
        LegacyLauncher launcher = LegacyLauncher.getInstance();
        if (launcher != null && launcher.getSettings() != null) {
            String inst = launcher.getSettings().get("minecraft.active_instance");
            if (inst != null && !inst.trim().isEmpty()) {
                return inst;
            }
        }
        return "default";
    }

    public void setSelectedInstance(String name) {
        LegacyLauncher launcher = LegacyLauncher.getInstance();
        if (launcher != null && launcher.getSettings() != null) {
            launcher.getSettings().set("minecraft.active_instance", name == null ? "default" : name);
        }
    }

    public File getActiveGameDir() {
        String selected = getSelectedInstance();
        if ("default".equalsIgnoreCase(selected) || selected == null || selected.trim().isEmpty()) {
            return getRootDir();
        }
        File instDir = new File(getInstancesDir(), selected);
        if (!instDir.exists()) {
            instDir.mkdirs();
        }
        return instDir;
    }

    public File getActiveModsDir() {
        File gameDir = getActiveGameDir();
        File mods = new File(gameDir, "mods");
        if (!mods.exists()) {
            mods.mkdirs();
        }
        return mods;
    }

    private String sanitizeName(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}

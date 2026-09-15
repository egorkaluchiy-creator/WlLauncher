package net.legacylauncher.instances;

import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

public class InstanceManager {
    private static final Logger log = LoggerFactory.getLogger(InstanceManager.class);
    private static final InstanceManager INSTANCE = new InstanceManager();

    private final List<InstanceManagerListener> listeners = new CopyOnWriteArrayList<>();

    public static InstanceManager getInstance() {
        return INSTANCE;
    }

    private InstanceManager() {
    }

    public void addListener(InstanceManagerListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(InstanceManagerListener listener) {
        listeners.remove(listener);
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
        return net.legacylauncher.util.MinecraftUtil.getWorkingDirectory();
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
            throw new IllegalArgumentException("Имя профиля не может быть пустым");
        }
        File instanceDir = new File(getInstancesDir(), cleanName);
        if (instanceDir.exists()) {
            throw new IOException("Профиль с таким именем уже существует: " + cleanName);
        }
        FileUtil.createFolder(instanceDir);
        FileUtil.createFolder(new File(instanceDir, "mods"));
        FileUtil.createFolder(new File(instanceDir, "saves"));
        FileUtil.createFolder(new File(instanceDir, "config"));
        FileUtil.createFolder(new File(instanceDir, "resourcepacks"));
        FileUtil.createFolder(new File(instanceDir, "shaderpacks"));

        // Copy currently selected version to new instance as initial default
        LegacyLauncher launcher = LegacyLauncher.getInstance();
        if (launcher != null && launcher.getSettings() != null) {
            String currentVer = launcher.getSettings().get("login.version");
            if (currentVer != null && !currentVer.trim().isEmpty()) {
                setInstanceVersion(cleanName, currentVer);
            }
        }

        log.info("Created new instance directory: {}", instanceDir.getAbsolutePath());
        notifyInstancesListChanged();
        return instanceDir;
    }

    public boolean deleteInstance(String name) {
        File instanceDir = new File(getInstancesDir(), name);
        if (instanceDir.exists() && instanceDir.isDirectory()) {
            try {
                FileUtil.deleteDirectory(instanceDir);
                if (getSelectedInstance().equalsIgnoreCase(name)) {
                    setSelectedInstance("default");
                }
                log.info("Deleted instance: {}", name);
                notifyInstancesListChanged();
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
        String cleanName = (name == null || name.trim().isEmpty()) ? "default" : name.trim();
        String old = getSelectedInstance();
        if (cleanName.equalsIgnoreCase(old)) {
            return;
        }
        LegacyLauncher launcher = LegacyLauncher.getInstance();
        if (launcher != null && launcher.getSettings() != null) {
            launcher.getSettings().set("minecraft.active_instance", cleanName);
        }
        log.info("Active instance switched: '{}' -> '{}'", old, cleanName);
        notifyActiveInstanceChanged(old, cleanName);
    }

    public String getInstanceVersion(String instanceName) {
        if (instanceName == null || "default".equalsIgnoreCase(instanceName)) {
            LegacyLauncher launcher = LegacyLauncher.getInstance();
            if (launcher != null && launcher.getSettings() != null) {
                return launcher.getSettings().get("login.version");
            }
            return null;
        }
        File instDir = new File(getInstancesDir(), instanceName);
        File propFile = new File(instDir, "instance.properties");
        if (propFile.exists()) {
            try (InputStream in = new FileInputStream(propFile)) {
                Properties p = new Properties();
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                return p.getProperty("instance.version");
            } catch (Exception e) {
                log.warn("Could not read instance.properties for {}", instanceName, e);
            }
        }
        return null;
    }

    public void setInstanceVersion(String instanceName, String version) {
        if (version == null) return;
        if (instanceName == null || "default".equalsIgnoreCase(instanceName)) {
            LegacyLauncher launcher = LegacyLauncher.getInstance();
            if (launcher != null && launcher.getSettings() != null) {
                launcher.getSettings().set("login.version", version);
            }
            return;
        }
        File instDir = new File(getInstancesDir(), instanceName);
        if (!instDir.exists()) {
            instDir.mkdirs();
        }
        File propFile = new File(instDir, "instance.properties");
        Properties p = new Properties();
        if (propFile.exists()) {
            try (InputStream in = new FileInputStream(propFile)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
        p.setProperty("instance.version", version);
        p.setProperty("instance.name", instanceName);
        p.setProperty("instance.updated", String.valueOf(System.currentTimeMillis()));
        try (OutputStream out = new FileOutputStream(propFile)) {
            p.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "WlLauncher Instance Configuration");
        } catch (Exception e) {
            log.error("Failed to save instance.properties for {}", instanceName, e);
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

    private void notifyActiveInstanceChanged(String oldInst, String newInst) {
        for (InstanceManagerListener l : listeners) {
            try {
                l.onActiveInstanceChanged(oldInst, newInst);
            } catch (Exception e) {
                log.error("Error in instance listener", e);
            }
        }
    }

    private void notifyInstancesListChanged() {
        for (InstanceManagerListener l : listeners) {
            try {
                l.onInstancesListChanged();
            } catch (Exception e) {
                log.error("Error in instance listener", e);
            }
        }
    }

    private String sanitizeName(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}

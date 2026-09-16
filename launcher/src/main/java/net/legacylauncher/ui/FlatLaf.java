package net.legacylauncher.ui;

import com.formdev.flatlaf.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.portals.Portals;
import net.legacylauncher.util.Lazy;
import net.legacylauncher.util.shared.FlatLafConfiguration;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static net.legacylauncher.util.shared.FlatLafConfiguration.getVersion;

@Slf4j
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class FlatLaf {
    private static final String SUPPORTED_CONFIG_VERSION = "v1";

    private static final Lazy<Boolean> IS_SUPPORTED = Lazy.of(() -> {
        try {
            if (!LegacyLauncher.getInstance().isMetadataEnabled("has_flatlaf")) {
                throw new RuntimeException("capability missing: has_flatlaf");
            }
            String configVersion = getVersion();
            if(!configVersion.equals(SUPPORTED_CONFIG_VERSION)) {
                throw new RuntimeException("version not supported: " + configVersion);
            }
        } catch(Throwable t) {
            log.warn("FlatLafConfiguration not available", t);
            return false;
        }
        return true;
    });

    private static final Lazy<List<String>> STATES = Lazy.of(() -> {
        if(!isSupported()) {
            return Collections.emptyList();
        }
        return Arrays.stream(FlatLafConfiguration.State.values())
                .map(FlatLafConfiguration.State::toString)
                .collect(Collectors.toList());
    });

    public static Map<String, String> getDefaults() {
        if(isSupported()) {
            return FlatLafConfiguration.getDefaults();
        } else {
            return Collections.emptyMap();
        }
    }

    public static Optional<FlatLafConfiguration> parseFromMap(Map<String, String> map) {
        if(isSupported()) {
            return Optional.of(FlatLafConfiguration.parseFromMap(map));
        } else {
            return Optional.empty();
        }
    }

    public static BufferedImage loadDefaultBackgroundFromThemeFile(String themeFile) {
        if (!themeFile.startsWith(":")) { // not a selector
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(Paths.get(themeFile)), StandardCharsets.UTF_8)) {
                JsonElement json = JsonParser.parseReader(reader);
                JsonObject o = json.getAsJsonObject();
                if (o.has("_tl")) {
                    JsonObject tlSection = o.getAsJsonObject("_tl");
                    if (tlSection.has("defaultBackground")) {
                        String defaultBackgroundPath = tlSection.getAsJsonPrimitive("defaultBackground")
                                .getAsString();
                        return ImageIO.read(new File(defaultBackgroundPath));
                    }
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Couldn't read default background from theme file: {}", themeFile, e);
            }
        }
        return null;
    }

    public static Optional<FlatLafConfiguration.Theme> getSelectedNowTheme(Optional<FlatLafConfiguration> configuration) {
        if (isSupported() && configuration.isPresent() && configuration.get().isEnabled()) {
            FlatLafConfiguration flatLafConfiguration = configuration.get();
            return Optional.of(flatLafConfiguration.getSelected().orElse(
                    UIManager.getBoolean("laf.dark") ?
                            FlatLafConfiguration.Theme.DARK : FlatLafConfiguration.Theme.LIGHT)
            );
        }
        return Optional.empty();
    }

    public static boolean isSupported() {
        return IS_SUPPORTED.get();
    }

    public static List<String> getStates() {
        return STATES.get();
    }

    public static void initialize(FlatLafConfiguration config, boolean ignoreState) {
        if (!config.isEnabled() && !ignoreState) {
            log.info("FlatLaf is not enabled. Skipping initialization");
            return;
        }
        FlatLafConfiguration.Theme theme = config.getSelected().orElse(detectTheme());
        setUIProperties(config.getUiPropertiesFiles().get(theme));
        setLaf(theme, config.getThemeFiles().get(theme));
    }

    public static void initialize(FlatLafConfiguration config) {
        initialize(config, false);
    }

    private static FlatLafConfiguration.Theme detectTheme() {
        try {
            switch (Portals.getPortal().getColorScheme()) {
                case PREFER_LIGHT:
                case NO_PREFERENCE:
                default:
                    return FlatLafConfiguration.Theme.LIGHT;
                case PREFER_DARK:
                    return FlatLafConfiguration.Theme.DARK;
            }
        } catch (Exception e) {
            return FlatLafConfiguration.Theme.LIGHT;
        }
    }

    private static void setLaf(FlatLafConfiguration.Theme theme, String themeFile) {
        com.formdev.flatlaf.FlatLaf laf = null;
        boolean useSystemTheme = false;
        if (themeFile != null) {
            if (themeFile.startsWith(":")) {
                switch (themeFile.substring(1)) {
                    case "darcula":
                        laf = new FlatDarculaLaf();
                        break;
                    case "dark":
                        laf = new FlatDarkLaf();
                        break;
                    case "intellij":
                        laf = new FlatIntelliJLaf();
                        break;
                    case "light":
                        laf = new FlatLightLaf();
                        break;
                    case "system":
                        useSystemTheme = true;
                        break;
                    default:
                        log.warn("unknown theme id {}", themeFile);
                        laf = new FlatLightLaf();
                        break;
                }
            } else {
                laf = loadLafFromThemeFile(themeFile);
            }
        }
        if (useSystemTheme) {
            log.info("System L&F is selected for theme {}", theme);
            if (theme == FlatLafConfiguration.Theme.DARK) {
                UIManager.put("laf.dark", true);
            }
            setSystemLookAndFeel();
            return;
        }
        if (laf == null) {
            switch (theme) {
                case DARK:
                    laf = new FlatDarkLaf();
                    break;
                case LIGHT:
                    laf = new FlatLightLaf();
                    break;
                default:
                    throw new IllegalArgumentException(theme.name());
            }
        }
        setLaf(laf);
    }

    private static com.formdev.flatlaf.FlatLaf loadLafFromThemeFile(String themeFile) {
        log.info("Loading L&F theme from {}", themeFile);
        try (FileInputStream in = new FileInputStream(themeFile)) {
            return IntelliJTheme.createLaf(in);
        } catch (IOException e) {
            log.error("Couldn't load IntelliJ theme from file {}", themeFile, e);
            return null;
        }
    }

    private static void setLaf(com.formdev.flatlaf.FlatLaf lookAndFeel) {
        log.info("Setting L&F {}", lookAndFeel);
        try {
            UIManager.setLookAndFeel(lookAndFeel);
            setupModernUIDefaults();
        } catch (Exception e) {
            log.error("Couldn't set L&F", e);
        }
        updateLafInWindows();
    }

    public static void setupModernUIDefaults() {
        // Modern rounded geometry and emerald accent
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ProgressBar.arc", 12);
        UIManager.put("ScrollBar.thumbArc", 8);
        UIManager.put("PopupMenu.borderCornerRadius", 10);
        UIManager.put("Component.accentColor", new Color(16, 185, 129));
        UIManager.put("Component.focusColor", new Color(16, 185, 129, 90));
        UIManager.put("Component.focusedBorderColor", new Color(16, 185, 129));
        UIManager.put("Component.borderColor", new Color(255, 255, 255, 45));

        // ComboBox styling
        UIManager.put("ComboBox.arc", 12);
        UIManager.put("ComboBox.padding", new Insets(5, 10, 5, 10));
        UIManager.put("ComboBox.focusedBorderColor", new Color(16, 185, 129));
        UIManager.put("ComboBox.selectionBackground", new Color(16, 185, 129, 60));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ComboBox.buttonBackground", new Color(32, 38, 50));
        UIManager.put("ComboBox.buttonEditableBackground", new Color(32, 38, 50));
        UIManager.put("ComboBox.buttonSeparatorColor", new Color(0, 0, 0, 0));
        UIManager.put("ComboBox.buttonDisabledSeparatorColor", new Color(0, 0, 0, 0));
        UIManager.put("ComboBox.buttonSeparatorWidth", 0);
        UIManager.put("ComboBox.arrowType", "chevron");

        // TabbedPane styling (Settings tabs, etc.)
        UIManager.put("TabbedPane.tabType", "underlined");
        UIManager.put("TabbedPane.underlineColor", new Color(16, 185, 129));
        UIManager.put("TabbedPane.inactiveUnderlineColor", new Color(255, 255, 255, 20));
        UIManager.put("TabbedPane.underlineHeight", 3);
        UIManager.put("TabbedPane.hoverColor", new Color(255, 255, 255, 15));
        UIManager.put("TabbedPane.focusColor", new Color(16, 185, 129, 40));
        UIManager.put("TabbedPane.selectedBackground", new Color(32, 36, 48, 80));
        UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
        UIManager.put("TabbedPane.tabInsets", new Insets(8, 20, 8, 20));
        UIManager.put("TabbedPane.tabArc", 8);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TabbedPane.hasFullBorder", false);
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.contentOpaque", false);
        UIManager.put("TabbedPane.opaque", false);

        // CheckBox modern styling
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("CheckBox.opaque", false);
        UIManager.put("CheckBox.icon.focusedBorderColor", new Color(16, 185, 129));
        UIManager.put("CheckBox.icon.selectedBackground", new Color(16, 185, 129));
    }

    public static void setSystemLookAndFeel() {
        String systemLaf = UIManager.getSystemLookAndFeelClassName();
        try {
            UIManager.setLookAndFeel(systemLaf);
        } catch (Exception e) {
            log.warn("Couldn't set system L&F {}", systemLaf, e);
        }
        updateLafInWindows();
    }

    private static void setUIProperties(String uiPropertiesFile) {
        if (uiPropertiesFile == null) {
            log.debug("No UI properties file, skipping");
            return;
        }
        log.info("Setting addon properties file: {}", uiPropertiesFile);
        FlatLafAddon.PROPERTIES_FILE = uiPropertiesFile;
    }

    public static void updateLafInWindows() {
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }
}

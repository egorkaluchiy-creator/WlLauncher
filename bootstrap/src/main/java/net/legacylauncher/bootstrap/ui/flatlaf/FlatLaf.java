package net.legacylauncher.bootstrap.ui.flatlaf;

import com.formdev.flatlaf.*;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.bootstrap.ui.UserInterface;
import net.legacylauncher.bootstrap.ui.flatlaf.themedetector.ThemeDetector;
import net.legacylauncher.util.shared.FlatLafConfiguration;

import javax.swing.*;
import java.awt.Color;
import java.awt.Insets;
import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
public class FlatLaf {
    public static void initialize(FlatLafConfiguration config) {
        if(config.isEnabled()) {
            FlatLafConfiguration.Theme theme = config.getSelected().orElse(detectTheme());
            setUIProperties(config.getUiPropertiesFiles().get(theme));
            setLaf(theme, config.getThemeFiles().get(theme));
        } else {
            log.info("FlatLaf is not enabled. Skipping initialization");
        }
    }

    private static FlatLafConfiguration.Theme detectTheme() {
        log.info("Detecting system theme");
        return ThemeDetector.detectTheme();
    }

    private static void setLaf(FlatLafConfiguration.Theme theme, String themeFile) {
        com.formdev.flatlaf.FlatLaf laf = null;
        boolean useSystemTheme = false;
        if (themeFile != null) {
            if(themeFile.startsWith(":")) {
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
                        log.warn("Unknown theme id: {}", themeFile);
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
            UserInterface.setSystemLookAndFeel();
            return;
        }
        if(laf == null) {
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
        try(FileInputStream in = new FileInputStream(themeFile)) {
            return IntelliJTheme.createLaf(in);
        } catch (IOException e) {
            log.error("Couldn't load IntelliJ theme from file: {}", themeFile, e);
            return null;
        }
    }

    private static void setLaf(com.formdev.flatlaf.FlatLaf lookAndFeel) {
        log.info("Setting L&F: {}", lookAndFeel);
        try {
            UIManager.setLookAndFeel(lookAndFeel);
            setupModernUIDefaults();
        } catch (Exception e) {
            log.error("Couldn't set L&F", e);
        }
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
        UIManager.put("ComboBox.arc", 10);
        UIManager.put("ComboBox.padding", new Insets(5, 10, 5, 10));
        UIManager.put("ComboBox.focusedBorderColor", new Color(16, 185, 129));
        UIManager.put("ComboBox.selectionBackground", new Color(16, 185, 129, 60));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ComboBox.buttonBackground", new Color(0, 0, 0, 0));
        UIManager.put("ComboBox.buttonEditableBackground", new Color(0, 0, 0, 0));
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

    private static void setUIProperties(String uiPropertiesFile) {
        if(uiPropertiesFile == null) {
            log.info("No UI properties file, skipping");
            return;
        }
        log.info("Setting addon properties file: {}", uiPropertiesFile);
        Addon.PROPERTIES_FILE = uiPropertiesFile;
    }
}

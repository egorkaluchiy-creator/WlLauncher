package net.legacylauncher.ui.settings;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.configuration.Configuration;
import net.legacylauncher.managers.JavaManagerConfig;
import net.legacylauncher.managers.VersionLists;
import net.legacylauncher.minecraft.launcher.hooks.GameModeHookLoader;
import net.legacylauncher.stats.Stats;
import net.legacylauncher.ui.FlatLaf;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.block.Blocker;
import net.legacylauncher.ui.converter.ActionOnLaunchConverter;
import net.legacylauncher.ui.converter.DirectionConverter;
import net.legacylauncher.ui.converter.LoggerTypeConverter;
import net.legacylauncher.ui.converter.SeparateDirsConverter;
import net.legacylauncher.ui.editor.*;
import net.legacylauncher.ui.explorer.FileExplorer;
import net.legacylauncher.ui.explorer.ImageFileExplorer;
import net.legacylauncher.ui.explorer.MediaFileExplorer;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.*;
import net.legacylauncher.ui.login.LoginException;
import net.legacylauncher.ui.login.LoginForm;
import net.legacylauncher.ui.scenes.DefaultScene;
import net.legacylauncher.ui.support.ContributorsAlert;
import net.legacylauncher.ui.swing.extended.BorderPanel;
import net.legacylauncher.ui.swing.extended.ExtendedButton;
import net.legacylauncher.util.Direction;
import net.legacylauncher.util.IntegerArray;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.shared.FlatLafConfiguration;
import net.minecraft.launcher.versions.ReleaseType;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;
import java.util.*;

import static net.legacylauncher.util.SwingUtil.updateUINullable;

@Slf4j
public class SettingsPanel extends TabbedEditorPanel implements LoginForm.LoginProcessListener, LocalizableComponent {
    final DefaultScene scene;
    private final TabbedEditorPanel.EditorPanelTab minecraftTab;
    public final EditorFieldHandler directory;
    public final EditorFieldHandler useSeparateDir;
    public final EditorFieldHandler resolution;
    public final EditorFieldHandler fullscreen;
    public final EditorFieldHandler jre;
    public final EditorFieldHandler memory;
    public final EditorFieldHandler gpu;
    public final EditorGroupHandler versionHandler;
    public final EditorFieldHandler oldVersionsHandler; // temp
    public final EditorGroupHandler extraHandler;
    public final EditorFieldHandler launcherResolution;
    public final EditorFieldHandler laf;
    public final EditorFieldHandler background;
    public final EditorFieldHandler loginFormDirection;
    public final EditorFieldHandler logger;
    public final EditorFieldHandler crashManager;
    public final EditorFieldHandler fullCommand;
    public final EditorFieldHandler launchAction;
    public final EditorGroupHandler alertUpdates;
    public final EditorFieldHandler allowNoticeDisable;
    public final EditorFieldHandler switchToBeta;
    public final EditorFieldHandler locale;
    public final HTMLPage about = null;
    private final BorderPanel buttonPanel;
    private final JPopupMenu popup;
    private final LocalizableMenuItem infoItem;
    private final LocalizableMenuItem defaultItem;
    private EditorHandler selectedHandler;

    public boolean ready = false;
    private boolean hideUponSave = true;

    public SettingsPanel(DefaultScene sc) {
        super(tipTheme, new Insets(5, 10, 10, 10));

        container.setNorth(null);
        setMagnifyGaps(false);

        if (tabPane.getExtendedUI() != null) {
            tabPane.getExtendedUI().setTheme(settingsTheme);
        }
        tabPane.setOpaque(false);
        tabPane.putClientProperty("JTabbedPane.tabType", "underlined");
        tabPane.putClientProperty("JTabbedPane.showTabSeparators", false);
        tabPane.putClientProperty("JTabbedPane.hasFullBorder", false);
        tabPane.putClientProperty("JTabbedPane.tabInsets", new Insets(8, 20, 8, 20));

        scene = sc;
        FocusListener warning = new FocusListener() {
            public void focusGained(FocusEvent e) {
                setError("settings.warning");
            }

            public void focusLost(FocusEvent e) {
                setMessage(null);
            }
        };
        FocusListener restart = new FocusListener() {
            public void focusGained(FocusEvent e) {
                setError("settings.restart");
            }

            public void focusLost(FocusEvent e) {
                setMessage(null);
            }
        };
        minecraftTab = new TabbedEditorPanel.EditorPanelTab("settings.tab.minecraft");

        FileExplorer dirExplorer;
        try {
            dirExplorer = FileExplorer.newExplorer();
            dirExplorer.setFileSelectionMode(1);
            dirExplorer.setFileHidingEnabled(false);
        } catch (Exception e) {
            dirExplorer = null;
        }

        directory = new EditorFieldHandler("minecraft.gamedir", new EditorFileField("settings.client.gamedir.prompt", dirExplorer, false, false), warning);
        directory.addListener(new EditorFieldChangeListener() {
            protected void onChange(String oldValue, String newValue) {
                if (ready) {
                    try {
                        tlauncher.getManager().getComponent(VersionLists.class).updateLocal();
                    } catch (IOException var4) {
                        Alert.showLocError("settings.client.gamedir.noaccess", var4);
                        return;
                    }

                    tlauncher.getVersionManager().asyncRefresh();
                    tlauncher.getProfileManager().recreate();
                }
            }
        });
        useSeparateDir = new EditorFieldHandler("minecraft.gamedir.separate", new EditorComboBox<>(new SeparateDirsConverter(true), Configuration.SeparateDirs.values()));
        minecraftTab.add(new EditorPair("settings.client.gamedir.label", directory, useSeparateDir));
        resolution = null;
        fullscreen = null;
        minecraftTab.nextPane();
        List<ReleaseType> releaseTypes = ReleaseType.getDefinable();
        List<EditorHandler> versions = new ArrayList<>(releaseTypes.size());

        versions.add(new EditorFieldHandler("minecraft.versions.sub." + ReleaseType.SubType.REMOTE, new EditorCheckBox("settings.versions.sub." + ReleaseType.SubType.REMOTE)));
        versions.add(EditorPair.NEXT_COLUMN);

        for (int i = 0; i < releaseTypes.size(); i++) {
            ReleaseType imgExplorer = releaseTypes.get(i);
            versions.add(new EditorFieldHandler("minecraft.versions." + imgExplorer, new EditorCheckBox("settings.versions." + imgExplorer)));
            if (i % 2 == 1) {
                versions.add(EditorPair.NEXT_COLUMN);
            }
        }

        versions.add(oldVersionsHandler = new EditorFieldHandler("minecraft.versions.sub." + ReleaseType.SubType.OLD_RELEASE, new EditorCheckBox("settings.versions.sub." + ReleaseType.SubType.OLD_RELEASE)));
        versions.add(new EditorFieldHandler("minecraft.versions.only-installed", new EditorCheckBox("settings.versions.only-installed")));

        versionHandler = new EditorGroupHandler(versions);
        versionHandler.addListener(new EditorFieldChangeListener() {
            protected void onChange(String oldvalue, String newvalue) {
                LegacyLauncher.getInstance().getVersionManager().updateVersionList();
            }
        });
        minecraftTab.add(new EditorPair("settings.versions.label", versions));
        minecraftTab.nextPane();
        jre = new EditorFieldHandler(JavaManagerConfig.PATH_JRE_TYPE, new JREComboBox(this));
        minecraftTab.add(new EditorPair("settings.jre.type.label", jre));
        final MemorySlider memorySlider = new MemorySlider(sc.loginForm.tlauncher.getMemoryAllocationService());

        minecraftTab.nextPane();
        memory = new EditorFieldHandler("minecraft.xmx", memorySlider, warning);
        minecraftTab.add(new EditorPair("settings.java.memory.label", memory));

        gpu = null;
        extraHandler = new EditorGroupHandler(Collections.emptyList());

        add(minecraftTab);
        EditorPanelTab tlauncherTab = new EditorPanelTab("settings.tab.tlauncher");
        launcherResolution = null;
        loginFormDirection = null;
        laf = new EditorFieldHandler(FlatLafConfiguration.KEY_STATE, new EditorComboBox<>(
                new LocalizableStringConverter<String>("settings.laf.state") {
                    @Override
                    protected String toPath(String from) {
                        return from;
                    }

                    @Override
                    public String fromString(String from) {
                        return from;
                    }

                    @Override
                    public String toValue(String from) {
                        return from;
                    }

                    @Override
                    public Class<String> getObjectClass() {
                        return String.class;
                    }
                },
                getLafStates()
        ));
        laf.addListener(new EditorFieldChangeListener() {
            protected void onChange(String oldValue, String newValue) {
                if (ready) {
                    sc.getMainPane().getRootFrame().updateLaf();
                    Alert.showLocWarning("", "settings.laf.restart-to-apply", null);
                }
            }
        });
        tlauncherTab.add(new EditorPair("settings.laf.label", laf));

        boolean mediaFxAvailable = sc.getMainPane().background.isMediaFxAvailable();
        FileExplorer backgroundExplorer = null;
        try {
            backgroundExplorer = mediaFxAvailable ? MediaFileExplorer.newExplorer() : ImageFileExplorer.newExplorer();
        } catch (Exception e) {
            log.warn("Could not load FileExplorer", e);
        }

        background = new EditorFieldHandler("gui.background", new EditorFileField("settings.slide.list.prompt." + (mediaFxAvailable ? "media" : "image"), backgroundExplorer, true, true));
        background.addListener(new EditorFieldChangeListener() {
            protected void onChange(String oldValue, String newValue) {
                if (SettingsPanel.this.ready) {
                    tlauncher.getFrame().mp.background.loadBackground();
                }
            }
        });
        tlauncherTab.add(new EditorPair("settings.slide.list.label", background));
        logger = null;
        fullCommand = null;
        launchAction = null;
        crashManager = new EditorFieldHandler("minecraft.crash", new EditorCheckBox("settings.crash.enable"));
        tlauncherTab.add(new EditorPair("settings.crash.label", crashManager));

        alertUpdates = new EditorGroupHandler(Collections.emptyList());
        allowNoticeDisable = null;
        switchToBeta = null;

        locale = new EditorFieldHandler("locale", new SettingsLocaleComboBox(this));
        locale.addListener(new EditorFieldChangeListener() {
            protected void onChange(String oldvalue, String newvalue) {
                if (SettingsPanel.this.ready) {
                    if (tlauncher.getFrame() != null) {
                        tlauncher.getFrame().updateLocales();
                    }
                    ContributorsAlert.showAlert();
                    hideUponSave = false;
                }
            }
        });
        tlauncherTab.add(new EditorPair("settings.lang.label", locale));
        add(tlauncherTab);
        LocalizableButton saveButton = new LocalizableButton("settings.save");
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));
        saveButton.addActionListener(e -> {
            if (!saveValues()) {
                return;
            }
            if (hideUponSave) {
                scene.setSidePanel(null);
            } else {
                hideUponSave = true;
            }
        });
        LocalizableButton defaultButton = new LocalizableButton("settings.default");
        defaultButton.addActionListener(e -> {
            if (Alert.showLocQuestion("settings.default.warning")) {
                resetValues();
            }

        });
        ExtendedButton homeButton = new ExtendedButton();
        homeButton.setIcon(Images.getIcon24("home"));
        homeButton.addActionListener(e -> {
            updateValues();
            scene.setSidePanel(null);
        });
        Dimension size1 = homeButton.getPreferredSize();
        if (size1 != null) {
            homeButton.setPreferredSize(new Dimension(SwingUtil.magnify(40), size1.height));
        }

        buttonPanel = new BorderPanel();
        buttonPanel.setCenter(sepPan(saveButton, defaultButton));
        buttonPanel.setEast(uSepPan(homeButton));
        tabPane.addChangeListener(new ChangeListener() {
            private final String aboutBlock = "abouttab";

            public void stateChanged(ChangeEvent e) {
                if (tabPane.getSelectedComponent() instanceof TabbedEditorPanel.EditorScrollPane && !((TabbedEditorPanel.EditorScrollPane) tabPane.getSelectedComponent()).getTab().getSavingEnabled()) {
                    Blocker.blockComponents("abouttab", buttonPanel);
                } else {
                    Blocker.unblockComponents("abouttab", buttonPanel);
                }

            }
        });
        container.setSouth(buttonPanel);
        popup = new JPopupMenu();
        infoItem = new LocalizableMenuItem("settings.popup.info");
        infoItem.setEnabled(false);
        popup.add(infoItem);
        defaultItem = new LocalizableMenuItem("settings.popup.default");
        defaultItem.addActionListener(e -> {
            if (selectedHandler != null) {
                resetValue(selectedHandler);
            }
        });
        popup.add(defaultItem);

        for (EditorHandler handler : handlers) {
            JComponent handlerComponent = handler.getComponent();
            if (handlerComponent != null)
                handlerComponent.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        if (e.getButton() == 3) {
                            callPopup(e, handler);
                        }
                    }
                });
        }

        updateValues();
        updateLocale();
    }

    private String[] getLafStates() {
        return new String[]{
                FlatLafConfiguration.State.DARK.toString(),
                FlatLafConfiguration.State.LIGHT.toString()
        };
    }

    public void updateValues() {
        boolean globalUnSaveable = !global.isSaveable();
        Iterator<EditorHandler> iterator = handlers.iterator();

        if (allowNoticeDisable != null && !tlauncher.isNoticeDisablingAllowed()) {
            allowNoticeDisable.getComponent().setEnabled(false);
            //allowNoticeDisableHint.getComponent().setEnabled(false);
        }

        while (true) {
            EditorHandler handler;
            String path;
            do {
                if (!iterator.hasNext()) {
                    return;
                }

                handler = iterator.next();
                path = handler.getPath();
                if (path == null)
                    continue;
                String value = global.get(path);
                handler.updateValue(value);
                setValid(handler, true);
            } while (!globalUnSaveable && global.isSaveable(path));

            Blocker.block(handler, "unsaveable");
        }
    }

    public boolean saveValues() {
        if (!checkValues()) {
            return false;
        } else {

            for (EditorHandler handler : handlers) {
                String path = handler.getPath();
                if (path == null)
                    continue;
                String value = handler.getValue();
                global.set(path, value, false);
                handler.onChange(value);
            }

            global.store();
            updateValues();
            return true;
        }
    }

    void resetValues() {

        for (EditorHandler handler : handlers) {
            resetValue(handler);
        }

    }

    void resetValue(EditorHandler handler) {
        String path = handler.getPath();
        if (global.isSaveable(path)) {
            String value = global.getDefault(path);
            handler.setValue(value);
        }
    }

    boolean canReset(EditorHandler handler) {
        String key = handler.getPath();
        return global.isSaveable(key) && global.getDefault(handler.getPath()) != null;
    }

    void callPopup(MouseEvent e, EditorHandler handler) {
        if (popup.isShowing()) {
            popup.setVisible(false);
        }

        defocus();
        int x = e.getX();
        int y = e.getY();
        selectedHandler = handler;
        updateResetMenu();
        infoItem.setVariables(handler.getPath());
        popup.show((JComponent) e.getSource(), x, y);
    }

    public void block(Object reason) {
        Blocker.blockComponents(minecraftTab, reason);
        updateResetMenu();
    }

    public void unblock(Object reason) {
        Blocker.unblockComponents(minecraftTab, reason);
        updateResetMenu();
    }

    private void updateResetMenu() {
        if (selectedHandler != null) {
            defaultItem.setEnabled(!Blocker.isBlocked(selectedHandler));
        }

    }

    public void loggingIn() throws LoginException {
        boolean ok;
        if (!tlauncher.getSettings().isFirstRun() && anyChanges()) {
            ok = askToSaveChanges();
        } else {
            ok = checkValues();
        }
        if (!ok) {
            scene.setSidePanel(DefaultScene.SidePanel.SETTINGS);
            throw new LoginException("Invalid settings!");
        }
    }

    public void loginFailed() {
    }

    public void loginSucceed() {
    }

    public void updateLocale() {
        /*if (tlauncher.getSettings().isLikelyRussianSpeakingLocale()) {
            add(serverTab);
        } else {
            remove(serverTab);
        }*/
    }

    private boolean anyChanges() {
        for (EditorHandler handler : handlers) {
            String path = handler.getPath();
            if (path == null) {
                continue;
            }
            String value = handler.getValue();
            String existingValue = global.get(path);
            if (!Objects.equals(value, existingValue)) {
                log.debug("Found changes: {}", path);
                return true;
            }
        }
        return false;
    }

    private boolean askToSaveChanges() {
        if (Alert.showQuestion(
                "",
                Localizable.get("settings.changed.confirm")
        )) {
            if (!saveValues()) {
                return false;
            }
        } else {
            updateValues();
        }
        scene.setSidePanel(null);
        return true;
    }

    @Override
    public void updateUI() {
        if (ready) {
            checkValues();
            updateUINullable(popup);
        }
        super.updateUI();
    }
}

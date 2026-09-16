package net.legacylauncher.ui.modrinth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moandjiezana.toml.Toml;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.swing.extended.ExtendedFrame;
import net.legacylauncher.util.MinecraftUtil;
import net.legacylauncher.util.OS;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
public class ModManagerFrame extends ExtendedFrame {
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    private final File modsDir;
    private final List<ModItem> allMods = Collections.synchronizedList(new ArrayList<>());
    private final List<ModItem> displayedMods = Collections.synchronizedList(new ArrayList<>());
    private final ModTableModel tableModel = new ModTableModel();
    private final JTable table;
    private final JTextField searchField;
    private final JComboBox<String> filterCombo;
    private final JLabel statsLabel;
    private final JLabel statusMessageLabel;

    public ModManagerFrame() {
        this(new File(MinecraftUtil.getWorkingDirectory(), "mods"));
    }

    public ModManagerFrame(File modsDir) {
        this.modsDir = modsDir;
        if (!this.modsDir.exists()) {
            this.modsDir.mkdirs();
        }

        setTitle("Менеджер установленных модов — WlLauncher");
        try {
            setIconImage(Images.loadIcon("plug", 24));
        } catch (Exception ignored) {
        }
        setSize(950, 620);
        setMinimumSize(new Dimension(800, 480));
        setLayout(new BorderLayout());

        // Header Panel
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        topPanel.setBackground(new Color(35, 39, 42));

        JPanel headerInfo = new JPanel(new BorderLayout());
        headerInfo.setOpaque(false);
        JLabel titleLabel = new JLabel("Менеджер установленных модов");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);

        JLabel pathLabel = new JLabel("Папка: " + modsDir.getAbsolutePath());
        pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pathLabel.setForeground(new Color(180, 190, 200));

        headerInfo.add(titleLabel, BorderLayout.NORTH);
        headerInfo.add(pathLabel, BorderLayout.SOUTH);
        topPanel.add(headerInfo, BorderLayout.NORTH);

        // Filter / Search Row
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        searchRow.setOpaque(false);

        JLabel searchTitle = new JLabel("Поиск:");
        searchTitle.setForeground(Color.WHITE);
        searchTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        searchRow.add(searchTitle);

        searchField = new JTextField(20);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.putClientProperty("JTextField.placeholderText", "Фильтр по названию...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        searchRow.add(searchField);

        JLabel filterTitle = new JLabel("Фильтр:");
        filterTitle.setForeground(Color.WHITE);
        filterTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        searchRow.add(filterTitle);

        filterCombo = new JComboBox<>(new String[]{"Все моды", "Только включенные", "Только отключенные"});
        filterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        filterCombo.addActionListener(e -> applyFilter());
        searchRow.add(filterCombo);

        JButton refreshBtn = new JButton("Обновить");
        refreshBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> scanModsAsync());
        searchRow.add(refreshBtn);

        topPanel.add(searchRow, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // Center Table
        table = new JTable(tableModel);
        table.setRowHeight(36);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(true);
        table.setGridColor(new Color(55, 60, 65));
        table.setBackground(new Color(44, 47, 51));
        table.setForeground(Color.WHITE);
        table.getTableHeader().setBackground(new Color(32, 34, 37));
        table.getTableHeader().setForeground(Color.WHITE);

        // Setup Column Renderers and Widths
        table.getColumnModel().getColumn(0).setPreferredWidth(75);
        table.getColumnModel().getColumn(0).setMaxWidth(85);
        table.getColumnModel().getColumn(0).setCellRenderer(new CheckBoxRenderer());

        table.getColumnModel().getColumn(1).setPreferredWidth(450);
        table.getColumnModel().getColumn(1).setCellRenderer(new ModNameRenderer());

        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setCellRenderer(new LoaderTypeRenderer());

        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setCellRenderer(new CenterTextRenderer());

        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setCellRenderer(new ActionButtonRenderer());

        // Handle Click on Table (Toggle checkbox or Action button)
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0 || row >= displayedMods.size()) return;

                ModItem item = displayedMods.get(row);
                if (col == 0) {
                    toggleMod(item);
                } else if (col == 4) {
                    deleteModPrompt(item);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(44, 47, 51));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Action Bar
        JPanel bottomBar = new JPanel(new BorderLayout(10, 10));
        bottomBar.setBorder(new EmptyBorder(10, 16, 12, 16));
        bottomBar.setBackground(new Color(35, 39, 42));

        JPanel statsPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        statsPanel.setOpaque(false);
        statsLabel = new JLabel("Загрузка списка модов...");
        statsLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statsLabel.setForeground(new Color(220, 225, 230));

        statusMessageLabel = new JLabel("Кликните на чекбокс слева для быстрого включения/отключения мода");
        statusMessageLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        statusMessageLabel.setForeground(new Color(160, 170, 180));

        statsPanel.add(statsLabel);
        statsPanel.add(statusMessageLabel);
        bottomBar.add(statsPanel, BorderLayout.WEST);

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionsPanel.setOpaque(false);

        JButton enableAllBtn = new JButton("Включить все");
        enableAllBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        enableAllBtn.setFocusPainted(false);
        enableAllBtn.addActionListener(e -> setAllModsState(true));
        actionsPanel.add(enableAllBtn);

        JButton disableAllBtn = new JButton("Отключить все");
        disableAllBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        disableAllBtn.setFocusPainted(false);
        disableAllBtn.addActionListener(e -> setAllModsState(false));
        actionsPanel.add(disableAllBtn);

        JButton openFolderBtn = new JButton("Открыть папку mods");
        openFolderBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        openFolderBtn.setFocusPainted(false);
        openFolderBtn.addActionListener(e -> {
            if (modsDir.isDirectory()) {
                OS.openFolder(modsDir);
            } else {
                Alert.showLocError("mods.not-found");
            }
        });
        actionsPanel.add(openFolderBtn);

        JButton catalogBtn = new JButton("Каталог Modrinth");
        catalogBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        catalogBtn.setBackground(new Color(88, 101, 242));
        catalogBtn.setForeground(Color.WHITE);
        catalogBtn.setFocusPainted(false);
        catalogBtn.addActionListener(e -> {
            new ModrinthCatalogFrame().showAtCenter();
        });
        actionsPanel.add(catalogBtn);

        bottomBar.add(actionsPanel, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        scanModsAsync();
    }

    private void scanModsAsync() {
        statsLabel.setText("Сканирование папки mods...");
        executor.submit(() -> {
            List<ModItem> loaded = new ArrayList<>();
            File[] files = modsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && isModFile(f.getName())) {
                        ModItem item = parseModItem(f);
                        loaded.add(item);
                    }
                }
            }

            loaded.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));

            SwingUtilities.invokeLater(() -> {
                allMods.clear();
                allMods.addAll(loaded);
                applyFilter();
            });
        });
    }

    private boolean isModFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".jar.disabled") || lower.endsWith(".jar.bak");
    }

    private ModItem parseModItem(File file) {
        String originalName = file.getName();
        boolean enabled = originalName.toLowerCase(Locale.ROOT).endsWith(".jar");
        long size = file.length();

        String displayName = null;
        String version = null;
        String loaderType = "Unknown";

        if (file.canRead()) {
            try (ZipFile zip = new ZipFile(file, ZipFile.OPEN_READ)) {
                // 1. Try Fabric
                ZipEntry fabricEntry = zip.getEntry("fabric.mod.json");
                if (fabricEntry != null) {
                    loaderType = "Fabric";
                    try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(fabricEntry), StandardCharsets.UTF_8)) {
                        JsonElement el = JsonParser.parseReader(reader);
                        if (el.isJsonObject()) {
                            JsonObject obj = el.getAsJsonObject();
                            if (obj.has("name") && !obj.get("name").isJsonNull()) {
                                displayName = obj.get("name").getAsString();
                            } else if (obj.has("id") && !obj.get("id").isJsonNull()) {
                                displayName = obj.get("id").getAsString();
                            }
                            if (obj.has("version") && !obj.get("version").isJsonNull()) {
                                version = obj.get("version").getAsString();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                // 2. Try Quilt
                if (displayName == null) {
                    ZipEntry quiltEntry = zip.getEntry("quilt.mod.json");
                    if (quiltEntry != null) {
                        loaderType = "Quilt";
                        try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(quiltEntry), StandardCharsets.UTF_8)) {
                            JsonElement el = JsonParser.parseReader(reader);
                            if (el.isJsonObject()) {
                                JsonObject obj = el.getAsJsonObject();
                                if (obj.has("quilt_loader") && obj.get("quilt_loader").isJsonObject()) {
                                    JsonObject ql = obj.getAsJsonObject("quilt_loader");
                                    if (ql.has("id")) displayName = ql.get("id").getAsString();
                                    if (ql.has("version")) version = ql.get("version").getAsString();
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                // 3. Try NeoForge
                if (displayName == null) {
                    ZipEntry neoEntry = zip.getEntry("META-INF/neoforge.mods.toml");
                    if (neoEntry != null) {
                        loaderType = "NeoForge";
                        try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(neoEntry), StandardCharsets.UTF_8)) {
                            Toml toml = new Toml().read(reader);
                            List<Toml> mods = toml.getTables("mods");
                            if (mods != null && !mods.isEmpty()) {
                                displayName = mods.get(0).getString("displayName", mods.get(0).getString("modId"));
                                version = mods.get(0).getString("version");
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                // 4. Try Forge mods.toml
                if (displayName == null) {
                    ZipEntry forgeEntry = zip.getEntry("META-INF/mods.toml");
                    if (forgeEntry != null) {
                        loaderType = "Forge";
                        try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(forgeEntry), StandardCharsets.UTF_8)) {
                            Toml toml = new Toml().read(reader);
                            List<Toml> mods = toml.getTables("mods");
                            if (mods != null && !mods.isEmpty()) {
                                displayName = mods.get(0).getString("displayName", mods.get(0).getString("modId"));
                                version = mods.get(0).getString("version");
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                // 5. Try Legacy Forge mcmod.info
                if (displayName == null) {
                    ZipEntry mcmodEntry = zip.getEntry("mcmod.info");
                    if (mcmodEntry != null) {
                        loaderType = "Forge";
                        try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(mcmodEntry), StandardCharsets.UTF_8)) {
                            JsonElement el = JsonParser.parseReader(reader);
                            if (el.isJsonArray() && el.getAsJsonArray().size() > 0) {
                                JsonObject obj = el.getAsJsonArray().get(0).getAsJsonObject();
                                if (obj.has("name")) displayName = obj.get("name").getAsString();
                                if (obj.has("version")) version = obj.get("version").getAsString();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (displayName == null || displayName.trim().isEmpty() || displayName.startsWith("${")) {
            String base = originalName.replaceAll("(?i)\\.jar(\\.disabled|\\.bak)?$", "");
            displayName = base;
        }

        return new ModItem(file, originalName, displayName, version, loaderType, enabled, size);
    }

    private synchronized void applyFilter() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        int filterMode = filterCombo.getSelectedIndex(); // 0 = all, 1 = enabled, 2 = disabled

        displayedMods.clear();
        for (ModItem item : allMods) {
            if (filterMode == 1 && !item.isEnabled()) continue;
            if (filterMode == 2 && item.isEnabled()) continue;

            if (!query.isEmpty()) {
                boolean matchName = item.getDisplayName().toLowerCase(Locale.ROOT).contains(query);
                boolean matchFile = item.getFileName().toLowerCase(Locale.ROOT).contains(query);
                boolean matchLoader = item.getLoaderType().toLowerCase(Locale.ROOT).contains(query);
                if (!matchName && !matchFile && !matchLoader) continue;
            }

            displayedMods.add(item);
        }

        tableModel.fireTableDataChanged();
        updateStats();
    }

    private void updateStats() {
        int total = allMods.size();
        long enabledCount = allMods.stream().filter(ModItem::isEnabled).count();
        long disabledCount = total - enabledCount;

        statsLabel.setText(String.format(Locale.ROOT, "Всего модов: %d  (Включено: %d,  Отключено: %d)", total, enabledCount, disabledCount));
    }

    private void toggleMod(ModItem item) {
        File currentFile = item.getFile();
        if (!currentFile.exists()) {
            scanModsAsync();
            return;
        }

        File targetFile;
        if (item.isEnabled()) {
            // Disable: rename to .jar.disabled
            String newName = currentFile.getName().replaceAll("(?i)\\.jar$", ".jar.disabled");
            targetFile = new File(currentFile.getParentFile(), newName);
        } else {
            // Enable: rename to .jar
            String newName = currentFile.getName().replaceAll("(?i)\\.jar(\\.(disabled|bak))+$", ".jar");
            targetFile = new File(currentFile.getParentFile(), newName);
        }

        if (currentFile.renameTo(targetFile)) {
            item.setFile(targetFile);
            item.setFileName(targetFile.getName());
            item.setEnabled(!item.isEnabled());
            tableModel.fireTableDataChanged();
            updateStats();
        } else {
            Alert.showLocError("mods.rename-error", "Не удалось переименовать файл: " + currentFile.getName());
        }
    }

    private void setAllModsState(boolean enable) {
        int changed = 0;
        for (ModItem item : new ArrayList<>(allMods)) {
            if (item.isEnabled() != enable) {
                File currentFile = item.getFile();
                if (!currentFile.exists()) continue;

                File targetFile;
                if (enable) {
                    String newName = currentFile.getName().replaceAll("(?i)\\.jar(\\.(disabled|bak))+$", ".jar");
                    targetFile = new File(currentFile.getParentFile(), newName);
                } else {
                    String newName = currentFile.getName().replaceAll("(?i)\\.jar$", ".jar.disabled");
                    targetFile = new File(currentFile.getParentFile(), newName);
                }

                if (currentFile.renameTo(targetFile)) {
                    item.setFile(targetFile);
                    item.setFileName(targetFile.getName());
                    item.setEnabled(enable);
                    changed++;
                }
            }
        }
        applyFilter();
    }

    private void deleteModPrompt(ModItem item) {
        int res = JOptionPane.showConfirmDialog(
                this,
                "Вы уверены, что хотите удалить мод:\n\n" + item.getDisplayName() + "\n(" + item.getFileName() + ")?",
                "Удаление мода",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (res == JOptionPane.YES_OPTION) {
            File f = item.getFile();
            if (f.delete() || !f.exists()) {
                allMods.remove(item);
                applyFilter();
            } else {
                Alert.showLocError("mods.delete-error", "Не удалось удалить файл мода: " + f.getName());
            }
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    @Getter
    @Setter
    public static class ModItem {
        private File file;
        private String fileName;
        private String displayName;
        private String version;
        private String loaderType;
        private boolean enabled;
        private long sizeBytes;

        public ModItem(File file, String fileName, String displayName, String version, String loaderType, boolean enabled, long sizeBytes) {
            this.file = file;
            this.fileName = fileName;
            this.displayName = displayName;
            this.version = version;
            this.loaderType = loaderType;
            this.enabled = enabled;
            this.sizeBytes = sizeBytes;
        }
    }

    private class ModTableModel extends AbstractTableModel {
        private final String[] columns = {"Статус", "Название и файл мода", "Загрузчик", "Размер", "Действие"};

        @Override
        public int getRowCount() {
            return displayedMods.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= displayedMods.size()) return null;
            ModItem item = displayedMods.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return item.isEnabled();
                case 1:
                    return item;
                case 2:
                    return item.getLoaderType();
                case 3:
                    return formatFileSize(item.getSizeBytes());
                case 4:
                    return "Удалить";
                default:
                    return null;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    private static class CheckBoxRenderer extends JCheckBox implements TableCellRenderer {
        public CheckBoxRenderer() {
            setHorizontalAlignment(CENTER);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setSelected(Boolean.TRUE.equals(value));
            setBackground(row % 2 == 0 ? new Color(44, 47, 51) : new Color(38, 41, 45));
            setForeground(Color.WHITE);
            return this;
        }
    }

    private static class ModNameRenderer extends JPanel implements TableCellRenderer {
        private final JLabel nameLabel = new JLabel();
        private final JLabel fileLabel = new JLabel();

        public ModNameRenderer() {
            setLayout(new GridLayout(2, 1, 0, 1));
            setBorder(new EmptyBorder(2, 8, 2, 8));
            setOpaque(true);

            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            fileLabel.setForeground(new Color(160, 170, 180));

            add(nameLabel);
            add(fileLabel);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value instanceof ModItem) {
                ModItem item = (ModItem) value;
                String ver = (item.getVersion() != null && !item.getVersion().isEmpty()) ? " (" + item.getVersion() + ")" : "";
                nameLabel.setText(item.getDisplayName() + ver);
                fileLabel.setText(item.getFileName());

                if (item.isEnabled()) {
                    nameLabel.setForeground(Color.WHITE);
                } else {
                    nameLabel.setForeground(new Color(150, 150, 150));
                }
            }
            setBackground(row % 2 == 0 ? new Color(44, 47, 51) : new Color(38, 41, 45));
            return this;
        }
    }

    private static class LoaderTypeRenderer extends JLabel implements TableCellRenderer {
        public LoaderTypeRenderer() {
            setHorizontalAlignment(CENTER);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            String val = String.valueOf(value);
            setText(val);
            if ("Fabric".equalsIgnoreCase(val)) {
                setForeground(new Color(196, 160, 255));
            } else if ("Forge".equalsIgnoreCase(val)) {
                setForeground(new Color(255, 175, 120));
            } else if ("NeoForge".equalsIgnoreCase(val)) {
                setForeground(new Color(255, 120, 120));
            } else if ("Quilt".equalsIgnoreCase(val)) {
                setForeground(new Color(180, 140, 255));
            } else {
                setForeground(new Color(170, 180, 190));
            }
            setBackground(row % 2 == 0 ? new Color(44, 47, 51) : new Color(38, 41, 45));
            return this;
        }
    }

    private static class CenterTextRenderer extends DefaultTableCellRenderer {
        public CenterTextRenderer() {
            setHorizontalAlignment(CENTER);
            setFont(new Font("Segoe UI", Font.PLAIN, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(row % 2 == 0 ? new Color(44, 47, 51) : new Color(38, 41, 45));
            setForeground(new Color(200, 210, 220));
            return this;
        }
    }

    private static class ActionButtonRenderer extends JButton implements TableCellRenderer {
        public ActionButtonRenderer() {
            setText("Удалить");
            setFont(new Font("Segoe UI", Font.PLAIN, 11));
            setForeground(new Color(255, 100, 100));
            setOpaque(true);
            setFocusPainted(false);
            setBorder(new LineBorder(new Color(80, 40, 40), 1, true));
            setBackground(new Color(45, 30, 30));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            return this;
        }
    }
}

package net.legacylauncher.ui.modrinth;

import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.instances.InstanceManager;
import net.legacylauncher.modrinth.ModrinthClient;
import net.legacylauncher.modrinth.ModrinthProject;
import net.legacylauncher.modrinth.ModrinthVersion;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.swing.extended.ExtendedFrame;
import net.legacylauncher.util.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModrinthCatalogFrame extends ExtendedFrame {
    private static final Logger log = LoggerFactory.getLogger(ModrinthCatalogFrame.class);
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final ConcurrentHashMap<String, Icon> iconCache = new ConcurrentHashMap<>();

    private final JTextField searchField;
    private final JComboBox<String> loaderCombo;
    private final JComboBox<String> versionCombo;
    private final JPanel cardsPanel;
    private final JLabel statusLabel;
    private final Timer debounceTimer;

    public ModrinthCatalogFrame() {
        setTitle("Каталог модов Modrinth — WlLauncher");
        try {
            setIconImage(Images.loadIcon("package", 24));
        } catch (Exception ignored) {
        }
        setSize(880, 640);
        setMinimumSize(new Dimension(750, 500));
        setLayout(new BorderLayout());

        // Top Panel: Title and Search Controls
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        topPanel.setBackground(new Color(35, 39, 42));

        // Header text
        JPanel headerInfo = new JPanel(new BorderLayout());
        headerInfo.setOpaque(false);
        JLabel titleLabel = new JLabel("🚀 Каталог модов (Modrinth API)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);

        File activeMods = InstanceManager.getInstance().getActiveModsDir();
        JLabel pathLabel = new JLabel("Папка установки: " + activeMods.getAbsolutePath());
        pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pathLabel.setForeground(new Color(180, 190, 200));

        headerInfo.add(titleLabel, BorderLayout.NORTH);
        headerInfo.add(pathLabel, BorderLayout.SOUTH);
        topPanel.add(headerInfo, BorderLayout.NORTH);

        // Filter & Search row
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        filterRow.setOpaque(false);

        searchField = new JTextField(20);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        searchField.putClientProperty("JTextField.placeholderText", "Поиск (например: Sodium, Iris, Xaero)...");
        filterRow.add(new JLabel("🔍"));
        filterRow.add(searchField);

        loaderCombo = new JComboBox<>(new String[]{"Все загрузчики", "Fabric", "Forge", "Quilt", "NeoForge"});
        loaderCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        filterRow.add(loaderCombo);

        versionCombo = new JComboBox<>(new String[]{
                "Все версии", "1.21.4", "1.21.1", "1.20.4", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.16.5", "1.12.2"
        });
        versionCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        filterRow.add(versionCombo);

        JButton searchButton = new JButton("Найти");
        searchButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        searchButton.setBackground(new Color(88, 101, 242));
        searchButton.setForeground(Color.WHITE);
        searchButton.setFocusPainted(false);
        searchButton.addActionListener(e -> performSearch());
        filterRow.add(searchButton);

        topPanel.add(filterRow, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // Center: Cards Scroll Pane
        cardsPanel = new JPanel();
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setBackground(new Color(44, 47, 51));
        cardsPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(cardsPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(44, 47, 51));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Bar
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBorder(new EmptyBorder(8, 16, 8, 16));
        bottomBar.setBackground(new Color(35, 39, 42));

        statusLabel = new JLabel("Готово к поиску. Введите название мода.");
        statusLabel.setForeground(new Color(200, 205, 210));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bottomBar.add(statusLabel, BorderLayout.WEST);

        JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottomButtons.setOpaque(false);

        JButton openModsBtn = new JButton("📂 Папка mods");
        openModsBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        openModsBtn.addActionListener(e -> OS.openFolder(InstanceManager.getInstance().getActiveModsDir()));
        bottomButtons.add(openModsBtn);

        JButton installedModsBtn = new JButton("📋 Установленные моды");
        installedModsBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        installedModsBtn.addActionListener(e -> showInstalledModsDialog());
        bottomButtons.add(installedModsBtn);

        bottomBar.add(bottomButtons, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        // Debounce timer for search
        debounceTimer = new Timer(450, e -> performSearch());
        debounceTimer.setRepeats(false);

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    debounceTimer.stop();
                    performSearch();
                } else {
                    debounceTimer.restart();
                }
            }
        });

        loaderCombo.addActionListener(e -> performSearch());
        versionCombo.addActionListener(e -> performSearch());

        // Default initial search: Top popular mods
        SwingUtilities.invokeLater(this::performSearch);
    }

    private void performSearch() {
        String query = searchField.getText();
        String loader = loaderCombo.getSelectedIndex() == 0 ? "all" : (String) loaderCombo.getSelectedItem();
        String version = versionCombo.getSelectedIndex() == 0 ? "all" : (String) versionCombo.getSelectedItem();

        statusLabel.setText("🔍 Поиск модов на Modrinth...");
        cardsPanel.removeAll();
        cardsPanel.revalidate();
        cardsPanel.repaint();

        executor.submit(() -> {
            List<ModrinthProject> hits = ModrinthClient.searchMods(query, loader, version, 25);
            SwingUtilities.invokeLater(() -> {
                cardsPanel.removeAll();
                if (hits.isEmpty()) {
                    JLabel noResults = new JLabel("Ничего не найдено. Попробуйте изменить поисковый запрос или фильтры.");
                    noResults.setForeground(Color.LIGHT_GRAY);
                    noResults.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    noResults.setBorder(new EmptyBorder(20, 20, 20, 20));
                    cardsPanel.add(noResults);
                    statusLabel.setText("Результатов не найдено.");
                } else {
                    for (ModrinthProject p : hits) {
                        cardsPanel.add(createModCard(p));
                        cardsPanel.add(Box.createRigidArea(new Dimension(0, 8)));
                    }
                    statusLabel.setText("Найдено модов: " + hits.size());
                }
                cardsPanel.revalidate();
                cardsPanel.repaint();
            });
        });
    }

    private JPanel createModCard(ModrinthProject project) {
        JPanel card = new JPanel(new BorderLayout(12, 6));
        card.setBackground(new Color(54, 57, 63));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(68, 72, 79), 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));

        // Left Icon
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(64, 64));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setIcon(getDefaultModIcon());
        card.add(iconLabel, BorderLayout.WEST);

        // Async load icon
        if (project.getIconUrl() != null && !project.getIconUrl().isEmpty()) {
            Icon cached = iconCache.get(project.getIconUrl());
            if (cached != null) {
                iconLabel.setIcon(cached);
            } else {
                executor.submit(() -> {
                    try {
                        BufferedImage img = ImageIO.read(new URL(project.getIconUrl()));
                        if (img != null) {
                            Image scaled = img.getScaledInstance(64, 64, Image.SCALE_SMOOTH);
                            javax.swing.ImageIcon icon = new javax.swing.ImageIcon(scaled);
                            iconCache.put(project.getIconUrl(), icon);
                            SwingUtilities.invokeLater(() -> iconLabel.setIcon(icon));
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
        }

        // Center: Title, Author, Description
        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleRow.setOpaque(false);

        JLabel title = new JLabel(project.getTitle());
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(Color.WHITE);
        titleRow.add(title);

        JLabel author = new JLabel("от " + project.getAuthor());
        author.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        author.setForeground(new Color(170, 175, 180));
        titleRow.add(author);

        JLabel downloads = new JLabel("📥 " + formatDownloads(project.getDownloads()));
        downloads.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        downloads.setForeground(new Color(87, 242, 135));
        titleRow.add(downloads);

        center.add(titleRow, BorderLayout.NORTH);

        JLabel desc = new JLabel(project.getDescription());
        desc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        desc.setForeground(new Color(210, 215, 220));
        center.add(desc, BorderLayout.CENTER);

        card.add(center, BorderLayout.CENTER);

        // Right: Install Button
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);

        JButton installBtn = new JButton("Установить");
        installBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        installBtn.setBackground(new Color(46, 204, 113));
        installBtn.setForeground(Color.WHITE);
        installBtn.setFocusPainted(false);
        installBtn.setPreferredSize(new Dimension(130, 36));

        // Check if already in active mods folder
        File activeMods = InstanceManager.getInstance().getActiveModsDir();
        boolean isAlreadyInstalled = checkIfModInstalled(project.getSlug(), activeMods);
        if (isAlreadyInstalled) {
            installBtn.setText("Установлен ✓");
            installBtn.setBackground(new Color(60, 64, 69));
            installBtn.setEnabled(false);
        }

        installBtn.addActionListener(e -> {
            installBtn.setEnabled(false);
            installBtn.setText("Поиск файла...");
            String loader = loaderCombo.getSelectedIndex() == 0 ? "fabric" : (String) loaderCombo.getSelectedItem();
            String version = versionCombo.getSelectedIndex() == 0 ? "1.18.2" : (String) versionCombo.getSelectedItem();

            executor.submit(() -> {
                ModrinthVersion ver = ModrinthClient.getLatestCompatibleVersion(project.getSlug(), loader, version);
                if (ver == null) {
                    // Try without filters if specific version not found
                    ver = ModrinthClient.getLatestCompatibleVersion(project.getSlug(), null, null);
                }

                if (ver == null || ver.getPrimaryFile() == null) {
                    SwingUtilities.invokeLater(() -> {
                        installBtn.setText("Не найден");
                        installBtn.setBackground(new Color(231, 76, 60));
                    });
                    return;
                }

                ModrinthVersion.ModrinthFile modFile = ver.getPrimaryFile();
                try {
                    ModrinthClient.downloadMod(modFile, activeMods, percent -> {
                        SwingUtilities.invokeLater(() -> installBtn.setText("Скачивание " + percent + "%"));
                    });
                    SwingUtilities.invokeLater(() -> {
                        installBtn.setText("Установлен ✓");
                        installBtn.setBackground(new Color(46, 204, 113));
                        statusLabel.setText("✅ Успешно установлен: " + modFile.getFilename());
                    });
                } catch (Exception ex) {
                    log.error("Failed to install mod {}", project.getSlug(), ex);
                    SwingUtilities.invokeLater(() -> {
                        installBtn.setText("Ошибка");
                        installBtn.setBackground(new Color(231, 76, 60));
                        installBtn.setEnabled(true);
                    });
                }
            });
        });

        rightPanel.add(installBtn);
        card.add(rightPanel, BorderLayout.EAST);

        return card;
    }

    private boolean checkIfModInstalled(String slug, File modsDir) {
        if (!modsDir.exists()) return false;
        File[] files = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null) return false;
        String cleanSlug = slug.toLowerCase().replace("-", "").replace("_", "");
        for (File f : files) {
            String name = f.getName().toLowerCase().replace("-", "").replace("_", "");
            if (name.contains(cleanSlug)) {
                return true;
            }
        }
        return false;
    }

    private void showInstalledModsDialog() {
        File modsDir = InstanceManager.getInstance().getActiveModsDir();
        File[] files = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));

        JDialog dialog = new JDialog(this, "Установленные моды (" + (files == null ? 0 : files.length) + ")", true);
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        DefaultListModel<String> model = new DefaultListModel<>();
        if (files != null) {
            for (File f : files) {
                model.addElement(f.getName());
            }
        }

        JList<String> list = new JList<>(model);
        list.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton deleteBtn = new JButton("Удалить выбранный");
        deleteBtn.addActionListener(e -> {
            String selected = list.getSelectedValue();
            if (selected != null) {
                File target = new File(modsDir, selected);
                if (target.delete()) {
                    model.removeElement(selected);
                }
            }
        });
        p.add(deleteBtn);

        JButton openFolder = new JButton("Открыть папку");
        openFolder.addActionListener(e -> OS.openFolder(modsDir));
        p.add(openFolder);

        dialog.add(p, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private Icon getDefaultModIcon() {
        return Images.getIcon24("package");
    }

    private String formatDownloads(long downloads) {
        if (downloads >= 1_000_000) {
            return String.format("%.1fM", downloads / 1_000_000.0);
        } else if (downloads >= 1_000) {
            return String.format("%.1fK", downloads / 1_000.0);
        }
        return String.valueOf(downloads);
    }
}

package net.legacylauncher.ui.modrinth;

import net.legacylauncher.modrinth.ModrinthClient;
import net.legacylauncher.modrinth.ModrinthProject;
import net.legacylauncher.modrinth.ModrinthVersion;
import net.legacylauncher.util.MinecraftUtil;
import net.legacylauncher.util.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ModrinthVersionDialog extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(ModrinthVersionDialog.class);
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    private final ModrinthProject project;
    private final Icon projectIcon;
    private final JComboBox<String> loaderFilterCombo;
    private final JComboBox<String> versionFilterCombo;
    private final JComboBox<String> typeFilterCombo;
    private final JPanel versionsListPanel;
    private final JLabel statusLabel;
    private final File modsDir;

    private List<ModrinthVersion> allVersions = Collections.emptyList();

    public ModrinthVersionDialog(Window parent, ModrinthProject project, Icon projectIcon) {
        super(parent, "Выбор версии — " + (project != null ? project.getTitle() : "Мод"), ModalityType.APPLICATION_MODAL);
        this.project = project;
        this.projectIcon = projectIcon;
        this.modsDir = new File(MinecraftUtil.getWorkingDirectory(), "mods");
        if (!this.modsDir.exists()) {
            this.modsDir.mkdirs();
        }

        setSize(780, 560);
        setMinimumSize(new Dimension(680, 440));
        setLocationRelativeTo(parent);
        getContentPane().setBackground(new Color(44, 47, 51));
        setLayout(new BorderLayout());

        // Top Panel: Header & Filters
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setBackground(new Color(35, 39, 42));
        topContainer.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Mod info banner
        JPanel infoBanner = new JPanel(new BorderLayout(12, 0));
        infoBanner.setOpaque(false);

        if (projectIcon != null) {
            JLabel iconLabel = new JLabel(projectIcon);
            iconLabel.setPreferredSize(new Dimension(48, 48));
            infoBanner.add(iconLabel, BorderLayout.WEST);
        }

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBox.setOpaque(false);

        JLabel nameLabel = new JLabel(project.getTitle() + " (от " + project.getAuthor() + ")");
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        nameLabel.setForeground(Color.WHITE);
        titleBox.add(nameLabel);

        JLabel pathInfo = new JLabel("Установка в: " + modsDir.getAbsolutePath());
        pathInfo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pathInfo.setForeground(new Color(180, 190, 200));
        titleBox.add(pathInfo);

        infoBanner.add(titleBox, BorderLayout.CENTER);
        topContainer.add(infoBanner);
        topContainer.add(Box.createRigidArea(new Dimension(0, 10)));

        // Filter Controls Row
        JPanel filtersRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filtersRow.setOpaque(false);

        JLabel filterLabel = new JLabel("Фильтры:");
        filterLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        filterLabel.setForeground(new Color(220, 225, 230));
        filtersRow.add(filterLabel);

        loaderFilterCombo = new JComboBox<>(new String[]{"Все загрузчики", "Fabric", "Forge", "NeoForge", "Quilt"});
        loaderFilterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        loaderFilterCombo.addActionListener(e -> applyFilters());
        filtersRow.add(loaderFilterCombo);

        versionFilterCombo = new JComboBox<>(new String[]{"Все версии игры"});
        versionFilterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        versionFilterCombo.addActionListener(e -> applyFilters());
        filtersRow.add(versionFilterCombo);

        typeFilterCombo = new JComboBox<>(new String[]{"Все типы", "Release", "Beta", "Alpha"});
        typeFilterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        typeFilterCombo.addActionListener(e -> applyFilters());
        filtersRow.add(typeFilterCombo);

        topContainer.add(filtersRow);
        add(topContainer, BorderLayout.NORTH);

        // Center: Version Cards
        versionsListPanel = new JPanel();
        versionsListPanel.setLayout(new BoxLayout(versionsListPanel, BoxLayout.Y_AXIS));
        versionsListPanel.setBackground(new Color(44, 47, 51));
        versionsListPanel.setBorder(new EmptyBorder(10, 14, 10, 14));

        JScrollPane scrollPane = new JScrollPane(versionsListPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(44, 47, 51));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Bar: Status and Close
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(new Color(35, 39, 42));
        bottomBar.setBorder(new EmptyBorder(10, 16, 10, 16));

        statusLabel = new JLabel("Загрузка списка версий...");
        statusLabel.setForeground(new Color(200, 205, 210));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bottomBar.add(statusLabel, BorderLayout.WEST);

        JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottomButtons.setOpaque(false);

        JButton openFolderBtn = new JButton("Папка mods");
        openFolderBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        openFolderBtn.addActionListener(e -> OS.openFolder(modsDir));
        bottomButtons.add(openFolderBtn);

        JButton closeBtn = new JButton("Закрыть");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeBtn.addActionListener(e -> dispose());
        bottomButtons.add(closeBtn);

        bottomBar.add(bottomButtons, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        // Fetch versions asynchronously
        fetchVersions();
    }

    private void fetchVersions() {
        executor.submit(() -> {
            try {
                List<ModrinthVersion> list = ModrinthClient.getAllProjectVersions(project.getSlug());
                this.allVersions = list != null ? list : Collections.emptyList();

                // Extract all game versions
                Set<String> distinctGameVersions = new LinkedHashSet<>();
                for (ModrinthVersion v : allVersions) {
                    if (v.getGameVersions() != null) {
                        distinctGameVersions.addAll(v.getGameVersions());
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    versionFilterCombo.removeAllItems();
                    versionFilterCombo.addItem("Все версии игры");
                    for (String gv : distinctGameVersions) {
                        versionFilterCombo.addItem(gv);
                    }
                    statusLabel.setText("Доступно версий: " + allVersions.size());
                    applyFilters();
                });
            } catch (Exception ex) {
                log.error("Failed to load versions for {}", project.getSlug(), ex);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Ошибка загрузки версий.");
                });
            }
        });
    }

    private void applyFilters() {
        String selectedLoader = (String) loaderFilterCombo.getSelectedItem();
        String selectedGameVer = (String) versionFilterCombo.getSelectedItem();
        String selectedType = (String) typeFilterCombo.getSelectedItem();

        List<ModrinthVersion> filtered = allVersions.stream().filter(v -> {
            if (selectedLoader != null && !selectedLoader.equals("Все загрузчики")) {
                if (v.getLoaders() == null || v.getLoaders().stream().noneMatch(l -> l.equalsIgnoreCase(selectedLoader))) {
                    return false;
                }
            }
            if (selectedGameVer != null && !selectedGameVer.equals("Все версии игры")) {
                if (v.getGameVersions() == null || !v.getGameVersions().contains(selectedGameVer)) {
                    return false;
                }
            }
            if (selectedType != null && !selectedType.equals("Все типы")) {
                if (!selectedType.equalsIgnoreCase(v.getVersionType())) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        renderVersionCards(filtered);
    }

    private void renderVersionCards(List<ModrinthVersion> versions) {
        versionsListPanel.removeAll();

        if (versions.isEmpty()) {
            JLabel emptyLabel = new JLabel("Нет версий, подходящих под выбранные фильтры.");
            emptyLabel.setForeground(Color.LIGHT_GRAY);
            emptyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            emptyLabel.setBorder(new EmptyBorder(20, 10, 20, 10));
            versionsListPanel.add(emptyLabel);
        } else {
            for (ModrinthVersion ver : versions) {
                versionsListPanel.add(createVersionRow(ver, modsDir));
                versionsListPanel.add(Box.createRigidArea(new Dimension(0, 6)));
            }
        }

        versionsListPanel.revalidate();
        versionsListPanel.repaint();
    }

    private JPanel createVersionRow(ModrinthVersion ver, File activeMods) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(new Color(54, 57, 63));
        row.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(68, 72, 79), 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        row.setPreferredSize(new Dimension(720, 64));

        // Left info: Version Name + Badges + MC + Loaders
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        // First line: Version name + type badge
        JPanel nameLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        nameLine.setOpaque(false);

        String verTitle = ver.getName() != null && !ver.getName().trim().isEmpty() ? ver.getName() : ver.getVersionNumber();
        JLabel nameLbl = new JLabel(verTitle);
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLbl.setForeground(Color.WHITE);
        nameLine.add(nameLbl);

        // Badge for type
        String type = ver.getVersionType() != null ? ver.getVersionType().toLowerCase() : "release";
        JLabel badge = new JLabel(" " + type.toUpperCase() + " ");
        badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        badge.setOpaque(true);
        if ("release".equalsIgnoreCase(type)) {
            badge.setBackground(new Color(46, 204, 113));
            badge.setForeground(Color.WHITE);
        } else if ("beta".equalsIgnoreCase(type)) {
            badge.setBackground(new Color(230, 126, 34));
            badge.setForeground(Color.WHITE);
        } else {
            badge.setBackground(new Color(231, 76, 60));
            badge.setForeground(Color.WHITE);
        }
        nameLine.add(badge);

        leftPanel.add(nameLine);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 3)));

        // Second line: MC versions & Loaders & File Size
        JPanel subLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        subLine.setOpaque(false);

        String mcStr = ver.getGameVersions() != null ? String.join(", ", ver.getGameVersions()) : "";
        if (mcStr.length() > 35) {
            mcStr = mcStr.substring(0, 32) + "...";
        }
        JLabel mcLbl = new JLabel("MC: " + (mcStr.isEmpty() ? "любая" : mcStr));
        mcLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        mcLbl.setForeground(new Color(170, 180, 190));
        subLine.add(mcLbl);

        String loadersStr = ver.getLoaders() != null ? String.join(", ", ver.getLoaders()) : "";
        JLabel loaderLbl = new JLabel("•  " + loadersStr);
        loaderLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        loaderLbl.setForeground(new Color(88, 166, 255));
        subLine.add(loaderLbl);

        ModrinthVersion.ModrinthFile primaryFile = ver.getPrimaryFile();
        if (primaryFile != null && primaryFile.getSize() > 0) {
            JLabel sizeLbl = new JLabel("•  " + formatSize(primaryFile.getSize()));
            sizeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            sizeLbl.setForeground(new Color(150, 155, 160));
            subLine.add(sizeLbl);
        }

        leftPanel.add(subLine);
        row.add(leftPanel, BorderLayout.CENTER);

        // Right side: Download / Install Button
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);

        JButton downloadBtn = new JButton("Скачать");
        downloadBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        downloadBtn.setBackground(new Color(46, 204, 113));
        downloadBtn.setForeground(Color.WHITE);
        downloadBtn.setFocusPainted(false);
        downloadBtn.setPreferredSize(new Dimension(120, 32));

        if (primaryFile != null) {
            File existing = new File(activeMods, primaryFile.getFilename());
            if (existing.exists()) {
                downloadBtn.setText("Установлен");
                downloadBtn.setBackground(new Color(60, 64, 69));
                downloadBtn.setEnabled(false);
            }
        }

        downloadBtn.addActionListener(e -> {
            if (primaryFile == null) {
                downloadBtn.setText("Нет файла");
                downloadBtn.setEnabled(false);
                return;
            }

            downloadBtn.setEnabled(false);
            downloadBtn.setText("Скачивание...");
            statusLabel.setText("Скачивание " + primaryFile.getFilename() + "...");

            executor.submit(() -> {
                try {
                    ModrinthClient.downloadMod(primaryFile, activeMods, percent -> {
                        SwingUtilities.invokeLater(() -> downloadBtn.setText("Загрузка " + percent + "%"));
                    });
                    SwingUtilities.invokeLater(() -> {
                        downloadBtn.setText("Установлен");
                        downloadBtn.setBackground(new Color(60, 64, 69));
                        statusLabel.setText("Успешно установлен: " + primaryFile.getFilename());
                    });
                } catch (Exception ex) {
                    log.error("Failed to download mod file {}", primaryFile.getFilename(), ex);
                    SwingUtilities.invokeLater(() -> {
                        downloadBtn.setText("Ошибка");
                        downloadBtn.setBackground(new Color(231, 76, 60));
                        downloadBtn.setEnabled(true);
                        statusLabel.setText("Ошибка скачивания " + primaryFile.getFilename());
                    });
                }
            });
        });

        rightPanel.add(downloadBtn);
        row.add(rightPanel, BorderLayout.EAST);

        return row;
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}

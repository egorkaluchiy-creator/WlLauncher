package net.legacylauncher.ui.modrinth;

import net.legacylauncher.LegacyLauncher;
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
    private static final ExecutorService executor = Executors.newFixedThreadPool(6);
    private static final ConcurrentHashMap<String, Icon> iconCache = new ConcurrentHashMap<>();
    private static final int PAGE_SIZE = 50;

    private final JTextField searchField;
    private final JComboBox<String> loaderCombo;
    private final JPanel cardsPanel;
    private final JLabel statusLabel;
    private final Timer debounceTimer;
    private final JButton prevPageBtn;
    private final JButton nextPageBtn;
    private final JLabel pageInfoLabel;
    private final File modsDir;

    private int currentPage = 1;
    private int totalHits = 0;

    public ModrinthCatalogFrame() {
        setTitle("Каталог модов Modrinth — WlLauncher");
        try {
            setIconImage(Images.loadIcon("download", 24));
        } catch (Exception ignored) {
        }
        setSize(1000, 680);
        setMinimumSize(new Dimension(850, 520));
        setLayout(new BorderLayout());

        modsDir = new File(net.legacylauncher.util.MinecraftUtil.getWorkingDirectory(), "mods");
        if (!modsDir.exists()) {
            modsDir.mkdirs();
        }

        // Top Panel: Title and Search Controls
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        topPanel.setBackground(new Color(35, 39, 42));

        // Header text
        JPanel headerInfo = new JPanel(new BorderLayout());
        headerInfo.setOpaque(false);
        JLabel titleLabel = new JLabel("Каталог модов Modrinth");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);

        JLabel pathLabel = new JLabel("Папка установки: " + modsDir.getAbsolutePath());
        pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pathLabel.setForeground(new Color(180, 190, 200));

        JButton localModsBtn = new JButton("Установленные моды");
        localModsBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        localModsBtn.setBackground(new Color(60, 65, 70));
        localModsBtn.setForeground(Color.WHITE);
        localModsBtn.setFocusPainted(false);
        localModsBtn.addActionListener(e -> new ModManagerFrame(modsDir).showAtCenter());

        headerInfo.add(titleLabel, BorderLayout.NORTH);
        headerInfo.add(pathLabel, BorderLayout.SOUTH);
        headerInfo.add(localModsBtn, BorderLayout.EAST);
        topPanel.add(headerInfo, BorderLayout.NORTH);

        // Filter & Search row
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        filterRow.setOpaque(false);

        JLabel searchTitle = new JLabel("Поиск:");
        searchTitle.setForeground(Color.WHITE);
        searchTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        filterRow.add(searchTitle);

        searchField = new JTextField(24);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        searchField.putClientProperty("JTextField.placeholderText", "Название мода (Sodium, Iris, JEI)...");
        filterRow.add(searchField);

        JLabel loaderTitle = new JLabel("Загрузчик:");
        loaderTitle.setForeground(Color.WHITE);
        loaderTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        filterRow.add(loaderTitle);

        loaderCombo = new JComboBox<>(new String[]{"Все загрузчики", "Fabric", "Forge", "NeoForge", "Quilt"});
        loaderCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        filterRow.add(loaderCombo);

        JButton searchButton = new JButton("Найти");
        searchButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        searchButton.setBackground(new Color(88, 101, 242));
        searchButton.setForeground(Color.WHITE);
        searchButton.setFocusPainted(false);
        searchButton.addActionListener(e -> {
            currentPage = 1;
            performSearch();
        });
        filterRow.add(searchButton);

        topPanel.add(filterRow, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // Center: Cards Scroll Pane
        cardsPanel = new JPanel();
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setBackground(new Color(44, 47, 51));
        cardsPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(cardsPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
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

        // Center Pagination Controls
        JPanel paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        paginationPanel.setOpaque(false);

        prevPageBtn = new JButton("< Назад");
        prevPageBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        prevPageBtn.setEnabled(false);
        prevPageBtn.addActionListener(e -> {
            if (currentPage > 1) {
                currentPage--;
                performSearch();
            }
        });
        paginationPanel.add(prevPageBtn);

        pageInfoLabel = new JLabel("Стр. 1");
        pageInfoLabel.setForeground(Color.WHITE);
        pageInfoLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        paginationPanel.add(pageInfoLabel);

        nextPageBtn = new JButton("Вперед >");
        nextPageBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        nextPageBtn.setEnabled(false);
        nextPageBtn.addActionListener(e -> {
            int maxPages = (int) Math.ceil((double) totalHits / PAGE_SIZE);
            if (currentPage < maxPages) {
                currentPage++;
                performSearch();
            }
        });
        paginationPanel.add(nextPageBtn);

        bottomBar.add(paginationPanel, BorderLayout.CENTER);

        JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottomButtons.setOpaque(false);

        JButton openModsBtn = new JButton("Папка mods");
        openModsBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        openModsBtn.addActionListener(e -> OS.openFolder(modsDir));
        bottomButtons.add(openModsBtn);

        JButton installedModsBtn = new JButton("Установленные моды");
        installedModsBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        installedModsBtn.addActionListener(e -> showInstalledModsDialog());
        bottomButtons.add(installedModsBtn);

        bottomBar.add(bottomButtons, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        // Debounce timer for search
        debounceTimer = new Timer(450, e -> {
            currentPage = 1;
            performSearch();
        });
        debounceTimer.setRepeats(false);

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    debounceTimer.stop();
                    currentPage = 1;
                    performSearch();
                } else {
                    debounceTimer.restart();
                }
            }
        });

        loaderCombo.addActionListener(e -> {
            currentPage = 1;
            performSearch();
        });

        // Default initial search: Top popular mods
        SwingUtilities.invokeLater(this::performSearch);
    }

    private void performSearch() {
        String query = searchField.getText();
        String loader = loaderCombo.getSelectedIndex() == 0 ? "all" : (String) loaderCombo.getSelectedItem();

        statusLabel.setText("Поиск модов на Modrinth...");
        prevPageBtn.setEnabled(false);
        nextPageBtn.setEnabled(false);

        int offset = (currentPage - 1) * PAGE_SIZE;

        executor.submit(() -> {
            ModrinthClient.ModrinthSearchResult result = ModrinthClient.searchMods(query, loader, null, PAGE_SIZE, offset);
            List<ModrinthProject> hits = result.getHits();
            totalHits = result.getTotalHits();
            int maxPages = Math.max(1, (int) Math.ceil((double) totalHits / PAGE_SIZE));

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
                    statusLabel.setText("Найдено: " + totalHits + " модов (показано " + hits.size() + ")");
                }

                pageInfoLabel.setText("Стр. " + currentPage + " из " + maxPages);
                prevPageBtn.setEnabled(currentPage > 1);
                nextPageBtn.setEnabled(currentPage < maxPages);

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
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        card.setPreferredSize(new Dimension(920, 90));

        // Left Icon
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(64, 64));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setIcon(createPlaceholderIcon(project.getTitle()));
        card.add(iconLabel, BorderLayout.WEST);

        // Async load icon
        if (project.getIconUrl() != null && !project.getIconUrl().isEmpty()) {
            Icon cached = iconCache.get(project.getIconUrl());
            if (cached != null) {
                iconLabel.setIcon(cached);
            } else {
                executor.submit(() -> {
                    try {
                        BufferedImage img = ModrinthClient.fetchImage(project.getIconUrl());
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

        JLabel downloads = new JLabel("Скачиваний: " + formatDownloads(project.getDownloads()));
        downloads.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        downloads.setForeground(new Color(87, 242, 135));
        titleRow.add(downloads);

        center.add(titleRow, BorderLayout.NORTH);

        String descText = project.getDescription();
        if (descText != null && descText.length() > 115) {
            descText = descText.substring(0, 112) + "...";
        }
        JLabel desc = new JLabel(descText != null ? descText : "");
        desc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        desc.setForeground(new Color(210, 215, 220));
        center.add(desc, BorderLayout.CENTER);

        card.add(center, BorderLayout.CENTER);

        // Right: Install / Select Version Button
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);

        JButton installBtn = new JButton("Установить");
        installBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        installBtn.setBackground(new Color(46, 204, 113));
        installBtn.setForeground(Color.WHITE);
        installBtn.setFocusPainted(false);
        installBtn.setPreferredSize(new Dimension(130, 36));

        // Check if already in active mods folder
        boolean isAlreadyInstalled = checkIfModInstalled(project.getSlug(), modsDir);
        if (isAlreadyInstalled) {
            installBtn.setText("Установлен");
            installBtn.setBackground(new Color(60, 64, 69));
        }

        installBtn.addActionListener(e -> {
            ModrinthVersionDialog dialog = new ModrinthVersionDialog(ModrinthCatalogFrame.this, project, iconLabel.getIcon());
            dialog.setVisible(true);

            // Re-check installation state
            boolean updatedInstalled = checkIfModInstalled(project.getSlug(), modsDir);
            if (updatedInstalled) {
                installBtn.setText("Установлен");
                installBtn.setBackground(new Color(60, 64, 69));
            }
        });

        rightPanel.add(installBtn);
        card.add(rightPanel, BorderLayout.EAST);

        return card;
    }

    private static Icon createPlaceholderIcon(String title) {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int hash = Math.abs(title == null ? 0 : title.hashCode());
        Color c1 = new Color((hash & 0x7F) + 40, ((hash >> 8) & 0x7F) + 40, ((hash >> 16) & 0x7F) + 60);
        Color c2 = new Color(Math.min(255, c1.getRed() + 45), Math.min(255, c1.getGreen() + 45), Math.min(255, c1.getBlue() + 45));

        g2d.setPaint(new GradientPaint(0, 0, c1, size, size, c2));
        g2d.fillRoundRect(2, 2, size - 4, size - 4, 12, 12);

        g2d.setColor(new Color(255, 255, 255, 50));
        g2d.drawRoundRect(2, 2, size - 4, size - 4, 12, 12);

        String initials = getInitials(title);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 20));
        FontMetrics fm = g2d.getFontMetrics();
        int x = (size - fm.stringWidth(initials)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(initials, x, y);
        g2d.dispose();
        return new ImageIcon(img);
    }

    private static String getInitials(String title) {
        if (title == null || title.trim().isEmpty()) return "M";
        String[] parts = title.trim().split("[\\s_-]+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        } else {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
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

    private String formatDownloads(long downloads) {
        if (downloads >= 1_000_000) {
            return String.format("%.1fM", downloads / 1_000_000.0);
        } else if (downloads >= 1_000) {
            return String.format("%.1fK", downloads / 1_000.0);
        }
        return String.valueOf(downloads);
    }
}

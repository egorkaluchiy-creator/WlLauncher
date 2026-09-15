package net.legacylauncher.ui.instances;

import net.legacylauncher.instances.InstanceManager;
import net.legacylauncher.instances.InstanceManagerListener;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.swing.extended.ExtendedFrame;
import net.legacylauncher.util.OS;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;

public class InstanceManagerFrame extends ExtendedFrame implements InstanceManagerListener {
    private final DefaultListModel<String> listModel;
    private final JList<String> instanceList;
    private final JLabel currentLabel;

    public InstanceManagerFrame() {
        setTitle("Менеджер инстансов — WlLauncher");
        try {
            setIconImage(Images.loadIcon("folder-open", 24));
        } catch (Exception ignored) {
        }
        setSize(580, 440);
        setMinimumSize(new Dimension(480, 340));
        setLayout(new BorderLayout(10, 10));

        // Top Banner
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(new EmptyBorder(12, 16, 10, 16));
        topPanel.setBackground(new Color(35, 39, 42));

        JLabel title = new JLabel("Изолированные профили (Мульти-инстансы)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);
        topPanel.add(title, BorderLayout.NORTH);

        currentLabel = new JLabel("Активный профиль: " + formatActiveInstanceName());
        currentLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        currentLabel.setForeground(new Color(87, 242, 135));
        topPanel.add(currentLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // Center List
        listModel = new DefaultListModel<>();
        refreshList();

        instanceList = new JList<>(listModel);
        instanceList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        instanceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        instanceList.setBorder(new EmptyBorder(6, 8, 6, 8));
        instanceList.setCellRenderer(new InstanceCellRenderer());

        // Double click to activate
        instanceList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectActiveInstance();
                }
            }
        });

        add(new JScrollPane(instanceList), BorderLayout.CENTER);

        // Bottom Controls
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        bottomPanel.setBackground(new Color(35, 39, 42));

        JButton selectBtn = new JButton("Сделать активным");
        selectBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        selectBtn.setBackground(new Color(88, 101, 242));
        selectBtn.setForeground(Color.WHITE);
        selectBtn.setFocusPainted(false);
        selectBtn.addActionListener(e -> selectActiveInstance());
        bottomPanel.add(selectBtn);

        JButton createBtn = new JButton("Создать инстанс");
        createBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        createBtn.addActionListener(e -> createNewInstance());
        bottomPanel.add(createBtn);

        JButton openFolderBtn = new JButton("Открыть папку");
        openFolderBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        openFolderBtn.addActionListener(e -> openSelectedFolder());
        bottomPanel.add(openFolderBtn);

        JButton deleteBtn = new JButton("Удалить");
        deleteBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        deleteBtn.setForeground(new Color(231, 76, 60));
        deleteBtn.addActionListener(e -> deleteSelectedInstance());
        bottomPanel.add(deleteBtn);

        add(bottomPanel, BorderLayout.SOUTH);

        InstanceManager.getInstance().addListener(this);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                InstanceManager.getInstance().removeListener(InstanceManagerFrame.this);
            }
        });
    }

    private void refreshList() {
        listModel.clear();
        listModel.addElement("default");
        List<String> instances = InstanceManager.getInstance().listInstances();
        for (String inst : instances) {
            listModel.addElement(inst);
        }
    }

    private String formatActiveInstanceName() {
        String active = InstanceManager.getInstance().getSelectedInstance();
        if ("default".equalsIgnoreCase(active) || active.isEmpty()) {
            return "[Основной каталог] .minecraft";
        }
        return active;
    }

    private void selectActiveInstance() {
        int index = instanceList.getSelectedIndex();
        if (index < 0) {
            JOptionPane.showMessageDialog(this, "Выберите инстанс из списка!", "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = listModel.get(index);
        InstanceManager.getInstance().setSelectedInstance(name);
        currentLabel.setText("Активный профиль: " + formatActiveInstanceName());
        instanceList.repaint();
    }

    private void createNewInstance() {
        String name = JOptionPane.showInputDialog(this, "Введите название нового инстанса (например: Fabric_1.18_Opti, RPG_Modpack):", "Создание инстанса", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            try {
                InstanceManager.getInstance().createInstance(name.trim());
                InstanceManager.getInstance().setSelectedInstance(name.trim());
                currentLabel.setText("Активный профиль: " + formatActiveInstanceName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Ошибка создания: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openSelectedFolder() {
        int index = instanceList.getSelectedIndex();
        if (index < 0) {
            OS.openFolder(InstanceManager.getInstance().getRootDir());
            return;
        }
        String name = listModel.get(index);
        if ("default".equalsIgnoreCase(name)) {
            OS.openFolder(InstanceManager.getInstance().getRootDir());
        } else {
            File folder = new File(InstanceManager.getInstance().getInstancesDir(), name);
            OS.openFolder(folder);
        }
    }

    private void deleteSelectedInstance() {
        int index = instanceList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        String name = listModel.get(index);
        if ("default".equalsIgnoreCase(name)) {
            JOptionPane.showMessageDialog(this, "Основной каталог по умолчанию нельзя удалить!", "Запрещено", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Вы уверены, что хотите удалить инстанс '" + name + "' и все его моды/миры?", "Подтверждение удаления", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            InstanceManager.getInstance().deleteInstance(name);
            currentLabel.setText("Активный профиль: " + formatActiveInstanceName());
        }
    }

    @Override
    public void onActiveInstanceChanged(String oldInstance, String newInstance) {
        SwingUtilities.invokeLater(() -> {
            currentLabel.setText("Активный профиль: " + formatActiveInstanceName());
            instanceList.repaint();
        });
    }

    @Override
    public void onInstancesListChanged() {
        SwingUtilities.invokeLater(() -> {
            refreshList();
            currentLabel.setText("Активный профиль: " + formatActiveInstanceName());
        });
    }

    private static class InstanceCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setBorder(new EmptyBorder(6, 10, 6, 10));
            String name = (String) value;
            String active = InstanceManager.getInstance().getSelectedInstance();
            boolean isActive = ("default".equalsIgnoreCase(name) && "default".equalsIgnoreCase(active))
                    || (name != null && name.equalsIgnoreCase(active));

            String ver = InstanceManager.getInstance().getInstanceVersion(name);
            String verInfo = (ver != null && !ver.trim().isEmpty()) ? " [Версия: " + ver + "]" : " [Версия: не выбрана]";

            String displayName;
            if ("default".equalsIgnoreCase(name)) {
                displayName = "Основной каталог (.minecraft)" + verInfo;
            } else {
                displayName = name + verInfo;
            }

            if (isActive) {
                setText("✓  " + displayName + "  (АКТИВЕН)");
                if (!isSelected) {
                    setForeground(new Color(87, 242, 135));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
            } else {
                setText("    " + displayName);
                if (!isSelected) {
                    setForeground(Color.LIGHT_GRAY);
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
            }
            return this;
        }
    }
}

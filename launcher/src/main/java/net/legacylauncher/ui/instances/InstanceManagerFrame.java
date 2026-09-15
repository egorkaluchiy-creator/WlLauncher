package net.legacylauncher.ui.instances;

import net.legacylauncher.instances.InstanceManager;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.swing.extended.ExtendedFrame;
import net.legacylauncher.util.OS;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

public class InstanceManagerFrame extends ExtendedFrame {
    private final DefaultListModel<String> listModel;
    private final JList<String> instanceList;
    private final JLabel currentLabel;

    public InstanceManagerFrame() {
        setTitle("Менеджер инстансов — WlLauncher");
        try {
            setIconImage(Images.loadIcon("folder", 24));
        } catch (Exception ignored) {
        }
        setSize(550, 420);
        setMinimumSize(new Dimension(450, 320));
        setLayout(new BorderLayout(10, 10));

        // Top Banner
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(new EmptyBorder(12, 16, 8, 16));
        topPanel.setBackground(new Color(35, 39, 42));

        JLabel title = new JLabel("📁 Изолированные профили (Мульти-инстансы)");
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
        add(new JScrollPane(instanceList), BorderLayout.CENTER);

        // Bottom Controls
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        bottomPanel.setBackground(new Color(35, 39, 42));

        JButton selectBtn = new JButton("✓ Выбрать активным");
        selectBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        selectBtn.setBackground(new Color(88, 101, 242));
        selectBtn.setForeground(Color.WHITE);
        selectBtn.setFocusPainted(false);
        selectBtn.addActionListener(e -> selectActiveInstance());
        bottomPanel.add(selectBtn);

        JButton createBtn = new JButton("➕ Создать инстанс");
        createBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        createBtn.addActionListener(e -> createNewInstance());
        bottomPanel.add(createBtn);

        JButton openFolderBtn = new JButton("📂 Открыть папку");
        openFolderBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        openFolderBtn.addActionListener(e -> openSelectedFolder());
        bottomPanel.add(openFolderBtn);

        JButton deleteBtn = new JButton("🗑 Удалить");
        deleteBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        deleteBtn.setForeground(new Color(231, 76, 60));
        deleteBtn.addActionListener(e -> deleteSelectedInstance());
        bottomPanel.add(deleteBtn);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void refreshList() {
        listModel.clear();
        listModel.addElement("📌 [Стандартный] Основной каталог .minecraft");
        List<String> instances = InstanceManager.getInstance().listInstances();
        for (String inst : instances) {
            listModel.addElement(inst);
        }
    }

    private String formatActiveInstanceName() {
        String active = InstanceManager.getInstance().getSelectedInstance();
        if ("default".equalsIgnoreCase(active) || active.isEmpty()) {
            return "[Стандартный] .minecraft";
        }
        return active;
    }

    private void selectActiveInstance() {
        int index = instanceList.getSelectedIndex();
        if (index < 0) {
            JOptionPane.showMessageDialog(this, "Выберите инстанс из списка!", "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (index == 0) {
            InstanceManager.getInstance().setSelectedInstance("default");
        } else {
            String name = listModel.get(index);
            InstanceManager.getInstance().setSelectedInstance(name);
        }
        currentLabel.setText("Активный профиль: " + formatActiveInstanceName());
        JOptionPane.showMessageDialog(this, "Активный инстанс переключен на: " + formatActiveInstanceName(), "Успешно", JOptionPane.INFORMATION_MESSAGE);
    }

    private void createNewInstance() {
        String name = JOptionPane.showInputDialog(this, "Введите название нового инстанса (например: Fabric_1.18_Opti, RPG_Modpack):", "Создание инстанса", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            try {
                InstanceManager.getInstance().createInstance(name.trim());
                refreshList();
                InstanceManager.getInstance().setSelectedInstance(name.trim());
                currentLabel.setText("Активный профиль: " + formatActiveInstanceName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Ошибка создания: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openSelectedFolder() {
        int index = instanceList.getSelectedIndex();
        if (index <= 0) {
            OS.openFolder(InstanceManager.getInstance().getRootDir());
        } else {
            String name = listModel.get(index);
            File folder = new File(InstanceManager.getInstance().getInstancesDir(), name);
            OS.openFolder(folder);
        }
    }

    private void deleteSelectedInstance() {
        int index = instanceList.getSelectedIndex();
        if (index <= 0) {
            JOptionPane.showMessageDialog(this, "Основной каталог по умолчанию нельзя удалить!", "Запрещено", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = listModel.get(index);
        int confirm = JOptionPane.showConfirmDialog(this, "Вы уверены, что хотите удалить инстанс '" + name + "' и все его моды/миры?", "Подтверждение удаления", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            InstanceManager.getInstance().deleteInstance(name);
            refreshList();
            currentLabel.setText("Активный профиль: " + formatActiveInstanceName());
        }
    }
}

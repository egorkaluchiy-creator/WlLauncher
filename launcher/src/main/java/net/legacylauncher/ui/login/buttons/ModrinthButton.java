package net.legacylauncher.ui.login.buttons;

import net.legacylauncher.ui.block.Unblockable;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.LocalizableButton;
import net.legacylauncher.ui.login.LoginForm;
import net.legacylauncher.ui.modrinth.ModManagerFrame;
import net.legacylauncher.ui.modrinth.ModrinthCatalogFrame;
import net.legacylauncher.util.MinecraftUtil;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class ModrinthButton extends LocalizableButton implements Unblockable {
    private final LoginForm lf;

    public ModrinthButton(LoginForm loginform) {
        this.lf = loginform;
        setToolTipText("loginform.button.modrinth");
        setIcon(Images.getIcon24("plug"));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPopupMenu popup = new JPopupMenu();
        JMenuItem catalogItem = new JMenuItem("Каталог модов Modrinth");
        catalogItem.addActionListener(e -> {
            lf.defocus();
            new ModrinthCatalogFrame().showAtCenter();
        });
        popup.add(catalogItem);

        JMenuItem managerItem = new JMenuItem("Менеджер установленных модов");
        managerItem.addActionListener(e -> {
            lf.defocus();
            new ModManagerFrame().showAtCenter();
        });
        popup.add(managerItem);

        popup.addSeparator();

        JMenuItem folderItem = new JMenuItem("Открыть папку mods");
        folderItem.addActionListener(e -> {
            File modsDir = new File(MinecraftUtil.getWorkingDirectory(), "mods");
            if (!modsDir.exists()) modsDir.mkdirs();
            OS.openFolder(modsDir);
        });
        popup.add(folderItem);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(ModrinthButton.this, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(ModrinthButton.this, e.getX(), e.getY());
                }
            }
        });

        addActionListener(e -> {
            lf.defocus();
            new ModrinthCatalogFrame().showAtCenter();
        });
    }

    @Override
    public Insets getInsets() {
        return SwingUtil.magnify(super.getInsets());
    }
}
package net.legacylauncher.ui.login.buttons;

import net.legacylauncher.ui.block.Unblockable;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.LocalizableButton;
import net.legacylauncher.ui.login.LoginForm;
import net.legacylauncher.ui.modrinth.ModrinthCatalogFrame;
import net.legacylauncher.util.SwingUtil;

import java.awt.*;

public class ModrinthButton extends LocalizableButton implements Unblockable {
    private final LoginForm lf;

    public ModrinthButton(LoginForm loginform) {
        this.lf = loginform;
        setToolTipText("loginform.button.modrinth");
        setIcon(Images.getIcon24("plug"));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

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
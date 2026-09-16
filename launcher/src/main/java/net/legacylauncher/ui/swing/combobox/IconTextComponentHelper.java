package net.legacylauncher.ui.swing.combobox;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import java.awt.*;

class IconTextComponentHelper {
    private Border mBorder;
    private Icon mIcon;
    private Border mOrigBorder;
    private final JTextComponent mTextComponent;

    IconTextComponentHelper(JTextComponent component) {
        mTextComponent = component;
        mOrigBorder = component.getBorder();
        mBorder = mOrigBorder;
    }

    Border getBorder() {
        return mBorder;
    }

    void onPaintComponent(Graphics g) {
        if (mIcon != null) {
            int y = (mTextComponent.getHeight() - mIcon.getIconHeight()) / 2;
            mIcon.paintIcon(mTextComponent, g, 16, Math.max(0, y));
        }
    }

    void onSetBorder(Border border) {
        mOrigBorder = border;

        if (mIcon == null) {
            mBorder = border;
        } else {
            Border margin = BorderFactory.createEmptyBorder(0, 16 + mIcon.getIconWidth() + 8, 0, 0);
            mBorder = BorderFactory.createCompoundBorder(border, margin);
        }
    }

    void onSetIcon(Icon icon) {
        mIcon = icon;
        resetBorder();
    }

    private void resetBorder() {
        mTextComponent.setBorder(mOrigBorder);
    }
}
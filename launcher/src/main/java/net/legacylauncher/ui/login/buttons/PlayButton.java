package net.legacylauncher.ui.login.buttons;

import net.legacylauncher.stats.Stats;
import net.legacylauncher.ui.LegacyLauncherFrame;
import net.legacylauncher.ui.block.Blockable;
import net.legacylauncher.ui.block.Blocker;
import net.legacylauncher.ui.images.DelayedIcon;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.LocalizableButton;
import net.legacylauncher.ui.loc.LocalizableMenuItem;
import net.legacylauncher.ui.login.LoginForm;
import net.legacylauncher.ui.notice.Notice;
import net.legacylauncher.ui.notice.NoticeManager;
import net.legacylauncher.ui.notice.NoticeManagerListener;
import net.legacylauncher.ui.notice.NoticePopup;
import net.legacylauncher.ui.swing.extended.BorderPanel;
import net.legacylauncher.util.SwingUtil;
import net.minecraft.launcher.updater.VersionSyncInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class PlayButton extends BorderPanel implements Blockable, LoginForm.LoginStateListener, NoticeManagerListener {
    private static final long serialVersionUID = 6944074583143406549L;
    private PlayButton.PlayButtonState state;
    private final LoginForm loginForm;

    private final LocalizableButton button, promotedNoticeButton;
    private final NoticePopup promotedNoticePopup = new NoticePopup();
    private final LocalizableMenuItem
            hideNotice = new LocalizableMenuItem("notice.action.hide"),
            hidePromoted = new LocalizableMenuItem("notice.promoted.hide.here");

    {
        Images.getIcon16("eye-slash").setup(hideNotice);
        hideNotice.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (promotedNoticePopup.getNotice() != null) {
                    NoticeManager manager = loginForm.scene.getMainPane().getRootFrame().getNotices();
                    manager.setHidden(promotedNoticePopup.getNotice(), true);
                    manager.selectRandom();
                }
            }
        });
        promotedNoticePopup.registerItem(hideNotice);

        Images.getIcon16("eye-slash").setup(hidePromoted);
        hidePromoted.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                NoticeManager manager = loginForm.scene.getMainPane().getRootFrame().getNotices();
                manager.setPromotedAllowed(false);
            }
        });
        promotedNoticePopup.registerItem(hidePromoted);
    }

    private int mouseX, mouseY;
    private final JPopupMenu wrongButtonMenu = new JPopupMenu();

    {
        LocalizableMenuItem wrongButtonItem = new LocalizableMenuItem("loginform.wrongbutton");
        wrongButtonItem.setEnabled(false);
        wrongButtonItem.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                wrongButtonMenu.setVisible(false);
            }
        });
        wrongButtonMenu.add(wrongButtonItem);
    }

    PlayButton(LoginForm lf) {
        loginForm = lf;
        button = new LocalizableButton() {
            private boolean isHovered = false;
            private boolean isPressed = false;

            {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setOpaque(false);
                setPreferredSize(new Dimension(0, SwingUtil.magnify(42)));

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        if (isEnabled()) {
                            isHovered = true;
                            repaint();
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        isHovered = false;
                        isPressed = false;
                        repaint();
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (isEnabled() && SwingUtilities.isLeftMouseButton(e)) {
                            isPressed = true;
                            repaint();
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        isPressed = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int arc = SwingUtil.magnify(12);

                Color topColor;
                Color bottomColor;
                Color glowColor;

                if (!isEnabled()) {
                    topColor = new Color(55, 65, 81);
                    bottomColor = new Color(38, 44, 54);
                    glowColor = null;
                } else if (state == PlayButtonState.CANCEL) {
                    topColor = isPressed ? new Color(185, 28, 28) : (isHovered ? new Color(239, 68, 68) : new Color(220, 38, 38));
                    bottomColor = isPressed ? new Color(153, 27, 27) : (isHovered ? new Color(185, 28, 28) : new Color(153, 27, 27));
                    glowColor = new Color(239, 68, 68, isHovered ? 90 : 40);
                } else if (state == PlayButtonState.INSTALL || state == PlayButtonState.REINSTALL) {
                    topColor = isPressed ? new Color(2, 132, 199) : (isHovered ? new Color(56, 189, 248) : new Color(14, 165, 233));
                    bottomColor = isPressed ? new Color(3, 105, 161) : (isHovered ? new Color(2, 132, 199) : new Color(3, 105, 161));
                    glowColor = new Color(14, 165, 233, isHovered ? 90 : 40);
                } else {
                    topColor = isPressed ? new Color(5, 150, 105) : (isHovered ? new Color(52, 211, 153) : new Color(16, 185, 129));
                    bottomColor = isPressed ? new Color(4, 120, 87) : (isHovered ? new Color(5, 150, 105) : new Color(5, 150, 105));
                    glowColor = new Color(16, 185, 129, isHovered ? 100 : 45);
                }

                if (glowColor != null && isHovered) {
                    g.setColor(glowColor);
                    g.fillRoundRect(0, 0, w, h, arc + 2, arc + 2);
                }

                GradientPaint gp = new GradientPaint(0, 0, topColor, 0, h, bottomColor);
                g.setPaint(gp);
                g.fillRoundRect(0, 0, w, h, arc, arc);

                g.setColor(new Color(255, 255, 255, isHovered ? 55 : 30));
                g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                String text = getText();
                if (text != null && !text.isEmpty()) {
                    Font font = getFont();
                    g.setFont(font);
                    FontMetrics fm = g.getFontMetrics(font);
                    int textX = (w - fm.stringWidth(text)) / 2;
                    int textY = (h + fm.getAscent() - fm.getDescent()) / 2 + (isPressed ? 1 : 0);

                    g.setColor(new Color(0, 0, 0, 100));
                    g.drawString(text, textX + 1, textY + 1);

                    g.setColor(isEnabled() ? Color.WHITE : new Color(156, 163, 175));
                    g.drawString(text, textX, textY);
                }

                g.dispose();
            }
        };
        button.addActionListener(e -> {
            switch (state) {
                case CANCEL:
                    loginForm.stopLauncher();
                    break;
                default:
                    loginForm.startLauncher();
            }

        });
        button.setFont(getFont().deriveFont(Font.BOLD).deriveFont(LegacyLauncherFrame.getFontSize() * 1.5f));
        setCenter(button);

        promotedNoticeButton = new LocalizableButton();
        promotedNoticeButton.setPreferredSize(SwingUtil.magnify(new Dimension(48, 1)));
        promotedNoticeButton.addActionListener(e -> {
            promotedNoticePopup.updateList();
            promotedNoticePopup.show(promotedNoticeButton, promotedNoticeButton.getWidth(), 0);
        });
        onNoticePromoted(null);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1)
                    wrongButtonMenu.show(PlayButton.this, mouseX, mouseY);
            }
        });
        setState(PlayButton.PlayButtonState.PLAY);

        lf.scene.getMainPane().getRootFrame().getNotices().addListener(this, true);
    }

    public PlayButton.PlayButtonState getState() {
        return state;
    }

    public void setState(PlayButton.PlayButtonState state) {
        if (state == null) {
            throw new NullPointerException();
        } else {
            this.state = state;
            button.setText(state.getPath());
            if (state == PlayButton.PlayButtonState.CANCEL) {
                setEnabled(true);
            }

        }
    }

    public void updateState() {
        VersionSyncInfo vs = loginForm.versions.getVersion();
        if (vs != null) {
            boolean installed = vs.isInstalled();
            boolean force = loginForm.checkbox.forceupdate.getState();
            if (!installed) {
                setState(PlayButton.PlayButtonState.INSTALL);
            } else {
                setState(force ? PlayButton.PlayButtonState.REINSTALL : PlayButton.PlayButtonState.PLAY);
            }

        }
    }

    public void loginStateChanged(LoginForm.LoginState state) {
        if (state == LoginForm.LoginState.LAUNCHING) {
            setState(PlayButton.PlayButtonState.CANCEL);
        } else {
            updateState();
            setEnabled(!Blocker.isBlocked(this));
        }

    }

    public void block(Object reason) {
        if (state != PlayButton.PlayButtonState.CANCEL) {
            setEnabled(false);
        }

    }

    public void unblock(Object reason) {
        setEnabled(true);
    }

    @Override
    public void onNoticeSelected(Notice notice) {

    }

    @Override
    public void onNoticePromoted(Notice promotedNotice) {
        if (promotedNotice == null) {
            setEast(null);
        } else {
            setEast(promotedNoticeButton);
            promotedNoticePopup.setNotice(promotedNotice);
            promotedNoticeButton.setIcon(new DelayedIcon(promotedNotice.getImage(), 32, 32));

            Stats.noticeViewed(promotedNotice);
        }

        validate();
        //invalidate();
        repaint();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        button.setText(enabled ? state.getPath() : PlayButtonState.BLOCKED.getPath());
    }

    public enum PlayButtonState {
        REINSTALL("loginform.enter.reinstall"),
        INSTALL("loginform.enter.install"),
        PLAY("loginform.enter"),
        CANCEL("loginform.enter.cancel"),
        BLOCKED("loginform.enter.blocked");

        private final String path;

        PlayButtonState(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}

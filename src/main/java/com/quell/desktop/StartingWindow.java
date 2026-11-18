package com.quell.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** The small window that says the product is starting, so a double-click never seems to do nothing. */
final class StartingWindow {

    private final JWindow window;

    private StartingWindow(JWindow window) {
        this.window = window;
    }

    static StartingWindow show(String product) {
        JWindow window = new JWindow();
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xEAEAEA)),
                BorderFactory.createEmptyBorder(28, 36, 24, 36)));

        JLabel title = new JLabel(product, SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setForeground(new Color(0x0A0A0A));
        Image logo = DesktopWindows.logo(64);
        if (logo != null) {
            title.setIcon(new ImageIcon(logo));
            title.setIconTextGap(14);
        }
        JLabel message = new JLabel("Starting… your browser opens in a few seconds.", SwingConstants.CENTER);
        message.setForeground(new Color(0x666666));
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);

        panel.add(title, BorderLayout.NORTH);
        panel.add(message, BorderLayout.CENTER);
        panel.add(progress, BorderLayout.SOUTH);
        window.setContentPane(panel);
        window.pack();
        window.setSize(Math.max(window.getWidth(), 420), window.getHeight());
        window.setLocationRelativeTo(null);
        window.setAlwaysOnTop(true);
        window.setVisible(true);
        URL icon = StartingWindow.class.getResource("/static/img/logo-256.png");
        if (icon != null) {
            window.setIconImage(new ImageIcon(icon).getImage());
        }
        return new StartingWindow(window);
    }

    void close() {
        SwingUtilities.invokeLater(window::dispose);
    }
}

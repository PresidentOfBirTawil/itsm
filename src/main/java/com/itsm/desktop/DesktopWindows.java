package com.itsm.desktop;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.TrayIcon;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The controls of the running desktop application: an icon in the system
 * tray (the menu bar on a Mac) with "Open" and "Quit", or, where there is
 * no tray, a small window with the same two buttons. And the message shown
 * when the application cannot start.
 */
final class DesktopWindows {

    private static final Logger log = LoggerFactory.getLogger(DesktopWindows.class);

    private DesktopWindows() {
    }

    static void installControls(String product, String url, Path dataDirectory, Runnable quit) {
        SwingUtilities.invokeLater(() -> {
            Image logo = logo(32);
            if (Taskbar.isTaskbarSupported() && logo != null) {
                try {
                    Taskbar.getTaskbar().setIconImage(logo(256));
                } catch (UnsupportedOperationException | SecurityException e) {
                    // Not every desktop allows it.
                }
            }
            macDockBehaviour(url, quit);
            if (SystemTray.isSupported() && logo != null && trayIcon(product, url, dataDirectory, quit, logo)) {
                return;
            }
            controlWindow(product, url, quit);
        });
    }

    private static boolean trayIcon(String product, String url, Path dataDirectory, Runnable quit, Image logo) {
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open " + product);
        open.addActionListener(event -> Browser.open(url));
        MenuItem folder = new MenuItem("Show data folder");
        folder.addActionListener(event -> openFolder(dataDirectory));
        MenuItem exit = new MenuItem("Quit " + product);
        exit.addActionListener(event -> quit.run());
        menu.add(open);
        menu.add(folder);
        menu.addSeparator();
        menu.add(exit);
        TrayIcon icon = new TrayIcon(logo, product + " is running", menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(event -> Browser.open(url));   // double-click
        try {
            SystemTray.getSystemTray().add(icon);
            icon.displayMessage(product + " is running",
                    "It opens in your browser. Use this icon to open it again or to quit.", TrayIcon.MessageType.INFO);
            return true;
        } catch (AWTException e) {
            log.debug("No tray icon: {}", e.toString());
            return false;
        }
    }

    /** Where there is no system tray: a small window that says it is running. Closing it quits. */
    private static void controlWindow(String product, String url, Runnable quit) {
        JFrame frame = new JFrame(product);
        Image logo = logo(256);
        if (logo != null) {
            frame.setIconImage(logo);
        }
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 14));
        panel.add(new JLabel(product + " is running at " + url));
        JButton open = new JButton("Open in browser");
        open.addActionListener(event -> Browser.open(url));
        JButton exit = new JButton("Quit");
        exit.addActionListener(event -> quit.run());
        panel.add(open);
        panel.add(exit);
        frame.setContentPane(panel);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                quit.run();
            }
        });
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /** On a Mac: clicking the Dock icon opens the browser again, and Quit in the Dock quits cleanly. */
    private static void macDockBehaviour(String url, Runnable quit) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        try {
            if (desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
                desktop.addAppEventListener((java.awt.desktop.AppReopenedListener) event -> Browser.open(url));
            }
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                desktop.setQuitHandler((event, response) -> quit.run());
            }
        } catch (UnsupportedOperationException | SecurityException e) {
            // Only some desktops offer these.
        }
    }

    private static void openFolder(Path folder) {
        try {
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            log.info("The data folder is {}", folder);
        }
    }

    /** Shown when the application cannot start; also logged. */
    static void fail(boolean withWindows, String message, Path logFile) {
        String text = message + (logFile == null ? "" : "\n\nThe details are in " + logFile + ".")
                + "\n\nIf another program uses the same port, close it and try again.";
        log.error(text);
        if (withWindows) {
            JOptionPane.showMessageDialog(null, text, "Could not start", JOptionPane.ERROR_MESSAGE);
        }
    }

    static Image logo(int size) {
        URL resource = DesktopWindows.class.getResource("/static/img/logo-256.png");
        if (resource == null) {
            return null;
        }
        Image image = new ImageIcon(resource).getImage();
        return size == 256 ? image : image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
    }
}

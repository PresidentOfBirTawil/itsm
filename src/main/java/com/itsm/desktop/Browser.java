package com.itsm.desktop;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opens a page in the person's own default browser. */
final class Browser {

    private static final Logger log = LoggerFactory.getLogger(Browser.class);

    private Browser() {
    }

    static void open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            log.debug("The desktop could not open the browser: {}", e.toString());
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] command = os.contains("win") ? new String[] {"rundll32", "url.dll,FileProtocolHandler", url}
                : os.contains("mac") ? new String[] {"open", url}
                : new String[] {"xdg-open", url};
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            log.info("Open {} in your browser.", url);
        }
    }
}

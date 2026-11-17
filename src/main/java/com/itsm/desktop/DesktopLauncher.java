package com.itsm.desktop;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Runs the product as a desktop application, the way the installers start
 * it: someone double-clicks the icon and the product opens in their browser.
 * No terminal, no commands, nothing to configure.
 *
 * <ol>
 *   <li>If it is already running, the browser is pointed at it and nothing else happens.</li>
 *   <li>A small "Starting…" window appears straight away, because the server needs a few seconds.</li>
 *   <li>The data is kept in the user's own application-data folder, so it survives updates.</li>
 *   <li>When the server is ready the window closes, the browser opens, and an icon in the
 *       system tray (menu bar on a Mac) offers "Open" and "Quit".</li>
 * </ol>
 *
 * <p>It is used only when the system property {@code app.desktop} is true (the
 * installers set it). The server, the tests and the cloud version never come here.</p>
 */
public final class DesktopLauncher {

    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);
    private static final int PREFERRED_PORT = 8080;
    private static final String PRODUCT = "Quell";

    private DesktopLauncher() {
    }

    /** True when the program was started as a desktop application. */
    public static boolean isRequested() {
        return Boolean.getBoolean("app.desktop");
    }

    public static void launch(Class<?> application, String[] args) {
        Path dataDirectory = dataDirectory();
        boolean withWindows = !GraphicsEnvironment.isHeadless();
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            DesktopWindows.fail(withWindows, "The folder for your data could not be created: " + dataDirectory, null);
            return;
        }

        RunningInstance instance = new RunningInstance(dataDirectory, PRODUCT);
        Optional<Integer> alreadyRunning = instance.find();
        if (alreadyRunning.isPresent()) {
            log.info("{} is already running on port {}: opening it", PRODUCT, alreadyRunning.get());
            Browser.open(url(alreadyRunning.get()));
            return;
        }

        int port = freePort(PREFERRED_PORT);
        System.setProperty("app.data-dir", dataDirectory.toString().replace('\\', '/'));
        System.setProperty("app.port", String.valueOf(port));

        StartingWindow starting = withWindows ? StartingWindow.show(PRODUCT) : null;
        SpringApplication spring = new SpringApplication(application);
        spring.setHeadless(!withWindows);
        spring.setAdditionalProfiles("desktop");
        spring.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> {
            String address = url(port);
            instance.remember(port);
            if (starting != null) {
                starting.close();
            }
            log.info("{} is ready at {} (data in {})", PRODUCT, address, dataDirectory);
            Browser.open(address);
            ConfigurableApplicationContext context = event.getApplicationContext();
            if (withWindows) {
                DesktopWindows.installControls(PRODUCT, address, dataDirectory, () -> quit(context, instance));
            }
        });
        try {
            spring.run(args);
        } catch (RuntimeException | Error e) {
            if (starting != null) {
                starting.close();
            }
            log.error("{} could not start", PRODUCT, e);
            DesktopWindows.fail(withWindows, PRODUCT + " could not start.", dataDirectory.resolve("quell.log"));
            System.exit(1);
        }
    }

    private static void quit(ConfigurableApplicationContext context, RunningInstance instance) {
        instance.forget();
        int code = SpringApplication.exit(context);
        System.exit(code);
    }

    /**
     * Where the data lives: the place each operating system expects
     * applications to keep their data, unless {@code app.data-dir} says otherwise.
     */
    static Path dataDirectory() {
        String chosen = System.getProperty("app.data-dir");
        if (chosen != null && !chosen.isBlank()) {
            return Path.of(chosen).toAbsolutePath();
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            return Path.of(localAppData != null ? localAppData : home, PRODUCT);
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", PRODUCT);
        }
        return Path.of(home, ".local", "share", PRODUCT.toLowerCase(Locale.ROOT));
    }

    /** The preferred port if it is free, otherwise any free port. */
    static int freePort(int preferred) {
        try (ServerSocket socket = new ServerSocket(preferred)) {
            socket.setReuseAddress(true);
            return preferred;
        } catch (IOException taken) {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (IOException e) {
                return preferred;
            }
        }
    }

    static String url(int port) {
        return "http://localhost:" + port + "/";
    }
}

package com.quell.desktop;

import java.nio.file.Path;
import java.util.Optional;

/** Lets the tests reach the desktop launcher's package-private helpers. */
public final class DesktopTestAccess {

    private DesktopTestAccess() {
    }

    public static Path dataDirectory() {
        return DesktopLauncher.dataDirectory();
    }

    public static int freePort(int preferred) {
        return DesktopLauncher.freePort(preferred);
    }

    public static Optional<Integer> runningPort(Path dataDirectory) {
        return new RunningInstance(dataDirectory, "Quell").find();
    }
}

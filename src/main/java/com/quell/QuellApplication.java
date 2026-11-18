package com.quell;

import com.quell.desktop.DesktopLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Runs as a server (the default, and in the cloud) or, when
 * started by the installed desktop application, as a desktop application
 * that opens in the browser ({@link DesktopLauncher}).
 */
@SpringBootApplication
public class QuellApplication {

    public static void main(String[] args) {
        if (DesktopLauncher.isRequested()) {
            DesktopLauncher.launch(QuellApplication.class, args);
        } else {
            SpringApplication.run(QuellApplication.class, args);
        }
    }
}

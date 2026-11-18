package com.quell.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.quell.config.Brand;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The web app manifest: what a browser needs to install Quell as an app
 * (name, icons, colours, start page, shortcuts). Built from the brand
 * settings, so a white-labelled installation installs under its own name.
 */
@RestController
public class AppManifestController {

    static final String MANIFEST_TYPE = "application/manifest+json";
    private static final String DARK = "#09090b";

    private final Brand brand;

    public AppManifestController(Brand brand) {
        this.brand = brand;
    }

    @GetMapping(value = "/manifest.webmanifest", produces = MANIFEST_TYPE)
    public ResponseEntity<Map<String, Object>> manifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", brand.getName());
        manifest.put("short_name", brand.getName());
        manifest.put("description", brand.getName() + ": " + brand.getTagline()
                + ". Report IT problems in seconds; they are prioritised, routed and tracked automatically.");
        manifest.put("id", "/");
        manifest.put("start_url", "/dashboard");
        manifest.put("scope", "/");
        manifest.put("display", "standalone");
        manifest.put("background_color", DARK);
        manifest.put("theme_color", DARK);
        manifest.put("categories", List.of("business", "productivity", "utilities"));
        manifest.put("icons", List.of(
                icon("/img/logo-192.png", "192x192", "any"),
                icon("/img/logo-512.png", "512x512", "any"),
                icon("/img/logo-maskable-512.png", "512x512", "maskable")));
        manifest.put("shortcuts", List.of(
                shortcut("Report a problem", "Report", "/employee/assistant"),
                shortcut("Service status", "Status", "/status"),
                shortcut("Notifications", "Notifications", "/notifications")));
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(manifest);
    }

    private static Map<String, String> icon(String src, String sizes, String purpose) {
        return Map.of("src", src, "sizes", sizes, "type", "image/png", "purpose", purpose);
    }

    private static Map<String, Object> shortcut(String name, String shortName, String url) {
        return Map.of("name", name, "short_name", shortName, "url", url,
                "icons", List.of(Map.of("src", "/img/logo-192.png", "sizes", "192x192")));
    }
}

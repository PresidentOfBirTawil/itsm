package com.quell.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The product's name and the few settings that change how it presents
 * itself. Pages read it as {@code ${@brand.name}}, so renaming the product
 * (or white-labelling it for a customer) is one line in
 * application.properties.
 */
@Component("brand")
public class Brand {

    private final String name;
    private final String tagline;
    private final String version;
    private final boolean demoMode;
    private final String downloadUrl;
    private final String publicUrl;

    public Brand(@Value("${app.brand.name:Quell}") String name,
                 @Value("${app.brand.tagline:Intelligent IT service management}") String tagline,
                 @Value("${app.version:dev}") String version,
                 @Value("${app.demo-mode:true}") boolean demoMode,
                 @Value("${app.download-url:}") String downloadUrl,
                 @Value("${app.public-url:http://localhost:8080}") String publicUrl) {
        this.name = name;
        this.tagline = tagline;
        this.version = version;
        this.demoMode = demoMode;
        this.downloadUrl = downloadUrl;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /** The address people use to reach this installation, for links in messages to other systems. */
    public String getPublicUrl() {
        return publicUrl;
    }

    public String getName() {
        return name;
    }

    public String getTagline() {
        return tagline;
    }

    public String getVersion() {
        return version;
    }

    /** The page with all downloads (the latest release), or empty when there is none. */
    public String getDownloadUrl() {
        return downloadUrl;
    }

    /** The direct link to one download, e.g. "Quell-Setup-Windows.exe". */
    public String download(String file) {
        return downloadUrl + "/download/" + file;
    }

    /** True while the demo accounts exist: the sign-in page then offers one-click demo sign-in. */
    public boolean isDemoMode() {
        return demoMode;
    }
}

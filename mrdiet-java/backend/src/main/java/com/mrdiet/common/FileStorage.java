package com.mrdiet.common;

import com.mrdiet.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/**
 * Saves uploads into the upload folder and returns "uploads/<file>" — the same
 * value format PHP stored in the settings / withdrawals tables.
 * Only image (and PDF for payout proofs) extensions are accepted; the PHP version
 * accepted any extension for logos, which on a PHP server means anyone with admin
 * access could upload a runnable .php file.
 */
@Service
public class FileStorage {

    public static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "gif", "webp", "ico");
    public static final Set<String> IMAGES_AND_PDF = Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf");

    private final Path dir;

    public FileStorage(AppProperties props) {
        this.dir = Path.of(props.uploadDir() == null ? "./uploads" : props.uploadDir()).toAbsolutePath().normalize();
    }

    public String save(MultipartFile file, String prefix, Set<String> allowed) {
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(ext)) {
            throw ApiException.badRequest("Unsupported file type ." + ext + ". Allowed: " + String.join(", ", allowed));
        }
        String safePrefix = prefix.replaceAll("[^A-Za-z0-9_-]", "_");
        String name = safePrefix + "_" + System.currentTimeMillis() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save upload", e);
        }
        return "uploads/" + name;
    }
}

package com.crmgenerator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final Path root;

    public FileStorageService(@Value("${crm.uploads-dir:uploads}") String uploadsDir) throws IOException {
        this.root = Paths.get(uploadsDir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public String store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) return null;
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED.contains(contentType)) {
            throw new IllegalArgumentException("Solo se permiten imágenes JPG, PNG, WEBP o GIF");
        }
        if (file.getSize() > 5_000_000) {
            throw new IllegalArgumentException("La imagen no puede superar 5 MB");
        }
        try {
            Path dir = root.resolve(folder == null ? "misc" : folder.replaceAll("[^a-zA-Z0-9_-]", ""));
            Files.createDirectories(dir);
            String ext = switch (contentType) {
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                default -> ".jpg";
            };
            String name = UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = dir.resolve(name);
            file.transferTo(target);
            // public URL path
            return "/uploads/" + dir.getFileName() + "/" + name;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la imagen: " + e.getMessage(), e);
        }
    }
}

package com.mediassistrover.backend.storage;

import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.FileTooLargeException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class MediaFileValidator {
    public static final long IMAGE_MAX = 10L * 1024 * 1024;
    public static final long VIDEO_MAX = 100L * 1024 * 1024;
    public static final long WAV_MAX = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp",
            "video/mp4", "mp4", "video/webm", "webm", "video/quicktime", "mov",
            "audio/wav", "wav");
    private static final Map<String, Set<String>> ACCEPTED_EXTENSIONS = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp"),
            "video/mp4", Set.of("mp4"),
            "video/webm", Set.of("webm"),
            "video/quicktime", Set.of("mov"),
            "audio/wav", Set.of("wav"));

    public record Validated(String kind, String contentType, String extension, long sizeBytes) { }

    public Validated imageOrVideo(MultipartFile file) {
        Validated value = detect(file);
        long max = "IMAGE".equals(value.kind()) ? IMAGE_MAX : "VIDEO".equals(value.kind()) ? VIDEO_MAX : -1;
        if (max < 0) throw new BusinessRuleException("Only JPG, PNG, WebP, MP4, WebM and MOV files are supported");
        if (value.sizeBytes() > max) throw new FileTooLargeException(value.kind() + " file exceeds " + (max / 1024 / 1024) + " MB");
        return value;
    }

    public Validated wav(MultipartFile file) {
        Validated value = detect(file);
        if (!"audio/wav".equals(value.contentType())) throw new BusinessRuleException("Only WAV audio is supported");
        if (value.sizeBytes() > WAV_MAX) throw new FileTooLargeException("WAV audio exceeds 5 MB");
        return value;
    }

    private Validated detect(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) throw new BusinessRuleException("A non-empty file is required");
        byte[] header = new byte[32];
        int length;
        try (InputStream input = file.getInputStream()) { length = input.read(header); }
        catch (Exception e) { throw new BusinessRuleException("Uploaded file cannot be read"); }
        String detected = signature(header, length);
        if (detected == null) throw new BusinessRuleException("Unsupported or invalid file signature");
        String reported = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (!reported.equals(detected) && !(detected.equals("audio/wav") && reported.equals("audio/x-wav")))
            throw new BusinessRuleException("Declared content type does not match file content");
        String extension = sourceExtension(file.getOriginalFilename());
        if (!ACCEPTED_EXTENSIONS.get(detected).contains(extension))
            throw new BusinessRuleException("File extension does not match file content");
        String kind = detected.startsWith("image/") ? "IMAGE" : detected.startsWith("video/") ? "VIDEO" : "AUDIO";
        return new Validated(kind, detected, EXTENSIONS.get(detected), file.getSize());
    }

    private String sourceExtension(String filename) {
        if (filename == null) return "";
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        return dot < 0 || dot == normalized.length() - 1 ? "" : normalized.substring(dot + 1);
    }

    private String signature(byte[] h, int n) {
        if (n >= 3 && u(h[0]) == 0xff && u(h[1]) == 0xd8 && u(h[2]) == 0xff) return "image/jpeg";
        if (n >= 8 && u(h[0]) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && u(h[4]) == 0x0d && u(h[5]) == 0x0a && u(h[6]) == 0x1a && u(h[7]) == 0x0a) return "image/png";
        if (n >= 12 && ascii(h, 0, "RIFF") && ascii(h, 8, "WEBP")) return "image/webp";
        if (n >= 12 && ascii(h, 0, "RIFF") && ascii(h, 8, "WAVE")) return "audio/wav";
        if (n >= 4 && u(h[0]) == 0x1a && u(h[1]) == 0x45 && u(h[2]) == 0xdf && u(h[3]) == 0xa3) return "video/webm";
        if (n >= 12 && ascii(h, 4, "ftyp")) return ascii(h, 8, "qt  ") ? "video/quicktime" : "video/mp4";
        return null;
    }

    private boolean ascii(byte[] h, int offset, String value) {
        if (h.length < offset + value.length()) return false;
        for (int i = 0; i < value.length(); i++) if (h[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }
    private int u(byte b) { return b & 0xff; }
}

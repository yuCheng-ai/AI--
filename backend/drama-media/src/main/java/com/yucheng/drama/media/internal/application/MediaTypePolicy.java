package com.yucheng.drama.media.internal.application;

import com.yucheng.drama.kernel.api.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.MediaType;

/** A narrow upload admission check, not a decoder or a promise of render readiness. */
final class MediaTypePolicy {
    private static final Set<String> ACCEPTED = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "video/mp4", "video/webm", "audio/mpeg", "audio/mp4",
            "audio/wav", "audio/ogg", "audio/flac", "audio/webm");

    private MediaTypePolicy() {
    }

    static String canonical(String declared) {
        if (declared == null || declared.isBlank()) {
            throw unsupported();
        }
        final MediaType parsed;
        try {
            parsed = MediaType.parseMediaType(declared);
        } catch (IllegalArgumentException exception) {
            throw unsupported();
        }
        String value = (parsed.getType() + "/" + parsed.getSubtype()).toLowerCase(Locale.ROOT);
        if ("audio/x-wav".equals(value)) {
            value = "audio/wav";
        }
        if (!ACCEPTED.contains(value)) {
            throw unsupported();
        }
        return value;
    }

    static void verifyPrefix(String contentType, byte[] prefix) {
        boolean matches = switch (contentType) {
            case "image/jpeg" -> starts(prefix, 0xff, 0xd8, 0xff);
            case "image/png" -> starts(prefix, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "image/gif" -> ascii(prefix, 0, "GIF87a") || ascii(prefix, 0, "GIF89a");
            case "image/webp" -> ascii(prefix, 0, "RIFF") && ascii(prefix, 8, "WEBP");
            case "video/mp4", "audio/mp4" -> prefix.length >= 12 && ascii(prefix, 4, "ftyp");
            case "video/webm", "audio/webm" -> starts(prefix, 0x1a, 0x45, 0xdf, 0xa3);
            case "audio/wav" -> ascii(prefix, 0, "RIFF") && ascii(prefix, 8, "WAVE");
            case "audio/ogg" -> ascii(prefix, 0, "OggS");
            case "audio/flac" -> ascii(prefix, 0, "fLaC");
            case "audio/mpeg" -> ascii(prefix, 0, "ID3") || isMpegFrame(prefix);
            default -> false;
        };
        if (!matches) {
            throw new BusinessException(415, "MEDIA_CONTENT_MISMATCH",
                    "文件头与声明的媒体类型不符；不接受 HTML、SVG 或未知格式。");
        }
    }

    private static boolean isMpegFrame(byte[] prefix) {
        return prefix.length >= 4 && (prefix[0] & 0xff) == 0xff
                && (prefix[1] & 0xe0) == 0xe0
                && (prefix[1] & 0x18) != 0x08
                && (prefix[1] & 0x06) != 0
                && (prefix[2] & 0xf0) != 0xf0
                && (prefix[2] & 0x0c) != 0x0c;
    }

    private static boolean starts(byte[] prefix, int... expected) {
        if (prefix.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((prefix[index] & 0xff) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] prefix, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (prefix.length < offset + bytes.length) {
            return false;
        }
        for (int index = 0; index < bytes.length; index++) {
            if (prefix[offset + index] != bytes[index]) {
                return false;
            }
        }
        return true;
    }

    private static BusinessException unsupported() {
        return new BusinessException(415, "MEDIA_TYPE_UNSUPPORTED",
                "仅接受 JPEG、PNG、WebP、GIF、MP4、WebM、MP3、WAV、Ogg、FLAC 媒体文件，并需提供正确的 Content-Type。");
    }
}

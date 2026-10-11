package com.yucheng.drama.media.internal.infrastructure.storage;

import com.yucheng.drama.media.internal.application.port.ObjectStorage;
import com.yucheng.drama.media.internal.application.port.StorageLimitExceededException;
import com.yucheng.drama.media.internal.config.MediaStorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local disk backend. Descriptor-relative operations never follow symlinks, including in
 * the configured root path. This backend deliberately requires SecureDirectoryStream.
 */
@Component
@Profile("!migrate")
public final class LocalObjectStorage implements ObjectStorage {
    public static final String BACKEND_ID = "local-disk";
    public static final String UPLOAD_BUCKET = "uploads";
    private final Path root;

    public LocalObjectStorage(MediaStorageProperties properties) {
        this.root = Path.of(properties.local().root()).toAbsolutePath().normalize();
        try {
            createDirectoriesWithoutSymlinks(root.resolve(UPLOAD_BUCKET));
            try (var ignored = openBucket(UPLOAD_BUCKET)) {
                // Fail at startup rather than silently using an unsafe filesystem provider.
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot initialize secure local media storage.", exception);
        }
    }

    @Override
    public String backendId() {
        return BACKEND_ID;
    }

    @Override
    public StoredObject put(String bucket, InputStream input, long maximumBytes) throws IOException {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("The upload size limit must be positive.");
        }
        var objectKey = UUID.randomUUID().toString();
        var name = objectName(objectKey);
        var digest = sha256();
        long size = 0;
        boolean created = false;
        try (var directory = openBucket(bucket)) {
            try {
                Set<OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                try (var channel = directory.newByteChannel(name, options);
                     var output = Channels.newOutputStream(channel)) {
                    created = true;
                    var buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (count == 0) {
                            continue;
                        }
                        if (count > maximumBytes - size) {
                            throw new StorageLimitExceededException();
                        }
                        output.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                        size += count;
                    }
                    output.flush();
                    if (channel instanceof FileChannel fileChannel) {
                        fileChannel.force(true);
                    }
                }
                if (size == 0) {
                    throw new IOException("An empty object cannot be stored.");
                }
                // This UUID object remains unpublished until the metadata transaction commits.
                return new StoredObject(BACKEND_ID, bucket, objectKey, size,
                        HexFormat.of().formatHex(digest.digest()));
            } catch (IOException | RuntimeException exception) {
                if (created) {
                    try {
                        directory.deleteFile(name);
                    } catch (NoSuchFileException ignored) {
                        // Already absent; cleanup is complete.
                    } catch (IOException cleanupFailure) {
                        exception.addSuppressed(cleanupFailure);
                    }
                }
                throw exception;
            }
        }
    }

    @Override
    public InputStream open(String bucket, String objectKey) throws IOException {
        var name = objectName(objectKey);
        try (var directory = openBucket(bucket)) {
            requireRegularFile(directory, name);
            Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            return Channels.newInputStream(directory.newByteChannel(name, options));
        }
    }

    @Override
    public Optional<ObjectStat> stat(String bucket, String objectKey) throws IOException {
        try (var directory = openBucket(bucket)) {
            try {
                var attributes = requireRegularFile(directory, objectName(objectKey));
                return Optional.of(new ObjectStat(attributes.size(), attributes.lastModifiedTime().toInstant()));
            } catch (NoSuchFileException exception) {
                return Optional.empty();
            }
        }
    }

    @Override
    public void delete(String bucket, String objectKey) throws IOException {
        try (var directory = openBucket(bucket)) {
            var name = objectName(objectKey);
            try {
                requireRegularFile(directory, name);
                directory.deleteFile(name);
            } catch (NoSuchFileException ignored) {
                // Idempotent deletion of a newly created, exclusively owned object.
            }
        }
    }

    private SecureDirectoryStream<Path> openBucket(String bucket) throws IOException {
        if (!UPLOAD_BUCKET.equals(bucket)) {
            throw new IOException("Unsupported local storage bucket.");
        }
        try (var rootDirectory = openSecureAbsoluteDirectory(root)) {
            return rootDirectory.newDirectoryStream(Path.of(bucket), LinkOption.NOFOLLOW_LINKS);
        }
    }

    private static SecureDirectoryStream<Path> openSecureAbsoluteDirectory(Path absolute) throws IOException {
        DirectoryStream<Path> initial = Files.newDirectoryStream(absolute.getRoot());
        if (!(initial instanceof SecureDirectoryStream<Path> secureInitial)) {
            initial.close();
            throw new IOException("The local filesystem does not support secure directory operations.");
        }
        SecureDirectoryStream<Path> current = secureInitial;
        try {
            for (Path segment : absolute) {
                var next = current.newDirectoryStream(segment, LinkOption.NOFOLLOW_LINKS);
                current.close();
                current = next;
            }
            return current;
        } catch (IOException | RuntimeException exception) {
            current.close();
            throw exception;
        }
    }

    private static void createDirectoriesWithoutSymlinks(Path absolute) throws IOException {
        Path current = absolute.getRoot();
        for (Path segment : absolute) {
            current = current.resolve(segment);
            try {
                Files.createDirectory(current);
            } catch (FileAlreadyExistsException ignored) {
                // Inspect existing entries without following links.
            }
            var attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
                throw new IOException("The local storage path must contain directories only, without symlinks.");
            }
        }
    }

    private static BasicFileAttributes requireRegularFile(SecureDirectoryStream<Path> directory, Path name)
            throws IOException {
        var view = directory.getFileAttributeView(name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        var attributes = view.readAttributes();
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IOException("Stored objects must be regular files, without symlinks.");
        }
        return attributes;
    }

    private static Path objectName(String objectKey) throws IOException {
        try {
            if (!UUID.fromString(objectKey).toString().equals(objectKey)) {
                throw new IllegalArgumentException("Noncanonical object key.");
            }
            return Path.of(objectKey);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IOException("Invalid local object key.", exception);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The Java runtime does not provide SHA-256.", exception);
        }
    }
}

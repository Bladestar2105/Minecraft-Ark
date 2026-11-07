package de.ark.wardrobe;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;

final class AtomicFiles {
    static void write(Path path,String text) throws IOException {
        Files.createDirectories(path.getParent());
        try { Files.setPosixFilePermissions(path.getParent(),PosixFilePermissions.fromString("rwx------")); }
        catch (UnsupportedOperationException ignored) { }
        Path temp=Files.createTempFile(path.getParent(),".wardrobe-",".tmp");
        try {
            try { Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException ignored) { }
            Files.writeString(temp,text,StandardCharsets.UTF_8);
            try (FileChannel file=FileChannel.open(temp,StandardOpenOption.WRITE)) { file.force(true); }
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}

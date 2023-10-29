package com.jih10157.voicemodmaker;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class Silent {
    public static void changeSilent(Path silentPath, Path dest) throws IOException {
        // 폴더 내의 모든 파일을 순회합니다.
        Files.walkFileTree(dest, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Files.copy(silentPath, file, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

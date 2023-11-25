package com.jih10157.voicemodmaker.util;

import com.jih10157.voicemodmaker.Main;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class WwiseUtil {

    private static final ExecutorService service = Executors.newFixedThreadPool(16);

    public static void unpack(Path tool, Path packFolder, Path dest) throws IOException, InterruptedException {
        long mills = System.currentTimeMillis();
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> list = Files.list(packFolder)) {
            list.filter(path -> path.getFileName().toString().endsWith(".pck")).forEach(paths::add);
        }
        System.out.println("pck 파일: " + paths.size() + "개");
        if (paths.isEmpty()) {
            return;
        }

        Set<Future<Integer>> result = paths.stream().map(path -> service.submit(() -> new ProcessBuilder()
                .command(tool.resolve("quickbms.exe").toString(), "-q", "-k", "-Y",
                        tool.resolve("wwise_pck_extractor.bms").toString(), path.toString(),
                        dest.resolve(getFileName(path)).toString()).start().waitFor())).collect(Collectors.toSet());

        while (!result.stream().allMatch(Future::isDone)) {
            System.out.println("진행중... " + result.stream().filter(Future::isDone).count() + "/" + result.size());
            Main.waitFor(1, TimeUnit.SECONDS, () -> result.parallelStream().allMatch(Future::isDone));
        }
        System.out.println(result.size() + "개의 파일이 언팩됨. 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");

//        Set<String> hashes = new HashSet<>();
//        JSONObject obj = new JSONObject();
//        try (Stream<Path> list = Files.walk(dest)) {
//            list.filter(path -> path.getFileName().toString().endsWith(".wem"))
//                    .map(path -> new AbstractMap.SimpleEntry<>(getFileName(path), path.getParent().getFileName().toString()))
//                    .forEach(e -> {
//                        obj.put(e.getKey(), e.getValue());
//                        hashes.add(e.getKey());
//                    });
//        }
//        Files.write(dest.resolve("hash-to-folder-jp.json"), obj.toJSONString().getBytes(StandardCharsets.UTF_8),
//                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
//        Files.write(dest.resolve("list.txt"), String.join("\n", hashes).getBytes(StandardCharsets.UTF_8),
//                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    }

    public static Set<Future<Integer>> wemToWav(Path tool, Path wemPath, Path dest) throws IOException {
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> list = Files.walk(wemPath)) {
            list.filter(path -> path.getFileName().toString().endsWith(".wem")).forEach(paths::add);
        }
        System.out.println("wem 파일: " + paths.size() + "개");
        if (paths.isEmpty()) {
            return new HashSet<>();
        }
        Path rootFolder = wemPath.subpath(0, 3);
        return paths.stream().map(path -> {
            Path wavFile = changeExtension(dest.resolve(rootFolder.relativize(path)), ".wav");
            try {
                return wemToWavFile(tool, path, wavFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
    }

    public static Future<Integer> wemToWavFile(Path tool, Path wemFile, Path destFile) throws IOException {
        Files.createDirectories(destFile.getParent());
        if (!Files.exists(destFile)) {
            return service.submit(() -> new ProcessBuilder()
                    .command(tool.resolve("vgmstream-win64").resolve("vgmstream-cli.exe").toString(), "-o",
                            destFile.toString(), wemFile.toString()).start().waitFor());
        } else {
            return CompletableFuture.completedFuture(0);
        }
    }

    public static void wavToWem(Path toolFolder, Path wavFolder, Path destFolder, Path tempFolder) throws IOException, InterruptedException {
        Path wwiseConsole = null;
        Path pathCache = toolFolder.resolve("WwisePath.txt");
        if (Files.exists(pathCache)) {
            List<String> strs = Files.readAllLines(pathCache, StandardCharsets.UTF_8);
            if (!strs.isEmpty()) {
                Path file = Paths.get(strs.get(0));
                if (Files.exists(file) && Files.isRegularFile(file)) {
                    wwiseConsole = file;
                } else {
                    System.out.println("WwiseConsole.exe 파일이 아닌 것 같습니다. 경로: " + strs.get(0));
                }
            }
        }
        if (wwiseConsole == null) {
            Optional<Path> wwiseFolder = StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), true)
                    .map(p -> p.resolve("Program Files (x86)\\Audiokinetic")).filter(Files::exists)
                    .map(p -> {
                        try (Stream<Path> folders = Files.list(p)) {
                            return folders.filter(f -> f.getFileName().toString().startsWith("Wwise"))
                                    .findFirst();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).filter(Optional::isPresent).map(Optional::get).findFirst();
            if (wwiseFolder.isPresent()) {
                Path file = wwiseFolder.get().resolve("Authoring\\x64\\Release\\bin\\WwiseConsole.exe");
                if (Files.exists(file) && Files.isRegularFile(file)) {
                    wwiseConsole = file;
                    Files.write(pathCache, file.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                }
            }
        }

        if (wwiseConsole == null) {
            System.out.println("Wwise 프로그램을 찾을 수 없습니다.");
            System.out.println("프로그램 설치 후에도 지속된다면");
            System.out.println("tool\\WwisePath.txt 파일을 생성해 WwiseConsole.exe 파일의 경로를 입력해주세요.");
            System.out.println("예시: C:\\Program Files (x86)\\Audiokinetic\\Wwise 2022.1.8.8316\\Authoring\\x64\\Release\\bin");
            return;
        }

        System.out.println("감지된 Wwise 프로그램: " + wwiseConsole);

        new ProcessBuilder()
                .command(wwiseConsole.toString(), "create-new-project",
                        tempFolder.resolve("wavtowem\\wavtowem.wproj").toAbsolutePath().toString(), "--quiet")
                .inheritIO().start().waitFor();

        System.out.println("임시 폴더에 새 프로젝트 생성 완료");

        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ExternalSourcesList SchemaVersion=\"1\" Root=\"");
        sb.append(wavFolder.toAbsolutePath()).append("\">");
        try (Stream<Path> list = Files.list(wavFolder)) {
            list.map(p -> p.getFileName().toString()).filter(s -> s.endsWith(".wav")).forEach(s -> {
                sb.append("\n\t<Source Path=\"").append(s).append("\" Conversion=\"ADPCM As Input\"/>");
            });
        }
        sb.append("\n</ExternalSourcesList>");

        Path wresources = tempFolder.resolve("list.wresources");
        Files.write(wresources, sb.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);

        System.out.println("프로젝트에 모든 wav 파일 경로 설정 완료");

        new ProcessBuilder()
                .command(wwiseConsole.toString(), "convert-external-source",
                        tempFolder.resolve("wavtowem\\wavtowem.wproj").toAbsolutePath().toString(),
                        "--source-file", wresources.toAbsolutePath().toString(), "--output", destFolder.toAbsolutePath().toString(), "--quiet"
                ).inheritIO().start().waitFor();

        System.out.println("wem 파일 변환 완료");

        try (Stream<Path> wem = Files.list(destFolder.resolve("Windows"))) {
            wem.filter(p -> {
                if (p.getFileName().toString().endsWith(".wem")) {
                    return true;
                } else {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return false;
                }
            }).forEach(p -> {
                try {
                    Files.move(p, destFolder.resolve(p.getFileName()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Files.delete(destFolder.resolve("Windows"));
    }

    public static Path changeExtension(Path path, String extension) {
        String name = path.getFileName().toString();
        String origin = name.substring(0, name.lastIndexOf('.'));
        return path.getParent().resolve(origin + extension);
    }

    public static String getFileName(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }
}

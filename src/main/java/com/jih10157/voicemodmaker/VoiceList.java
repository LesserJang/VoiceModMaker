package com.jih10157.voicemodmaker;

import com.jih10157.voicemodmaker.util.MD5;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public class VoiceList {

    private static final Path AUDIO_ASSETS_PATH = Paths.get("assets");
    private static final Path AUDIO_CACHE_PATH = Paths.get("cache");
    private static final Path AUDIO_CACHE_RAW_PATH = AUDIO_CACHE_PATH.resolve("raw");
    private static final Path AUDIO_CACHE_MAPPED_PATH = AUDIO_CACHE_PATH.resolve("mapped");
    private static final Path WORK_PATH = Paths.get("work");
    private static final Path TOOL_PATH = Paths.get("tool");
    private static final Path TEMP_PATH = Paths.get("temp");
    private static final Path RESULT_PATH = Paths.get("result");

    private static final AtomicInteger i = new AtomicInteger();

    // 1. 실제 존재하는 해시값들 추출
    // 2. 각 보이스 wem 파일의 원래 폴더 위치 추출
    // 3. 각 보이스 파일의 해시값 추출
    public static void generate() throws IOException {

        long mills = System.currentTimeMillis();
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> list = Files.list(AUDIO_ASSETS_PATH)) {
            list.filter(path -> path.getFileName().toString().endsWith(".pck")).forEach(paths::add);
        }
        System.out.println("pck 파일: " + paths.size() + "개");
        if (paths.isEmpty()) {
            return;
        }

        Map<String, JSONObject> result = new ConcurrentHashMap<>();

        paths.parallelStream().forEach(pckPath -> {
            try {
                String pckFolderName = getFileName(pckPath);
                Map<String, String> checksumMap = process(pckPath);
                checksumMap.forEach((hash, checksum) -> {
                    JSONObject entry = new JSONObject();
                    entry.put("folder", pckFolderName);
                    entry.put("checksum", checksum);
                    result.put(hash, entry);
                });
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println(System.currentTimeMillis() - mills);
        if (!Files.exists(WORK_PATH)) {
            Files.createDirectories(WORK_PATH);
        }
        Files.write(WORK_PATH.resolve("voicelist.json"), new JSONObject(result).toJSONString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        System.out.println(System.currentTimeMillis() - mills);
    }

    public static Map<String, String> process(Path pckFile) throws IOException, InterruptedException {
//        int id = i.getAndIncrement();
        Path targetFolder = AUDIO_CACHE_RAW_PATH.resolve(getFileName(pckFile));
        int code = new ProcessBuilder()
                .command(TOOL_PATH.resolve("quickbms.exe").toString(), "-q", "-k", "-Y",
                        TOOL_PATH.resolve("wwise_pck_extractor.bms").toString(), pckFile.toString(),
                        targetFolder.toString()).start().waitFor();
//        System.out.println("id: " + id + ", pckFile: " + pckFile + ", code: " + code + ", folder: " + targetFolder);


        List<Path> wemPaths = new ArrayList<>();
        try (Stream<Path> list = Files.list(targetFolder)) {
            list.filter(path -> path.getFileName().toString().endsWith(".wem")).forEach(wemPaths::add);
        }
//        System.out.println("id: " + id + ", wem size: " + wemPaths.size());

        Map<String, String> checksumMap = new ConcurrentHashMap<>();
        wemPaths.parallelStream().forEach(path -> {
//            System.out.println("id: " + id + ", path: " + path);
            try {
                Path dest = changeExtension(path, ".wav");
                new ProcessBuilder()
                        .command(TOOL_PATH.resolve("vgmstream-win64").resolve("vgmstream-cli.exe").toString(),
                                "-o", dest.toString(), path.toString()).start().waitFor();

                String checksum = MD5.checksum(dest);
                checksumMap.put(getFileName(dest), checksum);
                Files.delete(dest);
                Files.delete(path);
//                System.out.println("id: " + id + ", completed path: " + path);
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
//        System.out.println("id: " + id + ", completed");
        return checksumMap;
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

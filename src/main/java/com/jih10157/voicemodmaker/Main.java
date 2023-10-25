package com.jih10157.voicemodmaker;

import com.jih10157.voicemodmaker.util.MD5;
import com.jih10157.voicemodmaker.util.Unzip;
import com.jih10157.voicemodmaker.util.WwiseUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jih10157.voicemodmaker.util.WwiseUtil.wemToWavFile;

@SuppressWarnings({"unchecked", "rawtypes"})
public class Main {

    private static final Path AUDIO_ASSETS_PATH = Paths.get("assets");
    private static final Path AUDIO_CACHE_PATH = Paths.get("cache");
    private static final Path AUDIO_CACHE_RAW_PATH = AUDIO_CACHE_PATH.resolve("raw");
    private static final Path AUDIO_CACHE_MAPPED_PATH = AUDIO_CACHE_PATH.resolve("mapped");
    private static final Path WORK_PATH = Paths.get("work");
    private static final Path TOOL_PATH = Paths.get("tool");
    private static final Path TEMP_PATH = Paths.get("temp");
    private static final Path RESULT_PATH = Paths.get("result");
    private static final int DEFAULT_MODE = 0;
    private static final int VOICE_EXTRACT_MODE = 1;
    private static final int VOICE_EXTRACT_PATH_MODE = 2;
    private static final int VOICE_EXTRACT_CHARACTER_MODE = 3;
    private static final int VOICE_EXTRACT_CHARACTER_PATH_MODE_FIRST = 4;
    private static final int VOICE_EXTRACT_CHARACTER_PATH_MODE_SECOND = 5;
    private static final int VOICE_MOD_MAKE_MODE = 6;
    private static JSONObject voiceSetsJson = null;
    private static JSONObject hashToFolderJson = null;
    private static JSONObject pathToHashJson = null;
    private static int mode = DEFAULT_MODE;
    private static String character = "";


    public static void main(String[] args) throws IOException, InterruptedException {
        setupFolder();
        setupTool();
        setupCache();

        System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 나가기");

        Scanner scanner = new Scanner(System.in);
        loop:
        while (scanner.hasNextLine()) {
            String str = scanner.nextLine();
            switch (mode) {
                case DEFAULT_MODE:
                    switch (str) {
                        case "1":
                            mode = VOICE_EXTRACT_MODE;
                            System.out.println("1. 캐릭터 기반 추출 2. 폴더 또는 파일 기반 추출 3. 캐릭터 + 폴더 4. 이전으로");
                            break;
                        case "2":
                            mode = VOICE_MOD_MAKE_MODE;
                            System.out.println("보이스 모드의 이름을 입력해주세요.");
                            break;
                        case "3":
                            break loop;
                        default:
                            System.out.println("\n1. 보이스 추출 2. 보이스 모드 생성 3. 나가기");
                            break;
                    }
                    break;
                case VOICE_EXTRACT_MODE:
                    switch (str) {
                        case "1":
                            mode = VOICE_EXTRACT_CHARACTER_MODE;
                            System.out.println("한글로 캐릭터 이름을 입력해주세요.");
                            System.out.println("ex. '타이나리' '방랑자' '카에데하라 카즈하'");
                            break;
                        case "2":
                            mode = VOICE_EXTRACT_PATH_MODE;
                            System.out.println("폴더 또는 파일을 입력해주세요.");
                            System.out.println("ex. 'VO_friendship\\VO_tighnari' 'VO_LQ' 'VO_gameplay\\VO_neuvillette\\vo_neuvillette_chest_open_01.wem'");
                            break;
                        case "3":
                            mode = VOICE_EXTRACT_CHARACTER_PATH_MODE_FIRST;
                            System.out.println("한글로 캐릭터 이름을 입력해주세요.");
                            System.out.println("ex. '타이나리' '방랑자' '카에데하라 카즈하'");
                            break;
                        case "4":
                            mode = DEFAULT_MODE;
                            System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 나가기");
                            break;
//                        case "5":
//                            mode = DEFAULT_MODE;
//                            System.out.println("!!!");
//                            loadPath("");
//                            generateChecksumJson(WORK_PATH);
//                            break;
                        default:
                            System.out.println("\n1. 캐릭터 기반 추출 2. 폴더 또는 파일 기반 추출 3. 이전으로");
                            break;
                    }
                    break;
                case VOICE_EXTRACT_CHARACTER_MODE:
                    loadCharacter(str);
                    mode = DEFAULT_MODE;
                    System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 나가기");
                    break;
                case VOICE_EXTRACT_PATH_MODE:
                    loadPath(str);
                    mode = DEFAULT_MODE;
                    System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 나가기");
                    break;
                case VOICE_EXTRACT_CHARACTER_PATH_MODE_FIRST:
                    character = str;
                    mode = VOICE_EXTRACT_CHARACTER_PATH_MODE_SECOND;
                    System.out.println("폴더를 입력해주세요.");
                    System.out.println("ex. 'VO_friendship\\VO_tighnari' 'VO_LQ'");
                    break;
                case VOICE_EXTRACT_CHARACTER_PATH_MODE_SECOND:
                    loadCharacterInFolder(character, str);
                    mode = DEFAULT_MODE;
                    System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 나가기");
                    break;
                case VOICE_MOD_MAKE_MODE:
                    createVoiceMod(str);
                    mode = DEFAULT_MODE;
                    System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 나가기");
            }
        }
        scanner.close();
        System.exit(0);
    }

    public static JSONObject getVoiceSetsJson() {
        if (voiceSetsJson == null) {
            System.out.println("voicesets.json 을 불러옵니다.");
            JSONParser parser = new JSONParser();
            try {
                Path mappingFile = TOOL_PATH.resolve("mapping").resolve("voicesets.json");
                voiceSetsJson = (JSONObject) parser.parse(Files.newBufferedReader(mappingFile, StandardCharsets.UTF_8));
            } catch (ParseException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return voiceSetsJson;
    }

    public static JSONObject getHashToFolderJson() {
        if (hashToFolderJson == null) {
            System.out.println("hash-to-folder.json 을 불러옵니다.");
            JSONParser parser = new JSONParser();
            try {
                Path mappingFile = TOOL_PATH.resolve("mapping").resolve("hash-to-folder.json");
                hashToFolderJson = (JSONObject) parser.parse(Files.newBufferedReader(mappingFile, StandardCharsets.UTF_8));
            } catch (ParseException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return hashToFolderJson;
    }

    public static JSONObject getPathToHashJson() {
        if (pathToHashJson == null) {
            System.out.println("path-to-hash.json 을 불러옵니다.");
            JSONParser parser = new JSONParser();
            try {
                Path mappingFile = TOOL_PATH.resolve("mapping").resolve("path-to-hash.json");
                pathToHashJson = (JSONObject) parser.parse(Files.newBufferedReader(mappingFile, StandardCharsets.UTF_8));
            } catch (ParseException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return pathToHashJson;
    }

    public static void setupCache() throws IOException {
        System.out.println("캐시 준비중");
        try (Stream<Path> list = Files.list(AUDIO_CACHE_MAPPED_PATH)) {
            if (list.findAny().isPresent()) {
                System.out.println("캐시가 존재합니다.");
                return;
            }
        }

        try (Stream<Path> list = Files.list(AUDIO_ASSETS_PATH)) {
            if (!list.findAny().isPresent()) {
                System.out.println("assets 폴더가 비어있습니다.");
                JOptionPane.showMessageDialog(null, "assets 폴더가 비어있습니다.", ":<", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
                return;
            }
        }

        boolean existsRaw = false;
        try (Stream<Path> list = Files.list(AUDIO_CACHE_RAW_PATH)) {
            if (list.findAny().isPresent()) {
                existsRaw = true;
            }
        }

        if (!existsRaw) {
            System.out.println("raw 폴더에 pck 파일을 풉니다.");
            try {
                WwiseUtil.unpack(TOOL_PATH, AUDIO_ASSETS_PATH, AUDIO_CACHE_RAW_PATH);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        JSONObject mapping = (JSONObject) getVoiceSetsJson().get("mapping");

        System.out.println("캐시 폴더에 wem 파일을 매핑합니다.");
        try (Stream<Path> list = Files.walk(AUDIO_CACHE_RAW_PATH)) {
            list.filter(path -> path.getFileName().toString().endsWith(".wem")).forEach(path -> {
                String hash = getFileName(path);
                if (mapping.containsKey(hash)) {
                    JSONObject obj = (JSONObject) mapping.get(hash);
                    Path dest = AUDIO_CACHE_MAPPED_PATH.resolve((String) obj.get("path"));
                    try {
                        Files.createDirectories(dest.getParent());
                        Files.move(path, dest, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
//                } else {
//                    System.out.println("매핑파일에 존재하지 않는 해시값이 있습니다. 해시: " + hash);
//                }
            });
        }
    }

    public static void setupFolder() throws IOException {
        if (Files.notExists(AUDIO_ASSETS_PATH)) {
            Files.createDirectories(AUDIO_ASSETS_PATH);
        }
        if (Files.notExists(AUDIO_CACHE_MAPPED_PATH)) {
            Files.createDirectories(AUDIO_CACHE_MAPPED_PATH);
        }
        if (Files.notExists(AUDIO_CACHE_RAW_PATH)) {
            Files.createDirectories(AUDIO_CACHE_RAW_PATH);
        }
        if (Files.notExists(WORK_PATH)) {
            Files.createDirectories(WORK_PATH);
        }
        if (Files.notExists(TOOL_PATH)) {
            Files.createDirectories(TOOL_PATH);
        }
        if (Files.notExists(RESULT_PATH)) {
            Files.createDirectories(RESULT_PATH);
        }
        clearTemp();
    }

    public static void clearTemp() throws IOException {
        if (Files.notExists(TEMP_PATH)) {
            Files.createDirectories(TEMP_PATH);
        } else {
            try (Stream<Path> list = Files.walk(TEMP_PATH)) {
                list.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    public static void setupTool() throws IOException {
        try (Stream<Path> list = Files.list(TOOL_PATH)) {
            if (list.findAny().isPresent()) {
                return;
            }
        }

        InputStream res = Main.class.getResourceAsStream("/tool.zip");
        if (res == null) {
            throw new RuntimeException("tool.zip 파일을 찾을 수 없습니다.");
        }
        Unzip.unzipFile(res, TOOL_PATH);

        Path mappingFolder = TOOL_PATH.resolve("mapping");
        if (Files.notExists(mappingFolder)) {
            Files.createDirectories(mappingFolder);
        }
        Path a = mappingFolder.resolve("voicesets.json");
        if (Files.notExists(a)) {
            InputStream stream = Main.class.getResourceAsStream("/voicesets.json");
            if (stream == null) {
                throw new RuntimeException("voicesets.json 파일을 찾을 수 없습니다.");
            }
            Files.copy(stream, a);
        }
        Path b = mappingFolder.resolve("hash-to-folder.json");
        if (Files.notExists(b)) {
            InputStream stream = Main.class.getResourceAsStream("/hash-to-folder.json");
            if (stream == null) {
                throw new RuntimeException("hash-to-folder.json 파일을 찾을 수 없습니다.");
            }
            Files.copy(stream, b);
        }
        Path c = mappingFolder.resolve("path-to-hash.json");
        if (Files.notExists(c)) {
            InputStream stream = Main.class.getResourceAsStream("/path-to-hash.json");
            if (stream == null) {
                throw new RuntimeException("path-to-hash.json 파일을 찾을 수 없습니다.");
            }
            Files.copy(stream, c);
        }
    }

    public static void loadCharacter(String name) {
        System.out.println("다음 캐릭터의 보이스 추출을 시도합니다... '" + name + "'");
        long mills = System.currentTimeMillis();
        JSONObject json = getVoiceSetsJson();
        JSONObject talkerMap = (JSONObject) json.get("talker");
        JSONObject mapping = (JSONObject) json.get("mapping");
        if (talkerMap.containsKey(name)) {
            List<String> hashes = Collections.unmodifiableList((JSONArray) talkerMap.get(name));
            Set<Future<Integer>> result = hashes.parallelStream().map(s -> {
                JSONObject obj = (JSONObject) mapping.get(s);
                return (String) obj.get("path");
            }).map(path -> {
                try {
                    return wemToWavFile(TOOL_PATH, AUDIO_CACHE_MAPPED_PATH.resolve(path),
                            changeExtension(WORK_PATH.resolve(path), ".wav"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toSet());

            while (!result.parallelStream().allMatch(Future::isDone)) {
                System.out.println("진행중... " + result.parallelStream().filter(Future::isDone).count() + "/" + result.size());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
            System.out.println(result.size() + "개의 파일이 처리됨. 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");
        } else {
            System.out.println("해당하는 이름의 데이터가 없습니다. '" + name + "'");
        }
    }

    public static void loadPath(String path) throws IOException, InterruptedException {
        long mills = System.currentTimeMillis();
        Path targetPath = AUDIO_CACHE_MAPPED_PATH.resolve(path);
        if (!Files.exists(targetPath)) {
            System.out.println("해당 폴더 또는 파일은 존재하지 않습니다. '" + targetPath + "'");
            return;
        }
        if (Files.isDirectory(targetPath)) {
            System.out.println("다음 폴더의 보이스 추출을 시도합니다... '" + targetPath + "'");
            Set<Future<Integer>> result = WwiseUtil.wemToWav(TOOL_PATH, targetPath, WORK_PATH.resolve(path));
            while (!result.parallelStream().allMatch(Future::isDone)) {
                System.out.println("진행중... " + result.parallelStream().filter(Future::isDone).count() + "/" + result.size());
                Thread.sleep(1000);
            }

            System.out.println(result.size() + "개의 파일이 처리됨. 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");
        } else {
            System.out.println("다음 파일의 보이스 추출을 시도합니다... '" + targetPath + "'");
            Future<Integer> result = wemToWavFile(TOOL_PATH, targetPath, changeExtension(WORK_PATH.resolve(path), ".wav"));

            while (!result.isDone()) {
                System.out.println("진행중... 0/1");
                Thread.sleep(100);
            }
            System.out.println("1개의 파일이 처리됨. 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");
        }

    }

    public static void loadCharacterInFolder(String character, String pathStr) throws InterruptedException {
        long mills = System.currentTimeMillis();
        Path targetPath = AUDIO_CACHE_MAPPED_PATH.resolve(pathStr);
        if (!Files.exists(targetPath)) {
            System.out.println("해당 폴더 또는 파일은 존재하지 않습니다. '" + targetPath + "'");
            return;
        }
        if (Files.isDirectory(targetPath)) {
            JSONObject json = getVoiceSetsJson();
            JSONObject talkerMap = (JSONObject) json.get("talker");
            JSONObject mapping = (JSONObject) json.get("mapping");
            if (talkerMap.containsKey(character)) {
                System.out.println("다음 캐릭터의 보이스 추출을 시도합니다... '" + character + "' 경로: " + targetPath);

                Set<String> hashes = Collections.unmodifiableSet(new HashSet<>((JSONArray) talkerMap.get(character)));

                Set<Future<Integer>> result = hashes.parallelStream().map(s -> {
                    JSONObject obj = (JSONObject) mapping.get(s);
                    return (String) obj.get("path");
                }).filter(s -> {
                    Path wemFile = AUDIO_CACHE_MAPPED_PATH.resolve(s);
                    return wemFile.startsWith(targetPath);
                }).map(path -> {
                    try {
                        return WwiseUtil.wemToWavFile(TOOL_PATH, AUDIO_CACHE_MAPPED_PATH.resolve(path),
                                changeExtension(WORK_PATH.resolve(path), ".wav"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toSet());
                while (!result.parallelStream().allMatch(Future::isDone)) {
                    System.out.println("진행중... " + result.parallelStream().filter(Future::isDone).count() + "/" + result.size());
                    Thread.sleep(1000);
                }

                System.out.println(result.size() + "개의 파일이 처리됨. 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");
            } else {
                System.out.println("해당하는 이름의 데이터가 없습니다. '" + character + "'");
            }
        } else {
            System.out.println("해당 경로는 폴더가 아닙니다. 경로: " + targetPath);
        }
    }

    // folder = work\
    public static void generateChecksumJson(Path folder) throws IOException {
        Set<Path> paths = new HashSet<>();
        try (Stream<Path> stream = Files.walk(folder)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".wav")).forEach(paths::add);
        }

        Map<String, JSONObject> mapping = (Map<String, JSONObject>) getVoiceSetsJson().get("mapping");
        Map<String, String> pathToHash = mapping.entrySet().parallelStream().collect(Collectors.toMap(entry -> {
            String pathStr = (String) entry.getValue().get("path");
            return pathStr.substring(0, pathStr.lastIndexOf('.')) + ".wav";
        }, Map.Entry::getKey));


        Map<String, JSONObject> result = paths.parallelStream().map(path -> {
            String checksum = MD5.checksum(path);
            String fileHash = pathToHash.get(WORK_PATH.relativize(path).toString());
            JSONObject newObj = new JSONObject();
            newObj.put("checksum", checksum);
            newObj.put("hash", fileHash);
            return new AbstractMap.SimpleEntry<>(WORK_PATH.relativize(path).toString(), newObj);
        }).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        Files.write(WORK_PATH.resolve("path-to-hash.json"), new JSONObject(result).toJSONString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    }

    public static void createVoiceMod(String name) throws IOException, InterruptedException {
        Path modPath = RESULT_PATH.resolve(name);
        if (Files.exists(modPath)) {
            System.out.println("폴더 " + modPath + " 가 이미 존재합니다.");
            return;
        }
        Files.createDirectories(modPath);

        clearTemp();
        JSONObject pathToHash = getPathToHashJson();
        Set<Triple<Path, String, String>> pathHashChecksum = new HashSet<>();
        try (Stream<Path> stream = Files.walk(WORK_PATH)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".wav"))
                    .forEach(path -> {
                        JSONObject hashAndChecksum = (JSONObject) pathToHash.get(WORK_PATH.relativize(path).toString());
                        if (hashAndChecksum == null) {
                            System.out.println("wav 파일 '" + path + "' 의 원본을 찾을 수 없습니다.");
                            return;
                        }
                        String hash = (String) hashAndChecksum.get("hash");
                        String checksum = (String) hashAndChecksum.get("checksum");
                        pathHashChecksum.add(new Triple<>(path, hash, checksum));
                    });
        }

        Files.createDirectories(TEMP_PATH.resolve("audio"));
        Files.createDirectories(TEMP_PATH.resolve("output"));

        long count = pathHashChecksum.parallelStream()
                .filter(triple -> !MD5.checksum(triple.first).equals(triple.third))
                .peek(triple -> {
                    Path dest = TEMP_PATH.resolve("audio").resolve(triple.second + ".wav");
                    try {
                        Files.copy(triple.first, dest);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }).count();

        if (count == 0) {
            System.out.println("변경된 보이스 파일이 존재하지 않습니다.");
            return;
        }
        System.out.println("변경된 보이스 파일의 개수: " + count);

        WwiseUtil.wavToWem(TOOL_PATH, TEMP_PATH.resolve("audio"), TEMP_PATH.resolve("output"), TEMP_PATH);

        JSONObject hashToFolder = getHashToFolderJson();
        try (Stream<Path> stream = Files.list(TEMP_PATH.resolve("output"))) {
            stream.forEach(p -> {
                String folder = (String) hashToFolder.get(getFileName(p));
                if (folder == null) {
                    System.out.println("파일에 해당하는 폴더를 찾지 못했습니다. 파일: " + p);
                    return;
                }
                try {
                    Files.createDirectories(modPath.resolve(folder));
                    Files.move(p, modPath.resolve(folder).resolve(p.getFileName()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
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

    public static class Triple<T, U, V> {

        private final T first;
        private final U second;
        private final V third;

        public Triple(T first, U second, V third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }
}

// TODO 추가해야할 것.
// TODO 1. 원신 보이스 파일 불러오기
// TODO 2. 특정 캐릭터의 파일들을 특정 폴더에 모두 불러올 수 있게 하기
// TODO 3. 변경된 파일들을 감지해서 모드로 리팩하기.
// TODO 필요한거 A. 생성된 wav 파일의 체크섬 B. 변경된 wav 파일 wem 으로 변환

/*
wem to wav 실행 시
wav 체크섬

path to hash 구현은?

 */
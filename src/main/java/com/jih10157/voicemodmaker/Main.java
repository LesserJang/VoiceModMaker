package com.jih10157.voicemodmaker;

import com.jih10157.voicemodmaker.util.FNV1_64Hash;
import com.jih10157.voicemodmaker.util.MD5;
import com.jih10157.voicemodmaker.util.Unzip;
import com.jih10157.voicemodmaker.util.WwiseUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jih10157.voicemodmaker.util.WwiseUtil.wemToWavFile;

@SuppressWarnings({"unchecked", "rawtypes"})
public class Main {

    static final String ILLEGAL_EXP = "[\\\\:/%*?|\"<>]";
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
    private static final int VOICE_MOD_MAKE_SEL_NAME_MODE = 6;
    private static final int VOICE_MOD_MAKE_SET_LANGUAGE_MODE = 7;
    private static final int VOICE_MOD_EXTRACT_MODE = 8;
    private static final int VOICE_REPLACE_SILENT_MODE = 9;
    private static final String VOICE_SET_MD5 = "5045b4d6986a8f3aafe2025f59126a38";
    private static JSONObject voiceSetsJson = null;
    private static int mode = DEFAULT_MODE;
    private static String character = "";


    //    private static final String[] SILENTS = new String[] {
//            "아이테르", "케이아", "알베도", "미카", "베넷",
//            "레이저", "다이루크", "백출", "행추", "중운",
//            "종려", "소", "토마", "카미사토 아야토", "시카노인 헤이조",
//            "고로", "아라타키 이토", "카에데하라 카즈하", "타이나리", "사이노",
//            "알하이탐", "카베", "방랑자", "리니", "프레미네",
//            "느비예트", "라이오슬리"
//    };
    private static String modName = "";

    public static void main(String[] args) throws IOException, InterruptedException {
        if (String.join(" ", args).equalsIgnoreCase("voicelist")) {
            VoiceList.generate();
            return;
        }

        setupFolder();
        setupTool();
        setupCache();

        System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");

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
                            mode = VOICE_MOD_MAKE_SEL_NAME_MODE;
                            System.out.println("보이스 모드의 이름을 입력해주세요.");
                            break;
                        case "3":
                            mode = VOICE_MOD_EXTRACT_MODE;
                            System.out.println("추출할 보이스 모드의 절대 경로를 입력해주세요.");
                            System.out.println("ex. C:\\Users\\이름\\Downloads\\패키지\\타이나리 TS V3");
                            break;
                        case "4":
                            mode = VOICE_REPLACE_SILENT_MODE;
                            System.out.println("주의: work 폴더 내의 모든 wav 파일을 tool\\무음.wav 파일로 교체합니다.");
                            System.out.println("주의: 작업중인 결과물이 사라질 수 있으니 사용 전 필요하다면 백업해주시기 바랍니다.");
                            System.out.println("계속하시겠습니까? [Y/N]");
                            break;
//                        case "무음리스트":
//                            for (String character : SILENTS) {
//                                clearFolder(WORK_PATH);
//                                loadCharacter(character);
//                                Silent.changeSilent(TOOL_PATH.resolve("무음.wav"), WORK_PATH);
//                                createVoiceMod(character + " 무음", 0);
//                            }
//                            System.out.println("완료됨");
//                            System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 나가기");
//                            break;
                        default:
                            System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
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
                            System.out.println("ex. 'Korean\\VO_friendship\\VO_tighnari' 'Japanese\\VO_LQ' 'Korean\\VO_gameplay\\VO_neuvillette\\vo_neuvillette_chest_open_01.wem'");
                            break;
                        case "3":
                            mode = VOICE_EXTRACT_CHARACTER_PATH_MODE_FIRST;
                            System.out.println("한글로 캐릭터 이름을 입력해주세요.");
                            System.out.println("ex. '타이나리' '방랑자' '카에데하라 카즈하'");
                            break;
                        case "4":
                            mode = DEFAULT_MODE;
                            System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
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
                    System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
                    break;
                case VOICE_EXTRACT_PATH_MODE:
                    loadPath(str);
                    mode = DEFAULT_MODE;
                    System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
                    break;
                case VOICE_EXTRACT_CHARACTER_PATH_MODE_FIRST:
                    character = str;
                    mode = VOICE_EXTRACT_CHARACTER_PATH_MODE_SECOND;
                    System.out.println("폴더를 입력해주세요.");
                    System.out.println("ex. 'Korean\\VO_friendship\\VO_tighnari' 'Japanese\\VO_LQ'");
                    break;
                case VOICE_EXTRACT_CHARACTER_PATH_MODE_SECOND:
                    loadCharacterInFolder(character, str);
                    mode = DEFAULT_MODE;
                    System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
                    break;
                case VOICE_MOD_MAKE_SEL_NAME_MODE:
                    if (isValidFileName(str)) {
                        modName = str;
                        mode = VOICE_MOD_MAKE_SET_LANGUAGE_MODE;
                        System.out.println("언어를 선택해주세요.");
                        System.out.println("1. 한국어 2. 일본어 3. 나가기");
                    } else {
                        System.out.println("'" + str + "' 은 유효한 폴더 이름이 아닙니다.");
                        mode = DEFAULT_MODE;
                        System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
                    }
                    break;
                case VOICE_MOD_MAKE_SET_LANGUAGE_MODE:
                    switch (str) {
                        case "1":
                            createVoiceMod(modName, 0);
                            mode = DEFAULT_MODE;
                            System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
                            break;
                        case "2":
                            createVoiceMod(modName, 1);
                            mode = DEFAULT_MODE;
                            System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
                            break;
                        case "3":
                            mode = DEFAULT_MODE;
                            System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
                            break;
                        default:
                            System.out.println("\n언어를 선택해주세요.");
                            System.out.println("1. 한국어 2. 일본어 3. 나가기");
                            break;
                    }
                    break;
                case VOICE_MOD_EXTRACT_MODE:
                    loadFromVoiceMod(str);
                    mode = DEFAULT_MODE;
                    System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
                    break;
                case VOICE_REPLACE_SILENT_MODE:
                    switch (str) {
                        case "Y":
                        case "y":
                            changeSilent(TOOL_PATH.resolve("무음.wav"), WORK_PATH);
                            mode = DEFAULT_MODE;
                            System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
                            break;
                        default:
                            System.out.println("작업이 취소되었습니다.");
                            mode = DEFAULT_MODE;
                            System.out.println("\n\n\n\n1. 보이스 추출 2. 보이스 모드 생성 3. 보이스 모드에서 추출 4. 무음 파일로 변경");
                            break;
                    }
            }
        }
        scanner.close();
        System.exit(0);
    }

    public static boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty())
            return false;

        return !Pattern.compile(ILLEGAL_EXP).matcher(fileName).find();
    }

    public static JSONObject getVoiceSetsJson() {
        if (voiceSetsJson == null) {
            System.out.println("voicesets.json 을 불러옵니다.");
            JSONParser parser = new JSONParser();
            try {
                Path mappingFile = TOOL_PATH.resolve("voicesets.json");
                voiceSetsJson = (JSONObject) parser.parse(Files.newBufferedReader(mappingFile, StandardCharsets.UTF_8));
            } catch (ParseException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return voiceSetsJson;
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
                System.out.println("assets 폴더가 비어있어 캐시를 불러올 수 없습니다.");
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

        Set<Path> paths;

        try (Stream<Path> list = Files.walk(AUDIO_CACHE_RAW_PATH)) {
            paths = list.filter(path -> path.getFileName().toString().endsWith(".wem"))
                    .collect(Collectors.toSet());
        }

        AtomicLong mills = new AtomicLong(System.currentTimeMillis());
        ReentrantLock lock = new ReentrantLock();
        AtomicInteger i = new AtomicInteger(0);
        AtomicInteger notMapped = new AtomicInteger(0);
        paths.parallelStream().forEach(path -> {
            String hash = getFileName(path);
            if (mapping.containsKey(hash)) {
                JSONObject obj = (JSONObject) mapping.get(hash);
                Path dest = AUDIO_CACHE_MAPPED_PATH.resolve((String) obj.get("path"));
                try {
                    Files.createDirectories(dest.getParent());
                    Files.move(path, dest, StandardCopyOption.REPLACE_EXISTING);
                    i.incrementAndGet();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                notMapped.incrementAndGet();
            }

            if (lock.tryLock() && System.currentTimeMillis() - mills.get() >= 500) {
                try {
                    mills.set(System.currentTimeMillis());
                    System.out.println("매핑중... " + i.get() + "/" + paths.size() + " : 존재하지 않는 해시값: " + notMapped.get() + "개");
                } finally {
                    lock.unlock();
                }
            }
        });

        System.out.println("매핑 완료 " + i.get() + "/" + paths.size() + " : 존재하지 않는 해시값: " + notMapped.get() + "개");
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
        if (Files.notExists(TEMP_PATH)) {
            Files.createDirectories(TEMP_PATH);
        }
        clearFolder(TEMP_PATH);
    }

    public static void clearFolder(Path folder) {
        if (Files.exists(folder) && Files.isDirectory(folder)) {
            try (Stream<Path> list = Files.walk(folder)) {
                list.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void setupTool() throws IOException {
        InputStream res = Main.class.getResourceAsStream("/tool.zip");
        if (res == null) {
            throw new RuntimeException("tool.zip 파일을 찾을 수 없습니다.");
        }
        Unzip.unzipFile(res, TOOL_PATH);

        Path a = TOOL_PATH.resolve("voicesets.json");
        if (Files.notExists(a) || !MD5.checksum(a).equals(VOICE_SET_MD5)) {
            InputStream stream = Main.class.getResourceAsStream("/voicesets.json");
            if (stream == null) {
                throw new RuntimeException("voicesets.json 파일을 찾을 수 없습니다.");
            }
            Files.copy(stream, a, StandardCopyOption.REPLACE_EXISTING);
        }
        Path b = TOOL_PATH.resolve("무음.wav");
        if (Files.notExists(b)) {
            InputStream stream = Main.class.getResourceAsStream("/무음.wav");
            if (stream == null) {
                throw new RuntimeException("무음.wav 파일을 찾을 수 없습니다.");
            }
            Files.copy(stream, b);
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
            Set<Future<Integer>> result = hashes.parallelStream()
                    .map(s -> {
                        JSONObject obj = (JSONObject) mapping.get(s);
                        return (String) obj.get("path");
                    })
                    .filter(path -> Files.exists(AUDIO_CACHE_MAPPED_PATH.resolve(path)))
                    .map(path -> {
                        Path dest = Paths.get(path);
                        try {
                            return wemToWavFile(TOOL_PATH, AUDIO_CACHE_MAPPED_PATH.resolve(path),
                                    changeExtension(WORK_PATH.resolve(dest.subpath(1, dest.getNameCount())), ".wav"));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toSet());

            while (!result.parallelStream().allMatch(Future::isDone)) {
                System.out.println("진행중... " + result.parallelStream().filter(Future::isDone).count() + "/" + result.size());
                waitFor(1, TimeUnit.SECONDS, () -> result.parallelStream().allMatch(Future::isDone));
            }
            System.out.println(result.size() + "개의 파일이 처리됨. 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");
        } else {
            System.out.println("해당하는 이름의 데이터가 없습니다. '" + name + "'");
        }
    }

    public static void loadPath(String path) throws IOException {
        long mills = System.currentTimeMillis();
        Path targetPath = AUDIO_CACHE_MAPPED_PATH.resolve(path);
        if (!Files.exists(targetPath)) {
            System.out.println("해당 폴더 또는 파일은 존재하지 않습니다. '" + targetPath + "'");
            return;
        }

        if (!targetPath.startsWith(AUDIO_CACHE_MAPPED_PATH.resolve("Korean"))
                && !targetPath.startsWith(AUDIO_CACHE_MAPPED_PATH.resolve("Japanese"))) {
            System.out.println("경로는 Korean 폴더 또는 Japanese 폴더로 시작해야 합니다. '" + targetPath + "'");
            return;
        }

        if (Files.isDirectory(targetPath)) {
            System.out.println("다음 폴더의 보이스 추출을 시도합니다... '" + targetPath + "'");
            Set<Future<Integer>> result = WwiseUtil.wemToWav(TOOL_PATH, targetPath, WORK_PATH);
            while (!result.parallelStream().allMatch(Future::isDone)) {
                System.out.println("진행중... " + result.parallelStream().filter(Future::isDone).count() + "/" + result.size());
                waitFor(1, TimeUnit.SECONDS, () -> result.parallelStream().allMatch(Future::isDone));
            }

            System.out.println(result.size() + "개의 파일이 처리됨. 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");
        } else {
            System.out.println("다음 파일의 보이스 추출을 시도합니다... '" + targetPath + "'");
            Path dest = Paths.get(path);
            dest = WORK_PATH.resolve(dest.subpath(1, dest.getNameCount()));

            Future<Integer> result = wemToWavFile(TOOL_PATH, targetPath, changeExtension(dest, ".wav"));

            while (!result.isDone()) {
                System.out.println("진행중... 0/1");
                waitFor(1, TimeUnit.SECONDS, result::isDone);
            }
            System.out.println("1개의 파일이 처리됨. 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");
        }
    }

    public static void loadCharacterInFolder(String character, String pathStr) {
        long mills = System.currentTimeMillis();
        Path targetPath = AUDIO_CACHE_MAPPED_PATH.resolve(pathStr);
        if (!Files.exists(targetPath)) {
            System.out.println("해당 폴더는 존재하지 않습니다. '" + targetPath + "'");
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
                }).filter(path -> {
                    Path wemFile = AUDIO_CACHE_MAPPED_PATH.resolve(path);
                    return wemFile.startsWith(targetPath) && Files.exists(wemFile);
                }).map(path -> {
                    Path dest = Paths.get(path);
                    try {
                        return WwiseUtil.wemToWavFile(TOOL_PATH, AUDIO_CACHE_MAPPED_PATH.resolve(path),
                                changeExtension(WORK_PATH.resolve(dest.subpath(1, dest.getNameCount())), ".wav"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toSet());
                while (!result.parallelStream().allMatch(Future::isDone)) {
                    System.out.println("진행중... " + result.parallelStream().filter(Future::isDone).count() + "/" + result.size());
                    waitFor(1, TimeUnit.SECONDS, () -> result.parallelStream().allMatch(Future::isDone));
                }

                System.out.println(result.size() + "개의 파일이 처리됨. 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");
            } else {
                System.out.println("해당하는 이름의 데이터가 없습니다. '" + character + "'");
            }
        } else {
            System.out.println("해당 경로는 폴더가 아닙니다. 경로: " + targetPath);
        }
    }

    public static void loadFromVoiceMod(String pathStr) throws IOException {
        long mills = System.currentTimeMillis();
        Path targetPath = Paths.get(pathStr);
        if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
            System.out.println("해당 폴더는 존재하지 않습니다. '" + targetPath + "'");
            return;
        }

        System.out.println("다음 폴더의 보이스 추출을 시도합니다... '" + targetPath + "'");
        JSONObject voiceSets = (JSONObject) getVoiceSetsJson().get("mapping");
        Set<Path> paths;
        try (Stream<Path> stream = Files.walk(targetPath)) {
            paths = stream.filter(path -> path.getFileName().toString().endsWith(".wem"))
                    .filter(path -> {
                        String hash = getFileName(path);
                        if (voiceSets.containsKey(hash)) {
                            return true;
                        } else {
                            System.out.println("해당 파일의 원본 경로를 찾을 수 없습니다. 파일: " + path);
                            return false;
                        }
                    })
                    .collect(Collectors.toSet());
        }

        System.out.println("보이스 파일 개수: " + paths.size());

        Set<Future<Integer>> result = paths.stream().map(path -> {
            String hash = getFileName(path);
            Path dest = changeExtension(Paths.get((String) ((JSONObject) voiceSets.get(hash)).get("path")), ".wav");
            dest = WORK_PATH.resolve(dest.subpath(1, dest.getNameCount()));
            try {
                return wemToWavFile(TOOL_PATH, path, dest);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());

        while (!result.parallelStream().allMatch(Future::isDone)) {
            System.out.println("진행중... " + result.parallelStream().filter(Future::isDone).count() + "/" + result.size());
            waitFor(1, TimeUnit.SECONDS, () -> result.parallelStream().allMatch(Future::isDone));
        }

        System.out.println(result.size() + "개의 파일이 처리됨. 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");
    }

    public static void createVoiceMod(String name, int language) throws IOException, InterruptedException {
        long mills = System.currentTimeMillis();
        Path modPath = RESULT_PATH.resolve(name);
        if (Files.exists(modPath)) {
            System.out.println("폴더 " + modPath + " 가 이미 존재합니다.");
            return;
        }
        Files.createDirectories(modPath);

        clearFolder(TEMP_PATH);
        JSONObject voiceSets = (JSONObject) getVoiceSetsJson().get("mapping");
        String prefixPath = language == 0 ? "korean\\" : "japanese\\";
        Set<Triple<Path, String, String>> pathHashChecksum = new HashSet<>();
        try (Stream<Path> stream = Files.walk(WORK_PATH)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".wav"))
                    .forEach(path -> {
                        Path relPath = WORK_PATH.relativize(path);
                        String fullPath = prefixPath + changeExtension(relPath, ".wem").toString().toLowerCase();
                        String hash = FNV1_64Hash.fnv1_64(fullPath);
                        JSONObject obj = (JSONObject) voiceSets.get(hash);
                        if (obj == null) {
                            System.out.println("wav 파일 '" + path + "' 의 원본을 찾을 수 없습니다. 해시: " + hash);
                            return;
                        }
                        String checksum = (String) obj.get("checksum");
                        pathHashChecksum.add(new Triple<>(path, FNV1_64Hash.fnv1_64(
                                (prefixPath + changeExtension(relPath, ".wem")).toLowerCase()), checksum));
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

        System.out.println("wav 파일을 wem 파일로 변환합니다.");
        WwiseUtil.wavToWem(TOOL_PATH, TEMP_PATH.resolve("audio"), TEMP_PATH.resolve("output"), TEMP_PATH);

        try (Stream<Path> stream = Files.list(TEMP_PATH.resolve("output"))) {
            stream.forEach(p -> {
                JSONObject obj = (JSONObject) voiceSets.get(getFileName(p));
                String folder = (String) obj.get("folder");

                if (folder == null) {
                    System.out.println("파일에 해당하는 폴더를 찾지 못했습니다. 파일: " + p + ", 해시: " + getFileName(p));
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
        System.out.println("폴더 매핑 완료");
        System.out.println("모드 생성 완료 진행 시간: " + (System.currentTimeMillis() - mills) + "ms");
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

    @SuppressWarnings("BusyWait")
    public static void waitFor(long timeout, TimeUnit timeUnit, Supplier<Boolean> end) {
        long mills = timeUnit.toMillis(timeout) + System.currentTimeMillis();
        while (!end.get() && System.currentTimeMillis() < mills) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void changeSilent(Path silentPath, Path dest) throws IOException {
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
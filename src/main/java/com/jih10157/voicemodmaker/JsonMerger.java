package com.jih10157.voicemodmaker;

import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class JsonMerger {

    private static final Pattern pattern = Pattern.compile("「([^「」]+)」");

    private static final Path DATA_FOLDER = Paths.get("data");

    // https://github.com/Escartem/AnimeWwise/tree/dev/mapping
    private static final Path ESCARTEM = DATA_FOLDER.resolve("mappingKorean.json");
    // https://github.com/AI-Hobbyist/Genshin_Datasets/tree/main/Index%20%26%20Script/AI%20Hobbyist%20Version/Index/4.1
    private static final Path HOBBYIST = DATA_FOLDER.resolve("KR_output.json");
    // https://github.com/w4123/GenshinVoice
    private static final Path W4123 = DATA_FOLDER.resolve("result.json");

    private static final Path REAL_LIST = DATA_FOLDER.resolve("list.txt");

    private static final Path RESULT = DATA_FOLDER.resolve("voicesets.json");

    public static void main(String[] args) throws ParseException {
        JSONParser parser = new JSONParser();

        JSONObject jsonEscartem;
        try {
            jsonEscartem = (JSONObject) parser.parse(Files.newBufferedReader(ESCARTEM, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        JSONObject jsonHobbyist;
        try {
            jsonHobbyist = (JSONObject) parser.parse(Files.newBufferedReader(HOBBYIST, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        JSONObject jsonW4123;
        try {
            jsonW4123 = (JSONObject) parser.parse(Files.newBufferedReader(W4123, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Set<String> hashSet = new HashSet<>(878300);
        try {
            hashSet.addAll(Files.readAllLines(REAL_LIST, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 저장해야할 것
        // 해시값, 폴더 경로, 발언자, 대사(선택)

        // 데이터 목록
        // escartem: 해시: [경로, 파일이름]
        // hobbyist: 해시: [대사, 파일경로, 발언자 이름, 발언자(선택)]
        // W4123: 해시: [파일경로, 언어, NPC 이름, 대사, 종류]

        Map<String, Voice> map = new HashMap<>();

        for (Object e : jsonEscartem.entrySet()) {
            Map.Entry<String, JSONObject> entry = (Map.Entry<String, JSONObject>) e;

            String hash = entry.getKey();
            if (!hashSet.contains(hash)) {
                continue;
            }

            JSONObject value = entry.getValue();
            String folder = (String) value.get("path");
            String name = (String) value.get("name");
            Path path = Paths.get(folder, name + ".wem");
            map.put(hash, new Voice(path, null, null));
        }

        int count = 0;
        for (Object e : jsonHobbyist.entrySet()) {
            Map.Entry<String, JSONObject> entry = (Map.Entry<String, JSONObject>) e;

            String hash = entry.getKey();
            if (!hashSet.contains(hash)) {
                continue;
            }
            JSONObject value = entry.getValue();
            String sourceFile = (String) value.get("sourceFileName");
            if (sourceFile == null) {
                System.out.println("누락된 sourceFileName: " + hash);
                continue;
            }
            Path path = Paths.get(sourceFile);
            String voiceContent = (String) value.get("voiceContent");
            String talkName = (String) value.get("talkName");
            String avatarName = (String) value.get("avatarName");
            if ("PlayerGirl".equals(avatarName)) {
                talkName = "루미네";
            } else if ("PlayerBoy".equals(avatarName)) {
                talkName = "아이테르";
            }

            talkName = preprocess(talkName);

            String talkerByPath = getTalkerByPath(path);
            if (talkerByPath != null) {
                talkName = talkerByPath;
            }

            if (map.containsKey(hash)) {
                Voice origin = map.get(hash);
                origin.talker = talkName;
                origin.content = voiceContent;
            } else {
                map.put(hash, new Voice(path, talkName, voiceContent));
                count++;
            }
        }
        System.out.println("누락되어있던 파일 " + count + "개");

        int count2 = 0;
        for (Object e : jsonW4123.entrySet()) {
            Map.Entry<String, JSONObject> entry = (Map.Entry<String, JSONObject>) e;

            String hash = entry.getKey();
            if (!hashSet.contains(hash)) {
                continue;
            }
            JSONObject value = entry.getValue();
            if (!"KR".equals(value.get("language"))) {
                continue;
            }
            String fileName = ((String) value.get("fileName")).substring(7);
            Path path = Paths.get(fileName);
            String text = (String) value.get("text");
            String talker = preprocess((String) value.get("npcName"));


            String talkerByPath = getTalkerByPath(path);

            if (map.containsKey(hash)) {
                Voice origin = map.get(hash);
                if (origin.talker != null || talker != null || talkerByPath != null) {
                    String one = getOne(origin.talker, talker, talkerByPath);
                    if (one == null) {
                        origin.talker = talker != null ? talker : talkerByPath != null ? talkerByPath : origin.talker;
//                        System.out.println("발언자 충돌 감지: " + origin.talker + " : " + talker + " : " + talkerByPath + " : " + path);
                    } else {
                        origin.talker = one;
                    }
                }

                if (origin.content == null) {
                    origin.content = text;
                }
            } else {
                map.put(hash, new Voice(path, talker, text));
                count2++;
            }
        }
        System.out.println("누락되어있던 파일 " + count2 + "개");

        System.out.println("총 추가된 개수: " + map.size() + "개");

        Map<String, List<String>> talkerMap = new ConcurrentHashMap<>();

        Map<String, JSONObject> dataMap = map.entrySet().parallelStream().map(entry -> {
            JSONObject obj = new JSONObject();
            obj.put("path", entry.getValue().path.toString());
            if (entry.getValue().talker != null) {
                obj.put("talker", entry.getValue().talker);
                talkerMap.computeIfAbsent(entry.getValue().talker, set -> Collections.synchronizedList(new ArrayList<>())).add(entry.getKey());
            }
            if (entry.getValue().content != null) {
                obj.put("content", entry.getValue().content);
            }
            return new AbstractMap.SimpleEntry<>(entry.getKey(), obj);
        }).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));


        JSONObject root = new JSONObject();
        root.put("mapping", dataMap);
        root.put("talker", talkerMap);

        try {
            Files.write(RESULT, root.toJSONString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private static String getOne(String one, String two, String three) {
        if (equalsStr(one, two)) {
            if (one == null) {
                return three;
            } else {
                return one;
            }
        }
        if (equalsStr(two, three)) {
            if (two == null) {
                return one;
            } else {
                return two;
            }
        }
        if (equalsStr(one, three)) {
            if (one == null) {
                return two;
            } else {
                return one;
            }
        }
        return null;
    }

    private static boolean equalsStr(String one, String two) {
        if (one == null && two == null) return true;
        if (one == null || two == null) return false;
        return one.equals(two);
    }

    private static String preprocess(String talker) {
        if (talker != null) {
            Matcher matcher = pattern.matcher(talker);
            if (matcher.matches()) {
                talker = matcher.group(1);
            }
            if (talker.equals("에이(影)")) {
                talker = "에이";
            }
            if (talker.equals("카즈하")) {
                talker = "카에데하라 카즈하";
            }
            if (talker.equals("대도")) {
                talker = "케이아";
            }
            if (talker.equals("「잇신의 기술」 명검")) {
                talker = "카고츠루베 잇신";
            }
            if (talker.equals("쿠사일라의 편지")) {
                talker = "쿠사일라";
            }
            if (talker.equals("아이드")) {
                talker = "클로타르";
            }
            if (talker.equals("고압적인 남자")) {
                talker = "클로타르";
            }
            if (talker.equals("칠엽 적조의 비밀주")) {
                talker = "스크라무슈";
            }
            if (talker.equals("가부키모노")) {
                talker = "스크라무슈";
            }
            if (talker.equals("???")) {
                talker = null;
            }
        }
        return talker;
    }

    @Nullable
    private static String getTalkerByPath(Path path) {
        String first = path.subpath(0, 1).toString().substring(3).toLowerCase();
        switch (first) {
            case ("aq"):
            case ("card"):
            case ("coop"):
            case ("costume"):
            case ("eq"):
            case ("freetalk"):
            case ("friendship"):
            case ("gameplay"):
            case ("hs"):
            case ("lq"):
                String second = path.subpath(1, 2).toString().substring(3);
                String name = getNameByEnglish(second);
                if (name != null) {
                    if (name.equals("라이덴 쇼군") && path.toString().contains("raidenEi")) {
                        name = "에이";
                    } else if (name.equals("파루잔") && path.toString().contains("faruzanMom")) {
                        name = "파루잔의 어머니";
                    } else if (name.equals("호두") && path.toString().contains("xiao") && first.equals("freetalk")) {
                        name = "소";
                    } else if (name.equals("케이아") && path.toString().contains("kaede")) {
                        name = "카에데";
                    } else if (name.equals("향릉") && path.toString().contains("gooba")) {
                        name = "누룽지";
                    } else if (name.equals("백출") && path.toString().contains("changsheng")) {
                        name = "장생";
                    } else if (name.equalsIgnoreCase("npc")) {
                        if (path.toString().contains("urakusai")) {
                            name = "우라쿠사이";
                        } else if (path.toString().contains("eide")) {
                            name = "클로타르";
                        } else {
                            name = null;
                        }
                    }
                }
                return name;
            case ("gcg_monster"):
                break;
            case ("ingame"):
                break;
            case ("monster"):
                break;
            case ("spice"):
                break;
            case ("teamjoin"):
                break;
            case ("tips"):
                break;
            case ("wq"):
                break;
            default:
                return null;
        }
        return null;
    }

    /*
    print('VO_COOP - 전설퀘\nVO_EQ - 이벤트\nVO_AQ - 마신퀘\nVO_LQ - 전설퀘\nVO_WQ - 월드 퀘스트(이벤트 맵안의 월퀘포함)\n')
print('VO_tips 스토리중 기믹 조언\nVO_HS - 주전자\nVO_gameplay - 플레이 대사\nVO_freetalk - 직접 대화걸때 대사\nVO_friendship - 캐릭터창에서 대사\nVO_Card 원스스톤 대사\n')
     */

    @Nullable
    private static String getNameByEnglish(String name) {
        switch (name) {
            case "albedo":
                return "알베도";
            case "alhaitham":
                return "알하이탐";
            case "aloy":
                return "에일로이";
            case "ambor":
                return "엠버";
            case "ayaka":
                return "카미사토 아야카";
            case "ayato":
                return "카미사토 아야토";
            case "baizhu":
                return "백출";
            case "barbara":
                return "바바라";
            case "beidou":
                return "북두";
            case "bennett":
                return "베넷";
            case "candace":
                return "캔디스";
            case "chongyun":
                return "중운";
            case "collei":
                return "콜레이";
            case "cyno":
                return "사이노";
            case "dehya":
                return "데히야";
            case "diluc":
                return "다이루크";
            case "diona":
                return "디오나";
            case "dori":
                return "도리";
            case "dainsleif":
                return "데인슬레이프";
            case "eula":
                return "유라";
            case "faruzan":
                return "파루잔";
            case "fischi":
                return "피슬";
            case "freminet":
                return "프레미네";
            case "ganyu":
                return "감우";
            case "gorou":
                return "고로";
            case "heizou":
                return "시카노인 헤이조";
            case "hero":
                return "아이테르";
            case "heroine":
                return "루미네";
            case "hutao":
                return "호두";
            case "itto":
                return "아라타키 이토";
            case "kaeya":
                return "케이아";
            case "kaveh":
                return "카베";
            case "kazuha":
                return "카에데하라 카즈하";
            case "keqing":
                return "각청";
            case "kirara":
                return "키라라";
            case "klee":
                return "클레";
            case "kokomi":
                return "산고노미야 코코미";
            case "kujouSara":
                return "쿠죠 사라";
            case "layla":
                return "레일라";
            case "lisa":
                return "리사";
            case "lynette":
                return "리넷";
            case "lyney":
                return "리니";
            case "mika":
                return "미카";
            case "mona":
                return "모나";
            case "nahida":
                return "나히다";
            case "neuvillette":
                return "느비예트";
            case "nilou":
                return "닐루";
            case "ningquang":
                return "응광";
            case "noel":
                return "노엘";
            case "qin":
                return "진";
            case "qiqi":
                return "치치";
            case "raidenShogun":
                return "라이덴 쇼군";
            case "razor":
                return "레이저";
            case "rosaria":
                return "로자리아";
            case "sayu":
                return "사유";
            case "shenhe":
                return "신학";
            case "shinobu":
                return "쿠키 시노부";
            case "sucrose":
                return "설탕";
            case "tartaglia":
                return "타르탈리아";
            case "thoma":
                return "토마";
            case "tighnari":
                return "타이나리";
            case "venti":
                return "벤티";
            case "wanderer":
                return "방랑자";
            case "wriothesley":
                return "라이오슬리";
            case "xiangling":
                return "향릉";
            case "xiao":
                return "소";
            case "xingqui":
                return "행추";
            case "xinyan":
                return "신염";
            case "yaeMiko":
                return "야에 미코";
            case "yanfei":
                return "연비";
            case "yaoyao":
                return "요요";
            case "yelan":
                return "야란";
            case "yoimiya":
                return "요이미야";
            case "yunjin":
                return "운근";
            case "zhongli":
                return "종려";
            case "paimon":
                return "페이몬";
            case "scaramouche":
                return "스카라무슈";
            case "npc":
                return "npc";
            default:
                return null;
        }
    }

    private static class Voice {
        private final Path path;
        @Nullable
        private String talker;
        @Nullable
        private String content;


        private Voice(Path path, @Nullable String talker, @Nullable String content) {
            this.path = path;
            this.talker = talker;
            this.content = content;
        }
    }
}

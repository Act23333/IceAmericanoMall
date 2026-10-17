package org.icedamericanomall.arch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线格式标识字段的<b>往返性质</b>守卫（ADR-062 §守卫 3）。
 *
 * <p><b>为什么需要它</b>：静态扫描（{@code scripts/check-id-invariants.sh} 检查 4/5）只能看
 * 声明文本，看不到「这些字段真的按契约渲染」—— 一个 {@code Long spuId} 在静态面只是「类型不对」，
 * 在<b>行为</b>面则是「JSON 里出现数字、JS 端 {@code Number} 精度静默丢失」。本测试补上行为面：
 *
 * <pre>
 *   对每个含标识字段的 DTO/VO：给字段写入 IdGenerator 产出的 32-hex 业务键
 *   → 按线上命名策略（SNAKE_CASE）渲染为 JSON
 *   → 断言该键<b>是字符串</b>（不是数字）、值逐字相等
 *   → 反序列化回来，断言 parse(render(x)) == x
 * </pre>
 *
 * <p><b>命中面</b>：各服务的 {@code adapter/request}、{@code adapter/response} 与 ia-api 的 {@code dto}
 * ——与 shell 门禁检查 4 同一命名面（{@code spuId(s)}/{@code skuId(s)}/{@code productId(s)}/单号族）。
 * 类通过 URLClassLoader 从各模块 {@code target/classes} 加载（本模块刻意<b>不依赖任何业务模块</b>），
 * 业务键由真实 {@code IdGenerator} 生成（不另造形态）。
 *
 * <p><b>不得静默跳过</b>：加载/实例化/序列化失败的类一律计入失败清单（报类名 + 原因）；
 * 「序号型」字段（如 {@code packageNo}）为显式白名单，且<b>白名单项陈旧即失败</b>
 * （与 {@code check-id-invariants.sh} 同款自证），保证「跳过」永远是显式决策。
 */
class IdWireRoundTripTest {

    /** 标识字段命名面（与 check-id-invariants.sh 检查 4 逐字一致）。 */
    private static final Pattern ID_FIELD_NAME = Pattern.compile(
            "([A-Za-z0-9_]*)([sS]puId|[sS]puIds|[sS]kuId|[sS]kuIds|[pP]roductId|[pP]roductIds|No|Nos)");

    /** 源文本里的字段名（静态预筛：只对「含标识字段」的类做加载与往返，避免无谓装载）。 */
    private static final Pattern PRIVATE_FIELD_DECL = Pattern.compile(
            "(?m)^\\s*private\\s+(?:static\\s+)?(?:final\\s+)?[\\w.$<>,\\[\\] ]+?\\s+([A-Za-z0-9_]+)\\s*;");

    /**
     * 显式白名单：命名面命中但<b>不是实体标识</b>的「序号」字段（与 shell 门禁 WHITELIST 同源）。
     * 当前仅 {@code packageNo}（包裹序号，ADR-037）—— 序号无跨环境唯一性语义，保持 Integer。
     */
    private static final Set<String> SEQUENCE_FIELDS = Set.of("packageNo");

    /** 线格式目录（相对本模块：architecture-tests）。 */
    private static final String ADAPTER_REQUEST = "/src/main/java/";
    private static final String IA_API_DTO = "../ia-api/src/main/java/org/icedamericanomall/dto";

    @Test
    @DisplayName("每个含标识字段的 DTO/VO — render 必为字符串且 parse(render(x)) == x")
    void everyWireIdFieldShouldRoundTrip() throws Exception {
        List<Path> classDirs = classDirectories();
        assertTrue(!classDirs.isEmpty(),
                "未发现任何模块的 target/classes —— 门禁自身失效（本测试须在全量构建后运行）");

        List<Path> sources = wireSources();
        assertTrue(!sources.isEmpty(),
                "未扫描到任何线格式源文件 —— 门禁自身失效（目录基准错误），不得据通过判定合规");

        URL[] urls = classDirs.stream().map(IdWireRoundTripTest::toUrl).toArray(URL[]::new);
        URLClassLoader loader = new URLClassLoader(urls, IdWireRoundTripTest.class.getClassLoader());

        // 业务键由真实 IdGenerator 产出（http 层与测试同源形态，避免「测试自己造形态」）
        Class<?> idGenerator = Class.forName("org.icedamericanomall.common.utils.IdGenerator", true, loader);
        Method nextId = idGenerator.getMethod("nextId");

        ObjectMapper mapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.findAndRegisterModules();

        List<String> problems = new ArrayList<>();
        Set<String> whitelistHits = new LinkedHashSet<>();
        int checkedClasses = 0;
        int checkedFields = 0;

        for (Path source : sources) {
            Class<?> outer;
            try {
                outer = Class.forName(fullyQualifiedName(source), false, loader);
            } catch (Throwable t) {
                problems.add(rel(source) + ": 类加载失败（" + t.getClass().getSimpleName() + ": " + t.getMessage() + "）");
                continue;
            }
            for (Class<?> type : withNested(outer)) {
                if (skip(type)) {
                    continue;
                }
                List<Field> idFields = new ArrayList<>();
                List<Field> whitelisted = new ArrayList<>();
                Field[] declared;
                try {
                    declared = type.getDeclaredFields();
                } catch (Throwable t) {
                    problems.add(type.getName() + ": 字段声明不可解析（" + t.getClass().getSimpleName()
                            + ": " + t.getMessage() + "）—— 依赖不在门禁类路径时须显式登记，不得静默跳过");
                    continue;
                }
                for (Field field : declared) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if (!ID_FIELD_NAME.matcher(field.getName()).find()) {
                        continue;
                    }
                    if (SEQUENCE_FIELDS.contains(field.getName())) {
                        whitelisted.add(field);
                        whitelistHits.add(type.getSimpleName() + "." + field.getName());
                        continue;
                    }
                    idFields.add(field);
                }
                if (idFields.isEmpty()) {
                    continue;
                }
                checkedClasses++;
                checkedFields += idFields.size();
                problems.addAll(roundTrip(type, idFields, mapper, nextId));
            }
        }

        assertTrue(problems.isEmpty(),
                () -> "线格式标识字段的 JSON 往返不合规（ADR-062 §守卫 3）：\n  - "
                        + String.join("\n  - ", problems));
        // 命中面自证（写进构建日志，便于人工核对门禁「真的扫到了东西」）
        System.out.println("[ADR-062 §守卫 3] 线格式标识字段往返检查命中："
                + checkedClasses + " 个类 / " + checkedFields + " 个字段（源文件 " + sources.size() + " 个）");
        // 白名单自证：命中面中已无该字段 → 白名单陈旧 → 失败（不允许「跳过」悄悄失效）
        for (String expected : SEQUENCE_FIELDS) {
            boolean hit = whitelistHits.stream().anyMatch(h -> h.endsWith("." + expected));
            assertTrue(hit, "白名单项已陈旧（命中面中已无此字段，请删除该项）：" + expected);
        }
        assertTrue(checkedClasses > 0 && checkedFields > 0,
                "往返检查命中 " + checkedClasses + " 个类 / " + checkedFields + " 个字段 —— 命中面为 0 即门禁自身失效");
    }

    // ──────────────── 「两端同一次改动」护栏（契约 §1.8④） ────────────────

    /**
     * {@code SpuVO} ⇄ {@code SpuDetailCO} 往返守卫 —— <b>{@code spuName} 必须非空</b>。
     *
     * <p><b>为什么单独一条</b>：这两个类分别在 item-service 与 ia-api（跨模块、跨服务边界），
     * 一个改名另一个不改时，<b>编译期毫无征兆</b>、静态扫描也各有命中面互不相干 ——
     * 只有「把 item 侧的报文喂给契约侧的接收端」才能暴露：Jackson {@code SNAKE_CASE}
     * 忽略未知键 + 本仓 Spring Boot 默认 {@code FAIL_ON_UNKNOWN_PROPERTIES=false}，
     * 于是 {@code spu_name} 无声落成 {@code null}（AI 工具会显示「商品名: null」）。
     * 本测试就是 2026-10-18「甲b」把两侧改名<b>钉在同一次改动</b>上的护栏。
     *
     * <p>两侧都通过 {@code target/classes} 的 URLClassLoader 装载（本模块刻意不依赖业务模块），
     * 与 {@link #everyWireIdFieldShouldRoundTrip()} 同一装载面。
     */
    @Test
    @DisplayName("SpuVO → SpuDetailCO 往返：spu_name 线上键两端同源，spuName 解码后必须非空")
    void spuNameShouldSurviveBetweenItemDetailAndContract() throws Exception {
        List<Path> classDirs = classDirectories();
        assertTrue(!classDirs.isEmpty(),
                "未发现任何模块的 target/classes —— 门禁自身失效（本测试须在全量构建后运行）");
        URL[] urls = classDirs.stream().map(IdWireRoundTripTest::toUrl).toArray(URL[]::new);
        URLClassLoader loader = new URLClassLoader(urls, IdWireRoundTripTest.class.getClassLoader());

        Class<?> spuVoType = Class.forName("org.icedamericanomall.item.adapter.response.SpuVO", true, loader);
        Class<?> coType = Class.forName("org.icedamericanomall.dto.SpuDetailCO", true, loader);

        String spuName = "手工陶瓷茶杯";
        Object spuVo = spuVoType.getDeclaredConstructor().newInstance();
        spuVoType.getMethod("setSpuId", String.class).invoke(spuVo, "0f1e2d3c4b5a69788796a5b4c3d2e1f0");
        spuVoType.getMethod("setSpuName", String.class).invoke(spuVo, spuName);

        // 与生产同口径：SNAKE_CASE + 忽略未知键（Spring Boot 默认 FAIL_ON_UNKNOWN_PROPERTIES=false，
        // 正是「单侧改名静默落 null」的成因，故必须原样复现而不是顺手收紧）。
        ObjectMapper mapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.findAndRegisterModules();

        String json = mapper.writeValueAsString(spuVo);
        assertTrue(mapper.readTree(json).has("spu_name"),
                "SpuVO 渲染结果缺少线上键 spu_name（契约 §1.8④）：" + json);

        Object co = mapper.readValue(json, coType);
        Object decoded = coType.getMethod("spuName").invoke(co);
        org.junit.jupiter.api.Assertions.assertNotNull(decoded,
                "SpuDetailCO.spuName 解码为 null —— 说明两侧名称键不同批（甲b 硬约束：同一 PR 内改完）：" + json);
        assertEquals(spuName, decoded);
    }

    // ──────────────── 往返核心 ────────────────

    /** 对单个类型做「写值 → 渲染 → 断言字符串形态与值 → 反序列化比对」，返回问题清单。 */
    private static List<String> roundTrip(Class<?> type, List<Field> idFields,
                                          ObjectMapper mapper, Method nextId) throws Exception {
        List<String> problems = new ArrayList<>();
        Map<Field, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < idFields.size(); i++) {
            Field field = idFields.get(i);
            if (field.getType() == String.class) {
                values.put(field, (String) nextId.invoke(null));
            } else if (List.class.isAssignableFrom(field.getType())
                    && "java.lang.String".equals(stringElementOf(field))) {
                values.put(field, List.of((String) nextId.invoke(null), (String) nextId.invoke(null)));
            } else {
                problems.add(type.getSimpleName() + "." + field.getName() + ": 类型为 " + field.getType().getSimpleName()
                        + "（应为 String 或 List<String>）—— 数字型标识在 JSON 里会被 JS 静默丢精度");
                values.put(field, null);
            }
        }

        Object instance;
        try {
            instance = instantiate(type, values);
        } catch (Exception e) {
            problems.add(type.getSimpleName() + ": 无法实例化以做往返断言（" + e.getClass().getSimpleName()
                    + ": " + e.getMessage() + "）—— 需显式登记而非静默跳过");
            return problems;
        }

        String json;
        try {
            json = mapper.writeValueAsString(instance);
        } catch (Exception e) {
            problems.add(type.getSimpleName() + ": 序列化失败（" + e.getClass().getSimpleName()
                    + ": " + e.getMessage() + "）");
            return problems;
        }

        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            problems.add(type.getSimpleName() + ": 渲染结果不是合法 JSON（" + e.getMessage() + "）");
            return problems;
        }

        for (Map.Entry<Field, Object> entry : values.entrySet()) {
            Field field = entry.getKey();
            Object expected = entry.getValue();
            if (expected == null) {
                continue;
            }
            String wireName = snake(field.getName());
            JsonNode node = root.get(wireName);
            if (node == null || node.isNull()) {
                problems.add(type.getSimpleName() + "." + field.getName() + ": 渲染结果缺少线上键 `" + wireName
                        + "`（JSON=" + json + "）");
                continue;
            }
            if (expected instanceof String value) {
                if (!node.isTextual()) {
                    problems.add(type.getSimpleName() + "." + field.getName() + "（`" + wireName + "=" + node
                            + "`）：渲染为 JSON " + node.getNodeType() + " —— 标识必须是<b>字符串</b>");
                } else if (!value.equals(node.asText())) {
                    problems.add(type.getSimpleName() + "." + field.getName() + ": 渲染值被改写（期望 " + value
                            + "，实际 " + node.asText() + "）");
                }
            } else {
                @SuppressWarnings("unchecked")
                List<String> expectedList = (List<String>) expected;
                if (!node.isArray()) {
                    problems.add(type.getSimpleName() + "." + field.getName() + "（`" + wireName + "=" + node
                            + "`）：渲染为 JSON " + node.getNodeType() + " —— 标识列表必须是字符串数组");
                } else if (node.size() != expectedList.size()) {
                    problems.add(type.getSimpleName() + "." + field.getName() + ": 渲染条数不符（期望 "
                            + expectedList.size() + "，实际 " + node.size() + "）");
                } else {
                    for (int i = 0; i < expectedList.size(); i++) {
                        JsonNode element = node.get(i);
                        if (!element.isTextual() || !expectedList.get(i).equals(element.asText())) {
                            problems.add(type.getSimpleName() + "." + field.getName() + "[" + i + "]: 元素被改写为 "
                                    + element + "（期望字符串 " + expectedList.get(i) + "）");
                        }
                    }
                }
            }
        }

        // parse(render(x)) == x
        Object parsed;
        try {
            parsed = mapper.readValue(json, type);
        } catch (Exception e) {
            problems.add(type.getSimpleName() + ": 反序列化失败（" + e.getClass().getSimpleName()
                    + ": " + e.getMessage() + "）");
            return problems;
        }
        for (Map.Entry<Field, Object> entry : values.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            entry.getKey().setAccessible(true);
            Object actual = entry.getKey().get(parsed);
            assertEquals(entry.getValue(), actual,
                    type.getSimpleName() + "." + entry.getKey().getName() + " 往返不等价（parse(render(x)) != x）");
        }
        return problems;
    }

    /** 实例化：record 走规范构造器；普通类走无参构造器；非静态内部类先建外部实例。 */
    private static Object instantiate(Class<?> type, Map<Field, Object> values) throws Exception {
        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                parameterTypes[i] = components[i].getType();
                args[i] = defaultValue(parameterTypes[i]);
                Field field = type.getDeclaredField(components[i].getName());
                if (values.containsKey(field)) {
                    args[i] = values.get(field);
                }
            }
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }

        Object outerInstance = null;
        if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers())) {
            outerInstance = instantiate(type.getEnclosingClass(), Map.of());
        }
        Constructor<?> constructor = outerInstance == null
                ? type.getDeclaredConstructor()
                : type.getDeclaredConstructor(type.getEnclosingClass());
        constructor.setAccessible(true);
        Object instance = outerInstance == null
                ? constructor.newInstance()
                : constructor.newInstance(outerInstance);
        for (Map.Entry<Field, Object> entry : values.entrySet()) {
            entry.getKey().setAccessible(true);
            entry.getKey().set(instance, entry.getValue());
        }
        return instance;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return 0;
    }

    // ──────────────── 扫描与工具 ────────────────

    /** 线格式源文件：各服务（**主**代码）{@code adapter/{request,response}} + ia-api dto。 */
    private static List<Path> wireSources() throws Exception {
        List<Path> found = new ArrayList<>();
        Path backend = Paths.get("..").toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(backend)) {
            for (Path path : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String normalized = path.toString().replace('\\', '/');
                if (normalized.contains("/target/") || !normalized.contains("/src/main/java/")) {
                    continue;   // 构建产物与测试代码都不是线格式面
                }
                if (normalized.contains("/adapter/request/") || normalized.contains("/adapter/response/")
                        || normalized.contains("/ia-api/src/main/java/org/icedamericanomall/dto/")) {
                    if (mentionsIdField(path)) {
                        found.add(path);
                    }
                }
            }
        }
        found.sort(Path::compareTo);
        return found;
    }

    /** 静态预筛：源文本内是否存在「命名面命中」的私有字段（含嵌套类的字段）。 */
    private static boolean mentionsIdField(Path source) throws Exception {
        Matcher matcher = PRIVATE_FIELD_DECL.matcher(Files.readString(source, StandardCharsets.UTF_8));
        while (matcher.find()) {
            if (ID_FIELD_NAME.matcher(matcher.group(1)).find()) {
                return true;
            }
        }
        return false;
    }

    /** 全模块 {@code target/classes}（本模块刻意不依赖业务模块，故用 URLClassLoader 装载）。 */
    private static List<Path> classDirectories() throws Exception {
        List<Path> dirs = new ArrayList<>();
        Path backend = Paths.get("..").toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(backend, 3)) {
            for (Path path : walk.toList()) {
                if (Files.isDirectory(path) && path.endsWith(Paths.get("target", "classes"))) {
                    dirs.add(path);
                }
            }
        }
        dirs.sort(Path::compareTo);
        return dirs;
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception e) {
            throw new IllegalStateException("路径无法转为 URL: " + path, e);
        }
    }

    private static List<Class<?>> withNested(Class<?> outer) {
        List<Class<?>> all = new ArrayList<>();
        all.add(outer);
        for (Class<?> nested : outer.getDeclaredClasses()) {
            all.addAll(withNested(nested));
        }
        return all;
    }

    private static boolean skip(Class<?> type) {
        int modifiers = type.getModifiers();
        return type.isInterface() || type.isEnum() || type.isAnnotation() || type.isAnonymousClass()
                || type.isLocalClass() || Modifier.isAbstract(modifiers) || Modifier.isPrivate(modifiers)
                || type.getSuperclass() == null && !type.isRecord();
    }

    /** {@code ../item-service/src/main/java/.../SpuVO.java} → {@code org.icedamericanomall.item.adapter.response.SpuVO}。 */
    private static String fullyQualifiedName(Path source) throws Exception {
        String text = Files.readString(source, StandardCharsets.UTF_8);
        Matcher pkg = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(text);
        Matcher type = Pattern.compile("(?m)^public\\s+(?:final\\s+|abstract\\s+)?(?:class|record|enum|interface)\\s+(\\w+)")
                .matcher(text);
        if (!pkg.find() || !type.find()) {
            throw new IllegalStateException("无法解析包名/类型名: " + source);
        }
        return pkg.group(1) + "." + type.group(1);
    }

    /** Java 字段名 → 线上名（与各服务 {@code SNAKE_CASE} 命名策略一致）。 */
    private static String snake(String javaName) {
        return javaName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    /** 泛型实参（仅支持单参泛型，如 {@code List<String>}）；非泛型返回 null。 */
    private static String stringElementOf(Field field) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length == 1) {
            return Arrays.stream(parameterized.getActualTypeArguments())
                    .map(Type::getTypeName)
                    .findFirst().orElse(null);
        }
        return null;
    }

    private static String rel(Path path) {
        Path backend = Paths.get("..").toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(backend) ? backend.relativize(normalized).toString().replace('\\', '/')
                : path.toString();
    }
}

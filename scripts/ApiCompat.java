// Compatibilidade binária da API pública (0.14, fase 1). Lê com javap (-protected -s, pelo ToolProvider do próprio JDK)
// todas as classes de dev.calamidade_awakening.api.v1 (e .client) de um JAR e grava ou confere uma base de assinaturas.
// Programa Java de arquivo único (Java 21), sem dependências:
//
//   java scripts/ApiCompat.java --jar dist/calamidade_awakening-0.12.0.jar --base scripts/api-base/api-v1-0.12.0.txt --write
//   java scripts/ApiCompat.java --jar build/libs/calamidade_awakening-X.jar --base scripts/api-base/api-v1-0.12.0.txt
//   java scripts/ApiCompat.java --jar build/libs/calamidade_awakening-X.jar     (sem --base: autoteste das regras)
//
// No Gradle: ./gradlew checkApiCompat (parte do build). A conferência falha quando, em relação à base:
//   - some uma classe, ou o cabeçalho dela muda (modificadores, superclasse, interfaces, parâmetros de tipo);
//   - some um membro público ou protegido, ou muda o descritor dele (nome + descritor JVM: o que o addon compilado usa);
//   - uma interface ou classe abstrata da base ganha um método abstrato (quebraria quem a implementa ou estende);
//   - um enum da base perde ou reordena constantes (o GLSL do motor e os addons podem depender do ordinal).
// Membros e classes novos são permitidos: a regra 1 do AGENTS.md é "só adicionar".
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.spi.ToolProvider;
import java.util.zip.ZipFile;

public class ApiCompat {
    static final String PACKAGE = "dev/calamidade_awakening/api/v1/";

    /** One class: javap header and its members ("signature ;; descriptor"), in javap order. */
    record Type(String name, String header, List<String> members) {
        boolean extendable() {
            return header.contains(" interface ") || header.contains(" abstract ") && !header.contains(" final ");
        }
        boolean isEnum() { return header.contains(" enum ") || header.contains("extends java.lang.Enum<"); }
    }

    public static void main(String[] args) throws Exception {
        String jar = null, base = null;
        boolean write = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--jar" -> jar = args[++i];
                case "--base" -> base = args[++i];
                case "--write" -> write = true;
                default -> throw new IllegalArgumentException("argumento desconhecido: " + args[i]);
            }
        }
        Map<String, Type> current = read(Path.of(jar));
        if (base == null) { selfTest(current); return; }
        Path basePath = Path.of(base);
        if (write) {
            Files.createDirectories(basePath.getParent());
            Files.writeString(basePath, format(current), StandardCharsets.UTF_8);
            System.out.println("ApiCompat: base gravada com " + current.size() + " classes e "
                + current.values().stream().mapToInt(t -> t.members().size()).sum() + " membros em " + basePath);
            return;
        }
        Map<String, Type> expected = parse(Files.readString(basePath, StandardCharsets.UTF_8));
        List<String> problems = compare(expected, current);
        int members = expected.values().stream().mapToInt(t -> t.members().size()).sum();
        if (!problems.isEmpty()) {
            problems.forEach(p -> System.err.println("ApiCompat: " + p));
            System.err.println("ApiCompat: " + problems.size() + " quebra(s) de compatibilidade contra " + basePath.getFileName());
            System.exit(1);
        }
        System.out.println("ApiCompat: " + expected.size() + " classes e " + members + " membros da base "
            + basePath.getFileName() + " presentes e compatíveis (" + current.size() + " classes no JAR).");
    }

    static List<String> compare(Map<String, Type> expected, Map<String, Type> current) {
        List<String> problems = new ArrayList<>();
        for (Type old : expected.values()) {
            Type now = current.get(old.name());
            if (now == null) { problems.add("classe removida: " + old.name()); continue; }
            if (!old.header().equals(now.header()))
                problems.add("cabeçalho mudou: " + old.name() + "\n    antes: " + old.header() + "\n    agora: " + now.header());
            for (String m : old.members())
                if (!now.members().contains(m)) problems.add("membro removido ou mudado em " + old.name() + ": " + m);
            if (old.extendable())
                for (String m : now.members())
                    if (!old.members().contains(m) && m.contains(" abstract ") && !m.contains(" static "))
                        problems.add("método abstrato novo em tipo extensível " + old.name() + ": " + m);
            if (old.isEnum()) {
                List<String> before = constants(old), after = constants(now);
                if (after.size() < before.size() || !after.subList(0, before.size()).equals(before))
                    problems.add("constantes do enum " + old.name() + " removidas ou reordenadas: " + before + " -> " + after);
            }
        }
        return problems;
    }

    /**
     * Without --base: checks that each kind of break is caught, on copies of the JAR's own signatures (the same JAR
     * against itself must pass; each mutation must fail).
     */
    static void selfTest(Map<String, Type> jar) {
        List<String> failures = new ArrayList<>();
        if (!compare(parse(format(jar)), jar).isEmpty()) failures.add("o JAR contra a própria base falhou");
        Type anyClass = jar.values().stream().filter(t -> !t.members().isEmpty()).findFirst().orElseThrow();
        Type anEnum = jar.values().stream().filter(Type::isEnum).findFirst().orElseThrow();
        Type anInterface = jar.values().stream().filter(t -> t.header().contains(" interface ")).findFirst().orElseThrow();
        Map<String, java.util.function.Consumer<Map<String, Type>[]>> cases = new LinkedHashMap<>();
        cases.put("membro removido", m -> m[1].put(anyClass.name(), without(anyClass, anyClass.members().get(0))));
        cases.put("classe removida", m -> m[1].remove(anyClass.name()));
        cases.put("cabeçalho mudado", m -> m[1].put(anyClass.name(),
            new Type(anyClass.name(), anyClass.header() + " implements java.io.Closeable", anyClass.members())));
        cases.put("enum reordenado", m -> {
            List<String> members = new ArrayList<>(anEnum.members());
            java.util.Collections.swap(members, 0, 1);
            m[1].put(anEnum.name(), new Type(anEnum.name(), anEnum.header(), members));
        });
        cases.put("abstrato novo em interface", m -> {
            List<String> members = new ArrayList<>(anInterface.members());
            members.add("public abstract void novo(); ;; ()V");
            m[1].put(anInterface.name(), new Type(anInterface.name(), anInterface.header(), members));
        });
        for (var c : cases.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Type>[] pair = new Map[] {parse(format(jar)), new LinkedHashMap<>(jar)};
            c.getValue().accept(pair);
            if (compare(pair[0], pair[1]).isEmpty()) failures.add("não detectou: " + c.getKey());
        }
        List<String> added = new ArrayList<>(anyClass.members());
        added.add("public static void adicionado(); ;; ()V");
        Map<String, Type> grown = new LinkedHashMap<>(jar);
        grown.put(anyClass.name(), new Type(anyClass.name(), anyClass.header(), added));
        if (!compare(parse(format(jar)), grown).isEmpty()) failures.add("recusou um membro estático novo (adição permitida)");
        if (!failures.isEmpty()) {
            failures.forEach(f -> System.err.println("ApiCompat (autoteste): " + f));
            System.exit(1);
        }
        System.out.println("ApiCompat (autoteste): " + (cases.size() + 2) + " casos ok");
    }

    static Type without(Type type, String member) {
        List<String> members = new ArrayList<>(type.members());
        members.remove(member);
        return new Type(type.name(), type.header(), members);
    }

    /** Enum constants in declaration order (javap lists them first, as public static final fields of the enum type). */
    static List<String> constants(Type type) {
        List<String> out = new ArrayList<>();
        String marker = " static final " + type.name() + " ";
        for (String m : type.members()) {
            int at = m.indexOf(marker);
            if (at >= 0) out.add(m.substring(at + marker.length(), m.indexOf(';', at)).trim());
        }
        return out;
    }

    static Map<String, Type> read(Path jar) throws Exception {
        List<String> classes = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            zip.stream().map(e -> e.getName())
                .filter(n -> n.startsWith(PACKAGE) && n.endsWith(".class") && !n.matches(".*\\$\\d+.*\\.class"))
                .sorted().forEach(classes::add);
        }
        ToolProvider javap = ToolProvider.findFirst("javap").orElseThrow();
        Map<String, Type> types = new LinkedHashMap<>();
        for (String entry : classes) {
            String name = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            int code = javap.run(new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8),
                "-protected", "-s", "-cp", jar.toString(), name);
            if (code != 0) throw new IllegalStateException("javap falhou em " + name + ": " + err.toString(StandardCharsets.UTF_8));
            Type type = parseJavap(name, out.toString(StandardCharsets.UTF_8));
            if (type != null) types.put(type.name(), type);
        }
        return types;
    }

    /** Parses one javap -s listing; null for classes that are not public or protected (javap prints no header). */
    static Type parseJavap(String name, String text) {
        String header = null;
        List<String> members = new ArrayList<>();
        String pending = null;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("Compiled from") || line.equals("}")) continue;
            if (header == null) {
                if (line.endsWith("{")) header = line.substring(0, line.length() - 1).strip();
                continue;
            }
            if (line.startsWith("descriptor:")) {
                if (pending != null) members.add(pending + " ;; " + line.substring("descriptor:".length()).strip());
                pending = null;
            } else {
                if (pending != null) members.add(pending);
                pending = line;
            }
        }
        if (pending != null) members.add(pending);
        return header == null ? null : new Type(name, header, members);
    }

    static String format(Map<String, Type> types) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Base de compatibilidade binária da api.v1 (gerada por scripts/ApiCompat.java; não edite à mão).\n");
        for (Type t : types.values()) {
            sb.append("C ").append(t.name()).append(" :: ").append(t.header()).append('\n');
            for (String m : t.members()) sb.append("M ").append(m).append('\n');
        }
        return sb.toString();
    }

    static Map<String, Type> parse(String text) {
        Map<String, Type> types = new LinkedHashMap<>();
        Type open = null;
        for (String line : text.split("\\R")) {
            if (line.startsWith("C ")) {
                int sep = line.indexOf(" :: ");
                open = new Type(line.substring(2, sep), line.substring(sep + 4), new ArrayList<>());
                types.put(open.name(), open);
            } else if (line.startsWith("M ") && open != null) open.members().add(line.substring(2));
        }
        return types;
    }
}

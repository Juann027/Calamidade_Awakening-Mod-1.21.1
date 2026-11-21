// Gera docs/ai/API-REFERENCIA.md a partir das assinaturas públicas de dev.calamidade_awakening.api.v1 (javap -public
// no JAR compilado, pelo ToolProvider do próprio JDK) com uma linha de descrição por membro, tirada do Javadoc dos
// fontes. Programa Java de arquivo único (Java 21), sem dependências:
//
//   java scripts/ApiReferencia.java --jar build/libs/calamidade_awakening-0.9.0.jar --src src/main/java \
//        --out docs/ai/API-REFERENCIA.md [--check]
//
// No Gradle: ./gradlew apiDocs (regenera) e checkApiDocs (parte do build; falha se o arquivo estiver desatualizado).
// Falha também quando um membro público não tem Javadoc: toda a api.v1 precisa estar documentada.
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class ApiReferencia {
    static final String PACKAGE = "dev/calamidade_awakening/api/v1/";

    /** Javadoc of one declaration: first sentence and @param descriptions. */
    record Doc(String summary, Map<String, String> params) {}

    /** Documented member found in the sources. */
    record Member(String owner, String name, List<String> types, List<String> names, Doc doc) {}

    public static void main(String[] args) throws Exception {
        Map<String, String> opt = new HashMap<>();
        boolean check = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--check")) check = true;
            else opt.put(args[i], args[++i]);
        }
        Path jar = Path.of(opt.get("--jar")), src = Path.of(opt.get("--src")), out = Path.of(opt.get("--out"));
        Sources sources = new Sources();
        try (Stream<Path> files = Files.walk(src.resolve(PACKAGE))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) sources.parse(file);
        }
        List<String> classes = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            zip.stream().map(e -> e.getName())
                .filter(n -> n.startsWith(PACKAGE) && n.endsWith(".class") && !n.matches(".*\\$\\d.*"))
                .map(n -> n.substring(0, n.length() - 6).replace('/', '.'))
                .sorted().forEach(classes::add);
        }
        String version = jar.getFileName().toString().replaceAll(".*-([0-9][^-]*)\\.jar$", "$1");
        List<String> missing = new ArrayList<>();
        String markdown = render(version, javap(jar, classes), sources, missing);
        if (!missing.isEmpty()) {
            System.err.println("Membros públicos sem Javadoc (" + missing.size() + "):");
            missing.forEach(m -> System.err.println("  " + m));
            System.exit(2);
        }
        if (check) {
            String current = Files.exists(out) ? Files.readString(out, StandardCharsets.UTF_8) : "";
            if (!current.equals(markdown)) {
                System.err.println(out + " está desatualizado em relação à api.v1. Rode: ./gradlew apiDocs");
                System.exit(1);
            }
            System.out.println(out + " está atualizado.");
        } else {
            Files.createDirectories(out.getParent());
            Files.writeString(out, markdown, StandardCharsets.UTF_8);
            System.out.println("Gerado " + out);
        }
    }

    // ------------------------------------------------------------------------------------------------ javap

    /** Runs javap -public once for every class and splits the output per class. */
    static Map<String, List<String>> javap(Path jar, List<String> classes) {
        ToolProvider tool = ToolProvider.findFirst("javap").orElseThrow(() -> new IllegalStateException("javap ausente"));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        List<String> args = new ArrayList<>(List.of("-public", "-cp", jar.toString()));
        args.addAll(classes);
        int code = tool.run(new PrintStream(buffer, true, StandardCharsets.UTF_8), System.err, args.toArray(String[]::new));
        if (code != 0) throw new IllegalStateException("javap falhou: " + code);
        Map<String, List<String>> result = new LinkedHashMap<>();
        List<String> current = null;
        for (String line : buffer.toString(StandardCharsets.UTF_8).split("\n")) {
            line = line.strip();
            if (line.startsWith("Compiled from")) { current = null; continue; }
            if (current == null && line.endsWith("{")) {
                Matcher m = Pattern.compile("^public .*?(?:class|interface) ([\\w.$]+)").matcher(line);
                if (!m.find()) continue;
                current = new ArrayList<>();
                current.add(line);
                result.put(m.group(1), current);
            } else if (current != null && line.endsWith(";")) current.add(line);
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------------ render

    static String render(String version, Map<String, List<String>> classes, Sources sources, List<String> missing) {
        StringBuilder md = new StringBuilder();
        md.append("# Referência da API pública (api.v1) — Calamidade_Awakening ").append(version).append("\n\n");
        md.append("> Gerado por `scripts/ApiReferencia.java` a partir do `javap -public` do JAR compilado e do Javadoc dos fontes. ")
          .append("Não edite à mão: rode `./gradlew apiDocs`. O build (`checkApiDocs`) falha se este arquivo estiver desatualizado ")
          .append("ou se um membro público ficar sem Javadoc.\n\n");
        md.append("Unidades: tempo em ticks (20 por segundo), distâncias em blocos, cores em ARGB (`0xAARRGGBB`), frações em 0..1. ")
          .append("Posições de cliente (`post.center()`, uniforms de shader) são relativas à câmera. Entrada inválida lança `IllegalArgumentException`. ")
          .append("Guia por tarefa: [AGENTS.md](../../AGENTS.md) e [receitas](receitas/).\n\n");
        md.append("## Índice\n\n");
        for (String cls : classes.keySet()) {
            String display = display(cls);
            md.append("- [`").append(display).append("`](#").append(anchor(display)).append(")\n");
        }
        md.append('\n');
        for (var entry : classes.entrySet()) {
            String cls = entry.getKey();
            List<String> lines = entry.getValue();
            String header = lines.getFirst();
            String owner = cls.replace('$', '.');
            String display = display(cls);
            String kind = header.contains(" interface ") ? "interface" : header.contains("extends java.lang.Record") ? "record"
                : header.contains("extends java.lang.Enum") ? "enum" : "class";
            md.append("## ").append(display).append("\n\n");
            md.append("`").append(cls.replace('$', '.')).append("` · ").append(kind).append("\n\n");
            Doc typeDoc = sources.types.get(owner);
            if (typeDoc == null || typeDoc.summary().isEmpty()) missing.add(display + " (tipo)");
            else md.append(typeDoc.summary()).append("\n\n");
            List<String[]> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                String[] row = member(cls, owner, kind, line, sources, typeDoc, missing);
                if (row != null) rows.add(row);
            }
            if (!rows.isEmpty()) {
                md.append("| Membro | Descrição |\n|---|---|\n");
                for (String[] row : rows) md.append("| `").append(row[0]).append("` | ").append(row[1]).append(" |\n");
                md.append('\n');
            }
        }
        return md.toString();
    }

    /** One table row for a javap member line, or null for members left out (enum values/valueOf, record basics). */
    static String[] member(String cls, String owner, String kind, String line, Sources sources, Doc typeDoc, List<String> missing) {
        String decl = line.substring(0, line.length() - 1).replaceAll(" throws .*$", "");
        String simpleClass = cls.substring(cls.lastIndexOf('.') + 1);
        int paren = decl.indexOf('(');
        if (paren < 0) { // field or enum constant
            String name = decl.substring(decl.lastIndexOf(' ') + 1);
            String[] split = splitHead(decl.substring(0, decl.lastIndexOf(' ')));
            String type = simplifyGenerics(split[1]);
            boolean constant = kind.equals("enum") && type.equals(display(cls));
            Doc doc = sources.field(owner, name);
            String desc = doc == null ? null : doc.summary();
            if (desc == null || desc.isEmpty()) { missing.add(display(cls) + "." + name); desc = ""; }
            return new String[]{constant ? name : (split[0] + type + " " + name).strip(), desc};
        }
        String head = decl.substring(0, paren);
        String name = head.substring(head.lastIndexOf(' ') + 1);
        String[] split = splitHead(head.substring(0, Math.max(0, head.lastIndexOf(' '))));
        List<String> types = splitTypes(decl.substring(paren + 1, decl.lastIndexOf(')')));
        boolean ctor = name.equals(cls);
        if (kind.equals("enum") && (name.equals("values") && types.isEmpty() || name.equals("valueOf") && types.size() == 1)) return null;
        if (kind.equals("record") && (name.equals("toString") && types.isEmpty() || name.equals("hashCode") && types.isEmpty()
            || name.equals("equals") && types.size() == 1)) return null;
        String memberName = ctor ? simpleClass.substring(simpleClass.lastIndexOf('$') + 1) : name;
        List<String> simple = types.stream().map(ApiReferencia::simpleType).toList();
        Member found = sources.method(owner, memberName, simple);
        String desc = found == null ? null : found.doc().summary();
        List<String> paramNames = found == null ? null : found.names();
        if ((desc == null || desc.isEmpty()) && typeDoc != null) {
            // Record accessor: the record's @param; canonical record constructor: the record summary.
            if (kind.equals("record") && types.isEmpty() && typeDoc.params().containsKey(name)) desc = sentence(typeDoc.params().get(name));
            else if (kind.equals("record") && ctor) {
                List<String> components = sources.recordComponents.get(owner);
                if (components != null && components.size() == types.size()) {
                    desc = "Canonical constructor: " + String.join(", ", components) + ".";
                    paramNames = components;
                }
            }
        }
        if (desc == null || desc.isEmpty()) { missing.add(display(cls) + "." + memberName + simple); desc = ""; }
        StringBuilder sig = new StringBuilder();
        if (ctor) sig.append("new ").append(memberName);
        else sig.append(split[0]).append(simplifyGenerics(split[1])).append(' ').append(name);
        sig.append('(');
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sig.append(", ");
            sig.append(simplifyGenerics(types.get(i)));
            if (paramNames != null && i < paramNames.size()) sig.append(' ').append(paramNames.get(i));
        }
        sig.append(')');
        return new String[]{sig.toString().strip(), desc};
    }

    /** Splits "public static final java.util.Map<K, V>" into kept modifiers ("static ") and the type. */
    static String[] splitHead(String head) {
        StringBuilder mods = new StringBuilder();
        String rest = head.strip();
        while (true) {
            Matcher m = Pattern.compile("^(public|protected|static|final|abstract|default|synchronized|native|transient|volatile|<[^>]*>)\\s+").matcher(rest);
            if (!m.find()) break;
            if (m.group(1).equals("static") || m.group(1).equals("default")) mods.append(m.group(1)).append(' ');
            rest = rest.substring(m.end());
        }
        return new String[]{mods.toString(), rest};
    }

    /** Capitalized text ending with a period (record component descriptions come as fragments). */
    static String sentence(String text) {
        if (text.isEmpty()) return text;
        String t = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        return t.endsWith(".") ? t : t + ".";
    }

    static String display(String cls) {
        String simple = cls.startsWith("dev.calamidade_awakening.api.v1.client.") ? cls.substring(39)
            : cls.substring("dev.calamidade_awakening.api.v1.".length());
        return simple.replace('$', '.');
    }

    static String anchor(String display) { return display.toLowerCase().replace(".", ""); }

    /** Splits javap parameter types at top-level commas. */
    static List<String> splitTypes(String params) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) { out.add(params.substring(start, i).strip()); start = i + 1; }
        }
        if (!params.isBlank()) out.add(params.substring(start).strip());
        return out;
    }

    /** Simple name without generics or packages: java.util.List<x.Y> -> List; a.B$C... -> C... */
    static String simpleType(String type) {
        String t = type.replaceAll("<.*>", "").strip();
        String suffix = "";
        while (t.endsWith("[]")) { suffix += "[]"; t = t.substring(0, t.length() - 2); }
        if (t.endsWith("...")) { suffix = "..." + suffix; t = t.substring(0, t.length() - 3); }
        t = t.substring(t.lastIndexOf('.') + 1);
        t = t.substring(t.lastIndexOf('$') + 1);
        return t + suffix;
    }

    /** Shortens every qualified name inside a type, keeping generics: java.util.List<a.B$C> -> List<B.C>. */
    static String simplifyGenerics(String type) {
        Matcher m = Pattern.compile("[\\w$]+(?:\\.[\\w$]+)+").matcher(type);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String q = m.group();
            String simple = q.startsWith("dev.calamidade_awakening.api.v1.") ? display(q) : q.substring(q.lastIndexOf('.') + 1).replace('$', '.');
            m.appendReplacement(sb, Matcher.quoteReplacement(simple));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------ sources

    /** Minimal Java source reader: Javadoc of types, fields, methods and enum constants, keyed by owner path. */
    static final class Sources {
        final Map<String, Doc> types = new HashMap<>();
        final Map<String, Doc> fields = new HashMap<>();
        final List<Member> methods = new ArrayList<>();
        final Map<String, List<String>> recordComponents = new HashMap<>();

        Doc field(String owner, String name) { return fields.get(owner + "#" + name); }

        Member method(String owner, String name, List<String> types) {
            for (Member m : methods)
                if (m.owner().equals(owner) && m.name().equals(name) && m.types().equals(types)) return m;
            return null;
        }

        private static final class Frame {
            final String path; final boolean type, isEnum; boolean enumDone;
            Frame(String path, boolean type, boolean isEnum) { this.path = path; this.type = type; this.isEnum = isEnum; }
        }

        void parse(Path file) throws IOException {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher pkg = Pattern.compile("^package ([\\w.]+);", Pattern.MULTILINE).matcher(text);
            String packageName = pkg.find() ? pkg.group(1) : "";
            List<String> docs = new ArrayList<>();
            String code = strip(text, docs);
            List<Frame> stack = new ArrayList<>();
            stack.add(new Frame(packageName, false, false));
            StringBuilder buffer = new StringBuilder();
            int pendingDoc = -1, parens = 0;
            for (int i = 0; i < code.length(); i++) {
                char c = code.charAt(i);
                Frame top = stack.getLast();
                boolean member = top.type || stack.size() == 1;
                if (c == '\u0001') {
                    int end = code.indexOf('\u0002', i);
                    if (member) pendingDoc = Integer.parseInt(code.substring(i + 1, end));
                    i = end;
                    continue;
                }
                if (c == '(') parens++;
                if (c == ')') parens--;
                if (top.type && top.isEnum && !top.enumDone && parens == 0 && (c == ',' || c == ';' || c == '}')) {
                    String constant = buffer.toString().strip();
                    Matcher m = Pattern.compile("^(?:@\\w+\\s*)*([A-Z][A-Z0-9_]*)").matcher(constant);
                    if (m.find() && pendingDoc >= 0) fields.put(top.path + "#" + m.group(1), doc(docs.get(pendingDoc)));
                    buffer.setLength(0);
                    pendingDoc = -1;
                    if (c == ';') { top.enumDone = true; continue; }
                    if (c == ',') continue;
                }
                if (c == '{' && parens == 0) {
                    String stmt = clean(buffer.toString());
                    Doc doc = pendingDoc >= 0 ? doc(docs.get(pendingDoc)) : null;
                    if (member) {
                        Matcher m = Pattern.compile("\\b(class|interface|enum|record)\\s+(\\w+)").matcher(stmt);
                        if (m.find()) {
                            String path = stack.size() == 1 ? packageName + "." + m.group(2) : top.path + "." + m.group(2);
                            if (doc != null) types.put(path, doc);
                            if (m.group(1).equals("record")) recordComponents.put(path, componentNames(stmt));
                            stack.add(new Frame(path, true, m.group(1).equals("enum")));
                        } else {
                            declaration(top.path, stmt, doc);
                            stack.add(new Frame(top.path, false, false));
                        }
                    } else stack.add(new Frame(top.path, false, false));
                    buffer.setLength(0);
                    pendingDoc = -1;
                    continue;
                }
                if (c == '}' && parens == 0) {
                    stack.removeLast();
                    buffer.setLength(0);
                    pendingDoc = -1;
                    continue;
                }
                if (c == ';' && parens == 0) {
                    if (member) declaration(top.path, clean(buffer.toString()), pendingDoc >= 0 ? doc(docs.get(pendingDoc)) : null);
                    buffer.setLength(0);
                    pendingDoc = -1;
                    continue;
                }
                buffer.append(c);
            }
        }

        /** Field (one or several names) or method/constructor declaration. */
        void declaration(String owner, String stmt, Doc doc) {
            if (doc == null || stmt.isEmpty() || stmt.startsWith("import ") || stmt.startsWith("package ")) return;
            int eq = topLevel(stmt, '=');
            int paren = topLevel(stmt, '(');
            if (paren >= 0 && (eq < 0 || paren < eq)) {
                Matcher m = Pattern.compile("(\\w+)\\s*$").matcher(stmt.substring(0, paren));
                if (!m.find()) return;
                int close = matching(stmt, paren);
                List<String> types = new ArrayList<>(), names = new ArrayList<>();
                for (String param : splitParams(stmt.substring(paren + 1, close))) {
                    String p = param.replaceAll("@\\w+(\\([^)]*\\))?", "").replaceAll("\\bfinal\\s+", "").strip();
                    int cut = p.lastIndexOf(' ');
                    String type = p.substring(0, cut).strip(), name = p.substring(cut + 1);
                    types.add(simpleType(type.replace(" ", "")));
                    names.add(name);
                }
                methods.add(new Member(owner, m.group(1), types, names, doc));
                return;
            }
            String names = eq >= 0 ? stmt.substring(0, eq) : stmt;
            for (String part : splitParams(names.replaceAll("^.*?\\s(?=\\w+\\s*(,|$))", ""))) {
                Matcher m = Pattern.compile("(\\w+)\\s*$").matcher(part.strip());
                if (m.find()) fields.put(owner + "#" + m.group(1), doc);
            }
        }

        static List<String> componentNames(String stmt) {
            int paren = stmt.indexOf('(');
            if (paren < 0) return List.of();
            List<String> names = new ArrayList<>();
            for (String param : splitParams(stmt.substring(paren + 1, matching(stmt, paren)))) {
                String p = param.strip();
                names.add(p.substring(p.lastIndexOf(' ') + 1));
            }
            return names;
        }

        static int topLevel(String s, char target) {
            int depth = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '<') depth++;
                else if (c == '>') depth--;
                else if (c == target && depth == 0) return i;
            }
            return -1;
        }

        static int matching(String s, int open) {
            int depth = 0;
            for (int i = open; i < s.length(); i++) {
                if (s.charAt(i) == '(') depth++;
                else if (s.charAt(i) == ')' && --depth == 0) return i;
            }
            return s.length();
        }

        static List<String> splitParams(String params) {
            List<String> out = new ArrayList<>();
            int depth = 0, start = 0;
            for (int i = 0; i < params.length(); i++) {
                char c = params.charAt(i);
                if (c == '<' || c == '(') depth++;
                else if (c == '>' || c == ')') depth--;
                else if (c == ',' && depth == 0) { out.add(params.substring(start, i)); start = i + 1; }
            }
            if (!params.isBlank()) out.add(params.substring(start));
            return out;
        }

        static String clean(String stmt) {
            return stmt.replaceAll("@\\w+(\\.\\w+)*(\\([^)]*\\))?", " ").replaceAll("\\s+", " ").strip();
        }

        /** Code with Javadoc replaced by \u0001index\u0002 markers, other comments removed and literals emptied. */
        static String strip(String text, List<String> docs) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (text.startsWith("/**", i) && !text.startsWith("/**/", i)) {
                    int end = text.indexOf("*/", i + 3);
                    docs.add(text.substring(i + 3, end));
                    out.append('\u0001').append(docs.size() - 1).append('\u0002');
                    i = end + 1;
                } else if (text.startsWith("/*", i)) {
                    i = text.indexOf("*/", i + 2) + 1;
                    out.append(' ');
                } else if (text.startsWith("//", i)) {
                    int end = text.indexOf('\n', i);
                    i = (end < 0 ? text.length() : end) - 1;
                } else if (c == '"' || c == '\'') {
                    int j = i + 1;
                    while (j < text.length() && text.charAt(j) != c) j += text.charAt(j) == '\\' ? 2 : 1;
                    out.append(c).append(c);
                    i = j;
                } else out.append(c);
            }
            return out.toString();
        }

        /** First sentence of the description and the @param texts, with inline tags and HTML simplified. */
        static Doc doc(String raw) {
            StringBuilder body = new StringBuilder();
            for (String line : raw.split("\n")) body.append(line.replaceFirst("^\\s*\\*\\s?", "")).append('\n');
            String text = body.toString();
            int tag = indexOfTag(text);
            String description = tag < 0 ? text : text.substring(0, tag);
            Map<String, String> params = new LinkedHashMap<>();
            if (tag >= 0) {
                Matcher m = Pattern.compile("@param\\s+(\\w+)\\s+([\\s\\S]*?)(?=\\n\\s*@|$)").matcher(text.substring(tag));
                while (m.find()) params.put(m.group(1), inline(m.group(2)));
            }
            description = description.replaceAll("(?s)<pre>.*?</pre>", " ");
            String flat = inline(description);
            Matcher sentence = Pattern.compile("^(.*?[.!?])(?=\\s+[A-Z{(`]|\\s*$)").matcher(flat);
            String summary = sentence.find() ? sentence.group(1) : flat;
            return new Doc(summary.strip(), params);
        }

        static int indexOfTag(String text) {
            Matcher m = Pattern.compile("(?m)^\\s*@(param|return|throws|see|since|deprecated)\\b").matcher(text);
            return m.find() ? m.start() : -1;
        }

        static String inline(String s) {
            String t = s.replaceAll("\\{@code\\s+([^}]*)}", "`$1`")
                .replaceAll("\\{@value\\s+#?([\\w.#]+)}", "`$1`")
                .replaceAll("\\{@link(?:plain)?\\s+([^}\\s]+)\\s+([^}]+)}", "$2")
                .replaceAll("\\{@link(?:plain)?\\s+([^}]+)}", "`$1`")
                .replaceAll("`#(\\w+)", "`$1")
                .replaceAll("`(\\w+)#(\\w+)", "`$1.$2")
                .replaceAll("</?(p|em|b|i|ul|ol|li|code|pre)>", " ")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("|", "\\|");
            // Outside code spans, angle brackets would be read as HTML by Markdown renderers.
            String[] spans = t.split("`", -1);
            for (int i = 0; i < spans.length; i += 2) spans[i] = spans[i].replace("<", "&lt;").replace(">", "&gt;");
            return String.join("`", spans).replaceAll("\\s+", " ").strip();
        }
    }

}

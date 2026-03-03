package com.afterlands.core.config.update;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * Reconstrói um arquivo YAML completo com:
 * <ul>
 *   <li>Comentários do default (via {@link YamlCommentParser.ParseResult})</li>
 *   <li>Valores do usuário (quando existem)</li>
 *   <li>Valores do default (para keys novas)</li>
 *   <li>Respeito a seções user-owned via {@link MergeOptions}</li>
 * </ul>
 *
 * <p>Nunca usa {@code config.saveToString()}, preservando todos os comentários.</p>
 */
public final class CommentAwareWriter {

    private static final String INDENT_UNIT = "  ";

    private final Yaml yaml;

    public CommentAwareWriter() {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);
        opts.setWidth(200);
        this.yaml = new Yaml(opts);
    }

    /**
     * Reconstrói o arquivo YAML completo.
     *
     * @param userConfig    Configuração do usuário (pós-migrations)
     * @param defaultConfig Configuração default (do JAR)
     * @param parseResult   Comentários parseados do default
     * @param mergeOptions  Opções de merge (seções user-owned)
     * @param defaultLines  Linhas originais do default (para ordem das keys)
     * @return Conteúdo YAML completo como String
     */
    public String write(@NotNull FileConfiguration userConfig,
                        @NotNull FileConfiguration defaultConfig,
                        @NotNull YamlCommentParser.ParseResult parseResult,
                        @NotNull MergeOptions mergeOptions,
                        @NotNull List<String> defaultLines) {

        StringBuilder sb = new StringBuilder();

        // 1. Header comments
        String header = parseResult.getHeaderComments();
        if (!header.isEmpty()) {
            sb.append(header);
        }

        // 2. Iterar root keys do default na ORDEM do arquivo default
        List<String> orderedRootKeys = getOrderedRootKeys(defaultLines);
        Set<String> writtenKeys = new HashSet<>();

        for (String rootKey : orderedRootKeys) {
            if (writtenKeys.contains(rootKey)) continue;
            writtenKeys.add(rootKey);

            writeKey(sb, rootKey, rootKey, 0, userConfig, defaultConfig,
                    parseResult.getKeyComments(), mergeOptions);
        }

        // 3. Keys do default que possam não ter sido capturadas pela ordem textual
        for (String key : defaultConfig.getKeys(false)) {
            if (!writtenKeys.contains(key)) {
                writtenKeys.add(key);
                writeKey(sb, key, key, 0, userConfig, defaultConfig,
                        parseResult.getKeyComments(), mergeOptions);
            }
        }

        // 4. Footer
        String footer = parseResult.getFooterComments();
        if (!footer.isEmpty()) {
            sb.append(footer);
        }

        return sb.toString();
    }

    private void writeKey(StringBuilder sb, String key, String fullPath, int depth,
                          ConfigurationSection userConfig,
                          ConfigurationSection defaultConfig,
                          Map<String, String> comments,
                          MergeOptions mergeOptions) {

        String indent = getIndent(depth);

        // Escrever comentários associados a este path
        String comment = comments.get(fullPath);
        if (comment != null) {
            sb.append(comment);
        }

        // Determinar fonte dos dados
        boolean userHasKey = userConfig.contains(key);
        Object defaultVal = defaultConfig.get(key);
        Object userVal = userHasKey ? userConfig.get(key) : null;

        // Seção user-owned: copiar inteira do usuário (ou default se não existir)
        if (mergeOptions.isUserOwned(fullPath)) {
            ConfigurationSection source = null;
            Object sourceVal = null;

            if (userHasKey) {
                source = (userVal instanceof ConfigurationSection) ? (ConfigurationSection) userVal : null;
                sourceVal = userVal;
            } else {
                source = (defaultVal instanceof ConfigurationSection) ? (ConfigurationSection) defaultVal : null;
                sourceVal = defaultVal;
            }

            if (source != null) {
                sb.append(indent).append(quoteKeyIfNeeded(key)).append(":\n");
                writeUserOwnedSection(sb, source, depth + 1);
            } else {
                writeScalar(sb, indent, key, sourceVal, depth);
            }
            return;
        }

        // Seção normal (recursiva)
        if (defaultVal instanceof ConfigurationSection) {
            ConfigurationSection defaultSection = (ConfigurationSection) defaultVal;
            ConfigurationSection userSection = (userVal instanceof ConfigurationSection)
                    ? (ConfigurationSection) userVal : null;

            sb.append(indent).append(quoteKeyIfNeeded(key)).append(":\n");

            // Iterar sub-keys do default
            for (String subKey : defaultSection.getKeys(false)) {
                String subFullPath = fullPath + "." + subKey;

                writeKey(sb, subKey, subFullPath, depth + 1,
                        userSection != null ? userSection : defaultSection,
                        defaultSection, comments, mergeOptions);
            }
        } else {
            // Valor simples: usar valor do user se existir, senão default
            Object value = userHasKey ? userVal : defaultVal;
            writeScalar(sb, indent, key, value, depth);
        }
    }

    private void writeScalar(StringBuilder sb, String indent, String key, Object value, int depth) {
        sb.append(indent).append(quoteKeyIfNeeded(key)).append(":");
        appendValue(sb, value, depth);
    }

    /**
     * Serializa uma seção inteira do user (user-owned), sem consultar o default.
     */
    private void writeUserOwnedSection(StringBuilder sb, ConfigurationSection section, int depth) {
        String indent = getIndent(depth);

        for (String key : section.getKeys(false)) {
            Object val = section.get(key);

            if (val instanceof ConfigurationSection) {
                sb.append(indent).append(quoteKeyIfNeeded(key)).append(":\n");
                writeUserOwnedSection(sb, (ConfigurationSection) val, depth + 1);
            } else {
                sb.append(indent).append(quoteKeyIfNeeded(key)).append(":");
                appendValue(sb, val, depth);
            }
        }
    }

    /**
     * Appends a value (scalar or list) to the StringBuilder, handling indentation.
     */
    private void appendValue(StringBuilder sb, Object value, int depth) {
        if (value == null) {
            sb.append(" ''").append("\n");
            return;
        }

        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                sb.append(" []\n");
            } else {
                sb.append("\n");
                String itemIndent = getIndent(depth + 1);
                for (Object item : list) {
                    sb.append(itemIndent).append("- ");
                    if (item instanceof String) {
                        sb.append(serializeString((String) item));
                    } else if (item instanceof Map) {
                        sb.append(yaml.dump(item).trim());
                    } else if (item == null) {
                        sb.append("''");
                    } else {
                        sb.append(item);
                    }
                    sb.append("\n");
                }
            }
            return;
        }

        sb.append(" ").append(serializeScalar(value)).append("\n");
    }

    /**
     * Serializa um valor escalar (não-lista, não-seção).
     */
    private String serializeScalar(Object value) {
        if (value == null) return "''";
        if (value instanceof String) return serializeString((String) value);
        if (value instanceof Boolean || value instanceof Number) return String.valueOf(value);
        if (value instanceof Map) return yaml.dump(value).trim();
        return serializeString(String.valueOf(value));
    }

    private String serializeString(String value) {
        if (value.isEmpty()) return "''";

        boolean needsQuotes = false;

        // YAML booleans / null
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")
                || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("no")
                || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("off")
                || value.equalsIgnoreCase("null")) {
            needsQuotes = true;
        }

        // Numeric strings
        if (!needsQuotes) {
            try {
                Double.parseDouble(value);
                needsQuotes = true;
            } catch (NumberFormatException ignored) {}
        }

        // Special YAML characters
        if (!needsQuotes) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == ':' || c == '#' || c == '[' || c == ']'
                        || c == '{' || c == '}' || c == ',' || c == '&'
                        || c == '*' || c == '?' || c == '|' || c == '-'
                        || c == '<' || c == '>' || c == '=' || c == '!'
                        || c == '%' || c == '@' || c == '`' || c == '\''
                        || c == '"' || c == '\n') {
                    needsQuotes = true;
                    break;
                }
            }
        }

        if (value.startsWith(" ") || value.endsWith(" ")) {
            needsQuotes = true;
        }

        if (needsQuotes) {
            return "'" + value.replace("'", "''") + "'";
        }

        return value;
    }

    private String quoteKeyIfNeeded(String key) {
        if (isNumeric(key)) return "'" + key + "'";
        if (key.contains(" ") || key.contains(":") || key.contains("#")) {
            return "'" + key + "'";
        }
        return key;
    }

    private boolean isNumeric(String str) {
        if (str.isEmpty()) return false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private String getIndent(int depth) {
        if (depth <= 0) return "";
        StringBuilder sb = new StringBuilder(depth * 2);
        for (int i = 0; i < depth; i++) {
            sb.append(INDENT_UNIT);
        }
        return sb.toString();
    }

    /**
     * Extrai root keys do arquivo default na ordem em que aparecem no texto.
     */
    static List<String> getOrderedRootKeys(List<String> lines) {
        List<String> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String line : lines) {
            if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '#'
                    || line.charAt(0) == '-') {
                continue;
            }
            String keyName = YamlCommentParser.extractKeyName(line.trim());
            if (keyName != null && seen.add(keyName)) {
                keys.add(keyName);
            }
        }
        return keys;
    }
}

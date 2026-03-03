package com.afterlands.core.config.update;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Parseia linhas de um arquivo YAML default e mapeia cada path
 * ao bloco de comentários que o precede.
 *
 * <p>
 * Permite ao {@link CommentAwareWriter} reconstruir o arquivo
 * preservando comentários do default.
 * </p>
 */
public final class YamlCommentParser {

    private YamlCommentParser() {}

    /**
     * Resultado do parsing: header, mapa de comentários por path, e footer.
     */
    public static final class ParseResult {
        private final String headerComments;
        private final Map<String, String> keyComments;
        private final String footerComments;

        ParseResult(String headerComments, Map<String, String> keyComments, String footerComments) {
            this.headerComments = headerComments;
            this.keyComments = keyComments;
            this.footerComments = footerComments;
        }

        /** Comentários antes da primeira key (header do arquivo). */
        public String getHeaderComments() { return headerComments; }

        /** Mapa de path YAML → bloco de comentários acima da key. */
        public Map<String, String> getKeyComments() { return keyComments; }

        /** Comentários após a última key (fim do arquivo). */
        public String getFooterComments() { return footerComments; }
    }

    /**
     * Parseia as linhas do arquivo default e retorna mapeamento path → comentários.
     *
     * @param defaultLines Linhas do arquivo YAML default
     * @return ParseResult com header, key comments e footer
     */
    public static ParseResult parse(@NotNull List<String> defaultLines) {
        Map<String, String> keyComments = new LinkedHashMap<>();
        StringBuilder commentBuffer = new StringBuilder();
        Deque<IndentKey> stack = new ArrayDeque<>();
        String headerComments = null;
        boolean foundFirstKey = false;
        int lastKeyLine = -1;

        for (int i = 0; i < defaultLines.size(); i++) {
            String line = defaultLines.get(i);
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                // Acumular comentário / linha em branco
                commentBuffer.append(line).append("\n");
                continue;
            }

            // É uma linha de key YAML?
            int indent = getIndent(line);
            String keyName = extractKeyName(trimmed);

            if (keyName == null) {
                // Linha de valor (lista, continuação, etc.) — não é key
                commentBuffer.setLength(0);
                continue;
            }

            // Primeira key encontrada: tudo acumulado até agora é header
            if (!foundFirstKey) {
                headerComments = commentBuffer.toString();
                commentBuffer.setLength(0);
                foundFirstKey = true;
            }

            // Ajustar stack: pop até encontrar indent menor
            while (!stack.isEmpty() && stack.peek().indent >= indent) {
                stack.pop();
            }

            // Construir path completo
            stack.push(new IndentKey(indent, keyName));
            String fullPath = buildPath(stack);

            // Salvar comentários acumulados para este path
            if (commentBuffer.length() > 0) {
                keyComments.put(fullPath, commentBuffer.toString());
                commentBuffer.setLength(0);
            }

            lastKeyLine = i;
        }

        // Footer: qualquer coisa restante no buffer após a última key
        String footerComments = commentBuffer.toString();

        return new ParseResult(
                headerComments != null ? headerComments : "",
                keyComments,
                footerComments
        );
    }

    private static int getIndent(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else break;
        }
        return count;
    }

    static String extractKeyName(String trimmed) {
        // Ignorar linhas de lista ("- item")
        if (trimmed.startsWith("-")) return null;

        // Chave entre aspas: 'key': ou "key":
        if (trimmed.startsWith("'") || trimmed.startsWith("\"")) {
            char quote = trimmed.charAt(0);
            int end = trimmed.indexOf(quote, 1);
            if (end > 0 && end + 1 < trimmed.length() && trimmed.charAt(end + 1) == ':') {
                return trimmed.substring(1, end);
            }
            return null;
        }

        // Chave simples: key:
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx > 0) {
            String candidate = trimmed.substring(0, colonIdx);
            // Validar que é uma key válida (sem espaços antes do colon, exceto se quoted)
            if (!candidate.contains(" ") || candidate.matches("[a-zA-Z0-9_\\-. ]+")) {
                return candidate;
            }
        }
        return null;
    }

    private static String buildPath(Deque<IndentKey> stack) {
        List<String> parts = new ArrayList<>();
        // Stack é LIFO, precisamos inverter
        for (IndentKey ik : stack) {
            parts.add(ik.key);
        }
        Collections.reverse(parts);
        return String.join(".", parts);
    }

    private static final class IndentKey {
        final int indent;
        final String key;

        IndentKey(int indent, String key) {
            this.indent = indent;
            this.key = key;
        }
    }
}

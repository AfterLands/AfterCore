package com.afterlands.core.util;

import org.bukkit.ChatColor;

/**
 * Utilitários para manipulação de strings.
 */
public final class StringUtil {

    private static final int CENTER_PX = 154;
    private static final int MAX_PX = 250;
    private static final int[] CHAR_WIDTHS = new int[256];

    static {
        // Minecraft default font character widths
        CHAR_WIDTHS[' '] = 4;
        CHAR_WIDTHS['!'] = 2;
        CHAR_WIDTHS['"'] = 5;
        CHAR_WIDTHS['#'] = 6;
        CHAR_WIDTHS['$'] = 6;
        CHAR_WIDTHS['%'] = 6;
        CHAR_WIDTHS['&'] = 6;
        CHAR_WIDTHS['\''] = 3;
        CHAR_WIDTHS['('] = 5;
        CHAR_WIDTHS[')'] = 5;
        CHAR_WIDTHS['*'] = 5;
        CHAR_WIDTHS['+'] = 6;
        CHAR_WIDTHS[','] = 2;
        CHAR_WIDTHS['-'] = 6;
        CHAR_WIDTHS['.'] = 2;
        CHAR_WIDTHS['/'] = 6;
        CHAR_WIDTHS['0'] = 6;
        CHAR_WIDTHS['1'] = 6;
        CHAR_WIDTHS['2'] = 6;
        CHAR_WIDTHS['3'] = 6;
        CHAR_WIDTHS['4'] = 6;
        CHAR_WIDTHS['5'] = 6;
        CHAR_WIDTHS['6'] = 6;
        CHAR_WIDTHS['7'] = 6;
        CHAR_WIDTHS['8'] = 6;
        CHAR_WIDTHS['9'] = 6;
        CHAR_WIDTHS[':'] = 2;
        CHAR_WIDTHS[';'] = 2;
        CHAR_WIDTHS['<'] = 5;
        CHAR_WIDTHS['='] = 6;
        CHAR_WIDTHS['>'] = 5;
        CHAR_WIDTHS['?'] = 6;
        CHAR_WIDTHS['@'] = 7;
        CHAR_WIDTHS['A'] = 6;
        CHAR_WIDTHS['B'] = 6;
        CHAR_WIDTHS['C'] = 6;
        CHAR_WIDTHS['D'] = 6;
        CHAR_WIDTHS['E'] = 6;
        CHAR_WIDTHS['F'] = 6;
        CHAR_WIDTHS['G'] = 6;
        CHAR_WIDTHS['H'] = 6;
        CHAR_WIDTHS['I'] = 4;
        CHAR_WIDTHS['J'] = 6;
        CHAR_WIDTHS['K'] = 6;
        CHAR_WIDTHS['L'] = 6;
        CHAR_WIDTHS['M'] = 6;
        CHAR_WIDTHS['N'] = 6;
        CHAR_WIDTHS['O'] = 6;
        CHAR_WIDTHS['P'] = 6;
        CHAR_WIDTHS['Q'] = 6;
        CHAR_WIDTHS['R'] = 6;
        CHAR_WIDTHS['S'] = 6;
        CHAR_WIDTHS['T'] = 6;
        CHAR_WIDTHS['U'] = 6;
        CHAR_WIDTHS['V'] = 6;
        CHAR_WIDTHS['W'] = 6;
        CHAR_WIDTHS['X'] = 6;
        CHAR_WIDTHS['Y'] = 6;
        CHAR_WIDTHS['Z'] = 6;
        CHAR_WIDTHS['['] = 4;
        CHAR_WIDTHS['\\'] = 6;
        CHAR_WIDTHS[']'] = 4;
        CHAR_WIDTHS['^'] = 6;
        CHAR_WIDTHS['_'] = 6;
        CHAR_WIDTHS['`'] = 3;
        CHAR_WIDTHS['a'] = 6;
        CHAR_WIDTHS['b'] = 6;
        CHAR_WIDTHS['c'] = 6;
        CHAR_WIDTHS['d'] = 6;
        CHAR_WIDTHS['e'] = 6;
        CHAR_WIDTHS['f'] = 5;
        CHAR_WIDTHS['g'] = 6;
        CHAR_WIDTHS['h'] = 6;
        CHAR_WIDTHS['i'] = 2;
        CHAR_WIDTHS['j'] = 6;
        CHAR_WIDTHS['k'] = 5;
        CHAR_WIDTHS['l'] = 3;
        CHAR_WIDTHS['m'] = 6;
        CHAR_WIDTHS['n'] = 6;
        CHAR_WIDTHS['o'] = 6;
        CHAR_WIDTHS['p'] = 6;
        CHAR_WIDTHS['q'] = 6;
        CHAR_WIDTHS['r'] = 6;
        CHAR_WIDTHS['s'] = 6;
        CHAR_WIDTHS['t'] = 4;
        CHAR_WIDTHS['u'] = 6;
        CHAR_WIDTHS['v'] = 6;
        CHAR_WIDTHS['w'] = 6;
        CHAR_WIDTHS['x'] = 6;
        CHAR_WIDTHS['y'] = 6;
        CHAR_WIDTHS['z'] = 6;
        CHAR_WIDTHS['{'] = 5;
        CHAR_WIDTHS['|'] = 2;
        CHAR_WIDTHS['}'] = 5;
        CHAR_WIDTHS['~'] = 7;

        // Default para caracteres não mapeados
        for (int i = 0; i < CHAR_WIDTHS.length; i++) {
            if (CHAR_WIDTHS[i] == 0) {
                CHAR_WIDTHS[i] = 6;
            }
        }
    }

    private StringUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Centraliza uma mensagem no chat do Minecraft.
     * Calcula a largura em pixels da mensagem e adiciona espaços à esquerda
     * para centralizar no chat (154 pixels de centro).
     *
     * @param message Mensagem a centralizar (pode conter color codes)
     * @return Mensagem centralizada
     */
    public static String centeredMessage(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // Remover color codes para cálculo de largura
        String stripped = ChatColor.stripColor(message);

        int messagePxSize = 0;
        boolean previousCode = false;
        boolean isBold = false;

        for (char c : message.toCharArray()) {
            if (c == '§' || c == '&') {
                previousCode = true;
            } else if (previousCode) {
                previousCode = false;
                isBold = (c == 'l' || c == 'L');
            } else {
                int charWidth = getCharWidth(c);
                messagePxSize += isBold ? charWidth + 1 : charWidth;
                messagePxSize++; // Espaçamento entre caracteres
            }
        }

        int halvedMessageSize = messagePxSize / 2;
        int toCompensate = CENTER_PX - halvedMessageSize;
        int spaceLength = getCharWidth(' ') + 1;
        int compensated = 0;

        StringBuilder sb = new StringBuilder();
        while (compensated < toCompensate) {
            sb.append(' ');
            compensated += spaceLength;
        }

        return sb.append(message).toString();
    }

    /**
     * Obtém a largura em pixels de um caractere no Minecraft.
     *
     * @param c Caractere
     * @return Largura em pixels
     */
    private static int getCharWidth(char c) {
        int index = (int) c;
        if (index >= 0 && index < CHAR_WIDTHS.length) {
            return CHAR_WIDTHS[index];
        }
        return 6; // Default
    }
}

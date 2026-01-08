package com.afterlands.core.conditions.impl;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConditionComparison {
    private static final Pattern NUMERIC_PATTERN =
            Pattern.compile("(.+?)\\s*(>=|<=|!=|==|>|<)\\s*(.+)");

    private final String left;
    private final ConditionOperator operator;
    private final String right;

    private ConditionComparison(String left, ConditionOperator operator, String right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Nullable
    static ConditionComparison parse(String expression) {
        // Primeiro tenta operadores textuais (ex.: contains, equalsIgnoreCase...)
        ConditionOperator op = ConditionOperator.findInString(expression);
        if (op == null) return null;

        String symbol = op.symbol();

        // Numeric operator parsing (==, !=, >= ...)
        if (symbol.length() <= 2 && (symbol.contains("=") || symbol.contains(">") || symbol.contains("<") || symbol.equals("~"))) {
            if (!symbol.equals("~")) {
                Matcher m = NUMERIC_PATTERN.matcher(expression.trim());
                if (!m.matches()) return null;
                String left = m.group(1).trim();
                String right = m.group(3).trim();
                ConditionOperator parsed = ConditionOperator.findInString(m.group(2));
                if (parsed == null) return null;
                return new ConditionComparison(left, parsed, right);
            }
            // legacy "~" split
            String[] parts = expression.split(Pattern.quote(symbol), 2);
            if (parts.length != 2) return null;
            return new ConditionComparison(parts[0].trim(), op, parts[1].trim());
        }

        // Word operator parsing
        Pattern pattern;
        if (symbol.startsWith("!")) {
            String baseOp = symbol.substring(1);
            pattern = Pattern.compile("(.+?)\\s+!"+ Pattern.quote(baseOp) + "(?=\\s|$)\\s*(.+)$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        } else {
            pattern = Pattern.compile("(.+?)\\s+" + Pattern.quote(symbol) + "(?=\\s|$)\\s*(.+)$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        }
        Matcher matcher = pattern.matcher(expression);
        if (!matcher.matches()) return null;
        return new ConditionComparison(matcher.group(1).trim(), op, matcher.group(2).trim());
    }

    boolean evaluate() {
        return operator.evaluate(left, right);
    }
}


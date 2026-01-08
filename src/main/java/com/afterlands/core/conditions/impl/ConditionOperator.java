package com.afterlands.core.conditions.impl;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Superset dos operadores do AfterBlockState + AfterMotion.
 */
enum ConditionOperator {

    // Numeric / basic string comparisons
    EQUALS("=="),
    NOT_EQUALS("!="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUALS(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUALS("<="),

    // Explicit string operators
    EQUALS_EXPLICIT("equals"),
    NOT_EQUALS_EXPLICIT("!equals"),
    EQUALS_IGNORE_CASE("equalsIgnoreCase"),
    NOT_EQUALS_IGNORE_CASE("!equalsIgnoreCase"),
    STARTS_WITH("startsWith"),
    NOT_STARTS_WITH("!startsWith"),
    CONTAINS("contains"),
    NOT_CONTAINS("!contains"),
    ENDS_WITH("endsWith"),
    NOT_ENDS_WITH("!endsWith"),
    MATCHES("matches"),
    NOT_MATCHES("!matches"),

    // Legacy contains operator
    CONTAINS_LEGACY("~");

    private final String symbol;

    ConditionOperator(String symbol) {
        this.symbol = symbol;
    }

    String symbol() {
        return symbol;
    }

    static ConditionOperator findInString(String str) {
        if (str == null) return null;

        // string ops first
        if (str.contains("!equalsIgnoreCase")) return NOT_EQUALS_IGNORE_CASE;
        if (str.contains("equalsIgnoreCase")) return EQUALS_IGNORE_CASE;
        if (str.contains("!startsWith")) return NOT_STARTS_WITH;
        if (str.contains("startsWith")) return STARTS_WITH;
        if (str.contains("!endsWith")) return NOT_ENDS_WITH;
        if (str.contains("endsWith")) return ENDS_WITH;
        if (str.contains("!contains")) return NOT_CONTAINS;
        if (str.contains("contains")) return CONTAINS;
        if (str.contains("!matches")) return NOT_MATCHES;
        if (str.contains("matches")) return MATCHES;
        if (str.contains("!equals")) return NOT_EQUALS_EXPLICIT;
        if (str.contains("equals")) return EQUALS_EXPLICIT;

        // numeric ops (longest first)
        if (str.contains(">=")) return GREATER_THAN_OR_EQUALS;
        if (str.contains("<=")) return LESS_THAN_OR_EQUALS;
        if (str.contains("!=")) return NOT_EQUALS;
        if (str.contains("==")) return EQUALS;
        if (str.contains(">")) return GREATER_THAN;
        if (str.contains("<")) return LESS_THAN;

        if (str.contains("~")) return CONTAINS_LEGACY;
        return null;
    }

    boolean evaluate(String leftRaw, String rightRaw) {
        if (leftRaw == null || rightRaw == null) return false;

        String left = leftRaw.trim();
        String right = rightRaw.trim();

        return switch (this) {
            case EQUALS -> left.equalsIgnoreCase(right);
            case NOT_EQUALS -> !left.equalsIgnoreCase(right);
            case GREATER_THAN, GREATER_THAN_OR_EQUALS, LESS_THAN, LESS_THAN_OR_EQUALS -> evaluateNumeric(left, right);

            case EQUALS_EXPLICIT -> left.equals(right);
            case NOT_EQUALS_EXPLICIT -> !left.equals(right);
            case EQUALS_IGNORE_CASE -> left.equalsIgnoreCase(right);
            case NOT_EQUALS_IGNORE_CASE -> !left.equalsIgnoreCase(right);
            case STARTS_WITH -> left.startsWith(right);
            case NOT_STARTS_WITH -> !left.startsWith(right);
            case CONTAINS, CONTAINS_LEGACY -> left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT));
            case NOT_CONTAINS -> !left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT));
            case ENDS_WITH -> left.endsWith(right);
            case NOT_ENDS_WITH -> !left.endsWith(right);
            case MATCHES -> safeMatches(left, right);
            case NOT_MATCHES -> !safeMatches(left, right);
        };
    }

    private boolean safeMatches(String left, String regex) {
        try {
            return Pattern.compile(regex).matcher(left).find();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean evaluateNumeric(String left, String right) {
        try {
            double l = Double.parseDouble(left);
            double r = Double.parseDouble(right);
            return switch (this) {
                case GREATER_THAN -> l > r;
                case GREATER_THAN_OR_EQUALS -> l >= r;
                case LESS_THAN -> l < r;
                case LESS_THAN_OR_EQUALS -> l <= r;
                default -> false;
            };
        } catch (NumberFormatException e) {
            int cmp = left.compareToIgnoreCase(right);
            return switch (this) {
                case GREATER_THAN -> cmp > 0;
                case GREATER_THAN_OR_EQUALS -> cmp >= 0;
                case LESS_THAN -> cmp < 0;
                case LESS_THAN_OR_EQUALS -> cmp <= 0;
                default -> false;
            };
        }
    }
}


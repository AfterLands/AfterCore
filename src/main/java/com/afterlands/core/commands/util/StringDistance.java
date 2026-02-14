package com.afterlands.core.commands.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;

/**
 * String distance utilities for fuzzy command matching.
 *
 * <p>
 * Uses Levenshtein distance to find the closest match
 * from a set of candidates. Used by the command dispatcher
 * to suggest corrections when a user types an invalid subcommand.
 * </p>
 */
public final class StringDistance {

    private StringDistance() {
    }

    /**
     * Computes the Levenshtein distance between two strings.
     *
     * @param a First string
     * @param b Second string
     * @return The edit distance (number of insertions, deletions, or substitutions)
     */
    public static int levenshtein(@NotNull String a, @NotNull String b) {
        int lenA = a.length();
        int lenB = b.length();

        if (lenA == 0)
            return lenB;
        if (lenB == 0)
            return lenA;

        // Use single-row optimization (O(min(m,n)) space)
        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[lenB];
    }

    /**
     * Finds the closest match to the input from a collection of candidates.
     *
     * @param input       The user's input (case-insensitive)
     * @param candidates  Available valid options
     * @param maxDistance Maximum edit distance to consider a match
     * @return The closest candidate, or null if none within maxDistance
     */
    @Nullable
    public static String findClosest(@NotNull String input, @NotNull Collection<String> candidates, int maxDistance) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        String bestMatch = null;
        int bestDistance = maxDistance + 1;

        for (String candidate : candidates) {
            int distance = levenshtein(lowerInput, candidate.toLowerCase(Locale.ROOT));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = candidate;
            }
        }

        return bestMatch;
    }
}

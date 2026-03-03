package com.afterlands.core.config.update;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Opções de merge para o ConfigUpdater.
 *
 * <p>
 * Define seções "user-owned" cujos filhos são controlados pelo usuário
 * e NÃO devem ser mergeados do default.
 * </p>
 *
 * <p>Patterns suportados:</p>
 * <ul>
 *   <li>{@code "editor.items"} — match exato</li>
 *   <li>{@code "*.items"} — qualquer root + ".items" (1 nível)</li>
 *   <li>{@code "**.items"} — qualquer profundidade terminando em ".items"</li>
 * </ul>
 */
public final class MergeOptions {

    private static final MergeOptions NONE = new MergeOptions(Collections.emptySet());

    private final Set<String> userOwnedSections;

    private MergeOptions(Set<String> userOwnedSections) {
        this.userOwnedSections = userOwnedSections;
    }

    /**
     * Verifica se um path YAML é user-owned (não deve ser mergeado do default).
     *
     * @param path O path completo (ex: "editor.items", "main-menu.items.23")
     * @return true se o path ou algum ancestral é user-owned
     */
    public boolean isUserOwned(@NotNull String path) {
        for (String pattern : userOwnedSections) {
            if (matchesPattern(path, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String path, String pattern) {
        if (pattern.startsWith("**.")) {
            // "**.items" — qualquer profundidade terminando no sufixo
            String suffix = pattern.substring(3);
            return path.equals(suffix) || path.endsWith("." + suffix);
        } else if (pattern.startsWith("*.")) {
            // "*.items" — exatamente 1 nível + sufixo
            String suffix = pattern.substring(2);
            int dotIndex = path.indexOf('.');
            if (dotIndex < 0) return false;
            String rest = path.substring(dotIndex + 1);
            // rest deve ser exatamente o sufixo (sem mais dots que indiquem nível extra)
            return rest.equals(suffix);
        } else {
            // Match exato ou path é filho da seção user-owned
            return path.equals(pattern) || path.startsWith(pattern + ".");
        }
    }

    /**
     * @return MergeOptions sem exclusões (comportamento default)
     */
    public static MergeOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Set<String> patterns = new HashSet<>();

        private Builder() {}

        public Builder userOwned(String... patterns) {
            Collections.addAll(this.patterns, patterns);
            return this;
        }

        public MergeOptions build() {
            return new MergeOptions(Collections.unmodifiableSet(new HashSet<>(patterns)));
        }
    }
}

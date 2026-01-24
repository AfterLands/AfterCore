package com.afterlands.core.config;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * API padronizada de mensagens.
 */
public interface MessageService {

    void send(@NotNull CommandSender sender, @NotNull String path);

    /**
     * Envia mensagem com placeholders.
     * 
     * @param replacements Pares de key/value (ex: "{player}", "Steve")
     */
    void send(@NotNull CommandSender sender, @NotNull String path, @NotNull String... replacements);

    void sendRaw(@NotNull CommandSender sender, @NotNull String raw);

    /**
     * Obtém a mensagem bruta do arquivo de configuração.
     * 
     * @param path Caminho no yaml (ex: "game.error")
     * @return A mensagem encontrada ou string vazia se não achar.
     */
    @NotNull
    String get(@NotNull String path);

    /**
     * Obtém mensagem com placeholders.
     */
    @NotNull
    String get(@NotNull String path, @NotNull String... replacements);

    /**
     * Obtém uma lista de mensagens do arquivo de configuração.
     * 
     * /**
     * Obtém uma lista de mensagens do arquivo de configuração.
     * 
     * @param path Caminho no yaml
     * @return Lista de mensagens ou lista vazia se não achar.
     */
    @NotNull
    java.util.List<String> getList(@NotNull String path);

    @NotNull
    String format(@NotNull String raw);
}

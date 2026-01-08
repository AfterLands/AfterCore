package com.afterlands.core.diagnostics;

import org.jetbrains.annotations.NotNull;

/**
 * Serviço de diagnóstico e health check do AfterCore.
 *
 * <p>Coleta informações sobre:
 * <ul>
 *   <li>Dependências detectadas (ProtocolLib, PlaceholderAPI)</li>
 *   <li>Estado do database (enabled, ping, pool stats)</li>
 *   <li>Thread pools (tamanho, uso)</li>
 *   <li>Sistema (JVM, OS, memória)</li>
 * </ul>
 * </p>
 */
public interface DiagnosticsService {

    /**
     * Captura um snapshot do estado atual do sistema.
     *
     * <p>Operação rápida e thread-safe. Pode ser chamada da main thread.</p>
     *
     * @return snapshot com informações de diagnóstico
     */
    @NotNull DiagnosticsSnapshot captureSnapshot();

    /**
     * Testa conexão com database e retorna latência em ms.
     *
     * <p>ATENÇÃO: operação bloqueante. Deve ser executada em thread async.</p>
     *
     * @return latência em ms, ou -1 se database desabilitado/erro
     */
    long pingDatabase();
}

package com.afterlands.core.result;

/**
 * Códigos de erro padronizados do AfterCore.
 *
 * <p>Permite que plugins consumidores identifiquem categorias de erro
 * sem depender de stacktraces ou mensagens de erro em texto.</p>
 */
public enum CoreErrorCode {
    /**
     * Dependência externa necessária não está presente (ProtocolLib, PlaceholderAPI, etc.)
     */
    DEPENDENCY_MISSING("dependency_missing"),

    /**
     * Database está desabilitado na configuração.
     */
    DB_DISABLED("db_disabled"),

    /**
     * Database habilitado mas não disponível (falha de conexão, pool não inicializado, etc.)
     */
    DB_UNAVAILABLE("db_unavailable"),

    /**
     * Operação excedeu tempo limite.
     */
    TIMEOUT("timeout"),

    /**
     * Configuração inválida (campo obrigatório ausente, valor fora do intervalo, etc.)
     */
    INVALID_CONFIG("invalid_config"),

    /**
     * Operação requer estar na main thread mas foi chamada de outra thread.
     */
    NOT_ON_MAIN_THREAD("not_on_main_thread"),

    /**
     * Operação requer estar FORA da main thread mas foi chamada da main thread.
     */
    ON_MAIN_THREAD("on_main_thread"),

    /**
     * Recurso não encontrado (player offline, mundo inexistente, etc.)
     */
    NOT_FOUND("not_found"),

    /**
     * Operação não permitida (sem permissão, estado inválido, etc.)
     */
    FORBIDDEN("forbidden"),

    /**
     * Argumento inválido fornecido.
     */
    INVALID_ARGUMENT("invalid_argument"),

    /**
     * Estado interno inconsistente (bug/corrupção).
     */
    INTERNAL_ERROR("internal_error"),

    /**
     * Erro não categorizado.
     */
    UNKNOWN("unknown");

    private final String code;

    CoreErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}

package com.pfc.thindesk.util;

import java.util.List;

public class StatusChamado {

    public static final String ABERTO = "Aberto";
    public static final String EM_ANDAMENTO = "Em andamento";
    public static final String CONCLUIDO = "Concluído";
    public static final String CANCELADO = "Cancelado";

    public static final List<String> TODOS = List.of(ABERTO, EM_ANDAMENTO, CONCLUIDO, CANCELADO);

    public static String normalizar(String status) {
        if (status == null || status.isBlank()) {
            return ABERTO;
        }
        String texto = status.trim().toUpperCase().replace("_", " ");
        if (texto.contains("ANDAMENTO")) {
            return EM_ANDAMENTO;
        }
        if (texto.startsWith("CONCLU") || texto.startsWith("FECHAD") || texto.startsWith("RESOLVID") || texto.startsWith("FINALIZ")) {
            return CONCLUIDO;
        }
        if (texto.startsWith("CANCEL")) {
            return CANCELADO;
        }
        return ABERTO;
    }
}

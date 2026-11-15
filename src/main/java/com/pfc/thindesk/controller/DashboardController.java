package com.pfc.thindesk.controller;

import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/indicadores")
    public Map<String, Object> indicadores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String tecnico) {
        return dashboardService.indicadores(buscar(inicio, fim, status, tipo, tecnico));
    }

    @GetMapping("/por-periodo")
    public List<Map<String, Object>> porPeriodo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String tecnico,
            @RequestParam(defaultValue = "dia") String agrupamento) {
        return dashboardService.porPeriodo(buscar(inicio, fim, status, tipo, tecnico), agrupamento);
    }

    @GetMapping("/por-status")
    public List<Map<String, Object>> porStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String tecnico) {
        return dashboardService.porStatus(buscar(inicio, fim, null, tipo, tecnico));
    }

    @GetMapping("/por-tipo")
    public List<Map<String, Object>> porTipo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tecnico,
            @RequestParam(defaultValue = "10") int limite) {
        validarLimite(limite);
        return dashboardService.rankingTipos(buscar(inicio, fim, status, null, tecnico), limite);
    }

    @GetMapping("/por-tecnico")
    public List<Map<String, Object>> porTecnico(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo) {
        return dashboardService.rankingTecnicos(buscar(inicio, fim, status, tipo, null));
    }

    @GetMapping("/recentes")
    public List<Map<String, Object>> recentes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String tecnico,
            @RequestParam(defaultValue = "10") int limite) {
        validarLimite(limite);
        return dashboardService.recentes(buscar(inicio, fim, status, tipo, tecnico), limite);
    }

    @GetMapping("/filtros")
    public Map<String, Object> filtros() {
        return dashboardService.opcoesFiltro();
    }

    @PostMapping("/dados-exemplo")
    public Map<String, Object> dadosExemplo(@RequestParam(defaultValue = "60") int quantidade) {
        if (quantidade < 1 || quantidade > 500) {
            throw new IllegalArgumentException("A quantidade deve ficar entre 1 e 500.");
        }
        int criados = dashboardService.gerarDadosExemplo(quantidade);
        return Map.of("mensagem", criados + " chamados de exemplo criados.");
    }

    private void validarLimite(int limite) {
        if (limite < 1) {
            throw new IllegalArgumentException("O limite deve ser maior que zero.");
        }
    }

    private List<Chamado> buscar(LocalDate inicio, LocalDate fim, String status, String tipo, String tecnico) {
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial não pode ser maior que a data final.");
        }
        return dashboardService.filtrar(inicio, fim, status, tipo, tecnico);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> erroDeValidacao(IllegalArgumentException erro) {
        return ResponseEntity.badRequest().body(Map.of("erro", erro.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> erroDeFormato(MethodArgumentTypeMismatchException erro) {
        return ResponseEntity.badRequest().body(Map.of("erro", "Valor inválido no parâmetro: " + erro.getName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> erroGeral(Exception erro) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", "Erro ao carregar os dados do dashboard."));
    }
}

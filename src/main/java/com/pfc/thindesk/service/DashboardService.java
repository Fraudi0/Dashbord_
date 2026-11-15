package com.pfc.thindesk.service;

import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.repository.ChamadoRepository;
import com.pfc.thindesk.util.StatusChamado;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final String NAO_INFORMADO = "Não informado";
    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired
    private ChamadoRepository chamadoRepository;

    public List<Chamado> filtrar(LocalDate inicio, LocalDate fim, String status, String tipo, String tecnico) {
        List<Chamado> resultado = new ArrayList<>();
        for (Chamado chamado : chamadoRepository.findAll()) {
            if (passaNoFiltro(chamado, inicio, fim, status, tipo, tecnico)) {
                resultado.add(chamado);
            }
        }
        return resultado;
    }

    private boolean passaNoFiltro(Chamado chamado, LocalDate inicio, LocalDate fim, String status, String tipo, String tecnico) {
        LocalDateTime abertura = chamado.getDataAbertura();
        if (inicio != null && (abertura == null || abertura.toLocalDate().isBefore(inicio))) {
            return false;
        }
        if (fim != null && (abertura == null || abertura.toLocalDate().isAfter(fim))) {
            return false;
        }
        if (temValor(status) && !StatusChamado.normalizar(chamado.getStatus()).equals(StatusChamado.normalizar(status))) {
            return false;
        }
        if (temValor(tipo) && !textoOuPadrao(chamado.getTipo()).equalsIgnoreCase(tipo.trim())) {
            return false;
        }
        if (temValor(tecnico) && !textoOuPadrao(chamado.getTecnico()).equalsIgnoreCase(tecnico.trim())) {
            return false;
        }
        return true;
    }

    public Map<String, Object> indicadores(List<Chamado> chamados) {
        int abertos = 0;
        int emAndamento = 0;
        int concluidos = 0;
        int cancelados = 0;
        double somaHoras = 0;
        int quantidadeComTempo = 0;

        for (Chamado chamado : chamados) {
            String status = StatusChamado.normalizar(chamado.getStatus());
            if (status.equals(StatusChamado.ABERTO)) {
                abertos++;
            } else if (status.equals(StatusChamado.EM_ANDAMENTO)) {
                emAndamento++;
            } else if (status.equals(StatusChamado.CONCLUIDO)) {
                concluidos++;
                if (chamado.getDataAbertura() != null && chamado.getDataFechamento() != null) {
                    somaHoras += Duration.between(chamado.getDataAbertura(), chamado.getDataFechamento()).toMinutes() / 60.0;
                    quantidadeComTempo++;
                }
            } else {
                cancelados++;
            }
        }

        int total = chamados.size();
        double taxaConclusao = total == 0 ? 0 : (concluidos * 100.0) / total;
        double tempoMedio = quantidadeComTempo == 0 ? 0 : somaHoras / quantidadeComTempo;

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("total", total);
        resposta.put("abertos", abertos);
        resposta.put("emAndamento", emAndamento);
        resposta.put("concluidos", concluidos);
        resposta.put("cancelados", cancelados);
        resposta.put("pendentes", abertos + emAndamento);
        resposta.put("taxaConclusao", arredondar(taxaConclusao));
        resposta.put("tempoMedioHoras", arredondar(tempoMedio));
        resposta.put("atualizadoEm", LocalDateTime.now().format(FORMATO_DATA_HORA));
        return resposta;
    }

    public List<Map<String, Object>> porPeriodo(List<Chamado> chamados, String agrupamento) {
        boolean porMes = "mes".equalsIgnoreCase(agrupamento);
        TreeMap<String, int[]> grupos = new TreeMap<>();

        for (Chamado chamado : chamados) {
            if (chamado.getDataAbertura() != null) {
                String chave = chavePeriodo(chamado.getDataAbertura().toLocalDate(), porMes);
                grupos.computeIfAbsent(chave, c -> new int[2])[0]++;
            }
            boolean concluido = StatusChamado.normalizar(chamado.getStatus()).equals(StatusChamado.CONCLUIDO);
            if (concluido && chamado.getDataFechamento() != null) {
                String chave = chavePeriodo(chamado.getDataFechamento().toLocalDate(), porMes);
                grupos.computeIfAbsent(chave, c -> new int[2])[1]++;
            }
        }

        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Map.Entry<String, int[]> grupo : grupos.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("periodo", rotuloPeriodo(grupo.getKey(), porMes));
            item.put("abertos", grupo.getValue()[0]);
            item.put("concluidos", grupo.getValue()[1]);
            resposta.add(item);
        }
        return resposta;
    }

    private String chavePeriodo(LocalDate data, boolean porMes) {
        if (porMes) {
            return data.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return data.toString();
    }

    private String rotuloPeriodo(String chave, boolean porMes) {
        if (porMes) {
            String[] partes = chave.split("-");
            return partes[1] + "/" + partes[0];
        }
        return LocalDate.parse(chave).format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    public List<Map<String, Object>> porStatus(List<Chamado> chamados) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (String status : StatusChamado.TODOS) {
            contagem.put(status, 0);
        }
        for (Chamado chamado : chamados) {
            String status = StatusChamado.normalizar(chamado.getStatus());
            contagem.put(status, contagem.get(status) + 1);
        }

        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Map.Entry<String, Integer> item : contagem.entrySet()) {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("status", item.getKey());
            linha.put("quantidade", item.getValue());
            linha.put("percentual", percentual(item.getValue(), chamados.size()));
            resposta.add(linha);
        }
        return resposta;
    }

    public List<Map<String, Object>> rankingTipos(List<Chamado> chamados, int limite) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (Chamado chamado : chamados) {
            contagem.merge(textoOuPadrao(chamado.getTipo()), 1, Integer::sum);
        }

        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Map.Entry<String, Integer> item : contagem.entrySet()) {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("tipo", item.getKey());
            linha.put("quantidade", item.getValue());
            linha.put("percentual", percentual(item.getValue(), chamados.size()));
            resposta.add(linha);
        }
        resposta.sort(Comparator.comparing((Map<String, Object> linha) -> (Integer) linha.get("quantidade")).reversed());
        return resposta.subList(0, Math.min(limite, resposta.size()));
    }

    public List<Map<String, Object>> rankingTecnicos(List<Chamado> chamados) {
        Map<String, int[]> contagem = new LinkedHashMap<>();
        for (Chamado chamado : chamados) {
            int[] valores = contagem.computeIfAbsent(textoOuPadrao(chamado.getTecnico()), t -> new int[3]);
            valores[0]++;
            String status = StatusChamado.normalizar(chamado.getStatus());
            if (status.equals(StatusChamado.CONCLUIDO)) {
                valores[1]++;
            } else if (status.equals(StatusChamado.ABERTO) || status.equals(StatusChamado.EM_ANDAMENTO)) {
                valores[2]++;
            }
        }

        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Map.Entry<String, int[]> item : contagem.entrySet()) {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("tecnico", item.getKey());
            linha.put("total", item.getValue()[0]);
            linha.put("concluidos", item.getValue()[1]);
            linha.put("pendentes", item.getValue()[2]);
            resposta.add(linha);
        }
        resposta.sort(Comparator.comparing((Map<String, Object> linha) -> (Integer) linha.get("concluidos")).reversed());
        return resposta;
    }

    public List<Map<String, Object>> recentes(List<Chamado> chamados, int limite) {
        List<Chamado> ordenados = new ArrayList<>(chamados);
        ordenados.sort(Comparator.comparing(Chamado::getDataAbertura, Comparator.nullsLast(Comparator.reverseOrder())));

        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Chamado chamado : ordenados.subList(0, Math.min(limite, ordenados.size()))) {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("id", chamado.getId());
            linha.put("descricao", textoOuPadrao(chamado.getDescricao()));
            linha.put("status", StatusChamado.normalizar(chamado.getStatus()));
            linha.put("tipo", textoOuPadrao(chamado.getTipo()));
            linha.put("tecnico", textoOuPadrao(chamado.getTecnico()));
            linha.put("usuario", textoOuPadrao(chamado.getUsuario()));
            linha.put("prioridade", textoOuPadrao(chamado.getPrioridade()));
            linha.put("consideracoesTecnicas", temValor(chamado.getConsideracoesTecnicas()) ? chamado.getConsideracoesTecnicas() : "Sem considerações");
            linha.put("dataAbertura", formatarData(chamado.getDataAbertura()));
            linha.put("dataFechamento", formatarData(chamado.getDataFechamento()));
            resposta.add(linha);
        }
        return resposta;
    }

    public Map<String, Object> opcoesFiltro() {
        TreeSet<String> tipos = new TreeSet<>();
        TreeSet<String> tecnicos = new TreeSet<>();
        for (Chamado chamado : chamadoRepository.findAll()) {
            tipos.add(textoOuPadrao(chamado.getTipo()));
            tecnicos.add(textoOuPadrao(chamado.getTecnico()));
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("status", StatusChamado.TODOS);
        resposta.put("tipos", tipos);
        resposta.put("tecnicos", tecnicos);
        return resposta;
    }

    public int gerarDadosExemplo(int quantidade) {
        String[] tipos = {"Hardware", "Software", "Rede", "Impressora", "Acesso e senha", "E-mail"};
        String[] tecnicos = {"João", "Maria", "Carlos", "Ana"};
        String[] usuarios = {"Alberto", "Beatriz", "Lucas", "Fernanda", "Rafael", "Juliana", "Pedro"};
        String[] prioridades = {"Baixa", "Média", "Alta"};
        String[] descricoes = {
            "Computador não liga", "Erro ao abrir o sistema", "Sem acesso à internet",
            "Impressora travando papel", "Esqueci minha senha", "E-mail não sincroniza"
        };
        String[] consideracoes = {
            "Fonte de energia substituída.", "Reinstalação do programa feita.",
            "Roteador reiniciado e testado.", "Rolo de papel limpo e ajustado.",
            "Senha redefinida com o usuário.", "Configuração da conta refeita."
        };

        Random random = new Random();
        List<Chamado> novos = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            Chamado chamado = new Chamado();
            int indiceTipo = random.nextInt(tipos.length);
            chamado.setTipo(tipos[indiceTipo]);
            chamado.setDescricao(descricoes[indiceTipo]);
            chamado.setTecnico(tecnicos[random.nextInt(tecnicos.length)]);
            chamado.setUsuario(usuarios[random.nextInt(usuarios.length)]);
            chamado.setPrioridade(prioridades[random.nextInt(prioridades.length)]);

            LocalDateTime abertura = LocalDateTime.now().minusDays(random.nextInt(90)).minusHours(random.nextInt(10));
            chamado.setDataAbertura(abertura);

            int sorteio = random.nextInt(10);
            if (sorteio < 6) {
                chamado.setStatus(StatusChamado.CONCLUIDO);
                LocalDateTime fechamento = abertura.plusHours(1 + random.nextInt(72));
                chamado.setDataFechamento(fechamento.isAfter(LocalDateTime.now()) ? LocalDateTime.now() : fechamento);
                chamado.setConsideracoesTecnicas(consideracoes[indiceTipo]);
            } else if (sorteio < 8) {
                chamado.setStatus(StatusChamado.EM_ANDAMENTO);
                chamado.setConsideracoesTecnicas("Em análise pelo técnico.");
            } else if (sorteio < 9) {
                chamado.setStatus(StatusChamado.ABERTO);
            } else {
                chamado.setStatus(StatusChamado.CANCELADO);
                chamado.setDataFechamento(abertura.plusHours(2));
                chamado.setConsideracoesTecnicas("Cancelado a pedido do usuário.");
            }
            novos.add(chamado);
        }
        chamadoRepository.saveAll(novos);
        return novos.size();
    }

    private boolean temValor(String texto) {
        return texto != null && !texto.isBlank();
    }

    private String textoOuPadrao(String texto) {
        return temValor(texto) ? texto.trim() : NAO_INFORMADO;
    }

    private String formatarData(LocalDateTime data) {
        return data == null ? "-" : data.format(FORMATO_DATA_HORA);
    }

    private double percentual(int valor, int total) {
        return total == 0 ? 0 : arredondar((valor * 100.0) / total);
    }

    private double arredondar(double valor) {
        return Math.round(valor * 10) / 10.0;
    }
}

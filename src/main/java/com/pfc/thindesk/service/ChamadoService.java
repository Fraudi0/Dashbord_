package com.pfc.thindesk.service;

import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.repository.ChamadoRepository;
import com.pfc.thindesk.util.StatusChamado;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChamadoService {

    @Autowired
    private ChamadoRepository chamadoRepository;

    public Chamado criarChamado(Chamado chamado) {
        if (chamado.getDataAbertura() == null) {
            chamado.setDataAbertura(LocalDateTime.now());
        }
        ajustarStatus(chamado);
        return chamadoRepository.save(chamado);
    }

    public List<Chamado> listarChamados() {
        return chamadoRepository.findAll();
    }

    public Chamado atualizarChamado(String id, Chamado chamadoAtualizado) {
        return chamadoRepository.findById(id)
                .map(chamado -> {
                    chamado.setDescricao(chamadoAtualizado.getDescricao());
                    chamado.setStatus(chamadoAtualizado.getStatus());
                    chamado.setTipo(chamadoAtualizado.getTipo());
                    chamado.setTecnico(chamadoAtualizado.getTecnico());
                    chamado.setUsuario(chamadoAtualizado.getUsuario());
                    if (chamadoAtualizado.getPrioridade() != null) {
                        chamado.setPrioridade(chamadoAtualizado.getPrioridade());
                    }
                    if (chamadoAtualizado.getConsideracoesTecnicas() != null) {
                        chamado.setConsideracoesTecnicas(chamadoAtualizado.getConsideracoesTecnicas());
                    }
                    if (chamado.getDataAbertura() == null) {
                        chamado.setDataAbertura(LocalDateTime.now());
                    }
                    ajustarStatus(chamado);
                    return chamadoRepository.save(chamado);
                })
                .orElseThrow(() -> new RuntimeException("Chamado não encontrado com id: " + id));
    }

    private void ajustarStatus(Chamado chamado) {
        String status = StatusChamado.normalizar(chamado.getStatus());
        chamado.setStatus(status);
        boolean finalizado = status.equals(StatusChamado.CONCLUIDO) || status.equals(StatusChamado.CANCELADO);
        if (finalizado && chamado.getDataFechamento() == null) {
            chamado.setDataFechamento(LocalDateTime.now());
        }
        if (!finalizado) {
            chamado.setDataFechamento(null);
        }
    }
}

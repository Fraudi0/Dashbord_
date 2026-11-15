const CORES = {
    verde: '#16a34a',
    verdeClaro: '#86efac',
    vermelho: '#dc2626',
    vermelhoClaro: '#f87171',
    cinza: '#9ca3af'
};

const CORES_STATUS = {
    'Aberto': CORES.vermelho,
    'Em andamento': CORES.vermelhoClaro,
    'Concluído': CORES.verde,
    'Cancelado': CORES.cinza
};

let graficoPeriodo = null;
let graficoStatus = null;
let graficoTipos = null;
let temporizador = null;

function montarParametros(extras) {
    const parametros = new URLSearchParams();
    const campos = {
        inicio: document.getElementById('filtroInicio').value,
        fim: document.getElementById('filtroFim').value,
        status: document.getElementById('filtroStatus').value,
        tipo: document.getElementById('filtroTipo').value,
        tecnico: document.getElementById('filtroTecnico').value
    };
    Object.assign(campos, extras);
    for (const chave in campos) {
        if (campos[chave]) {
            parametros.append(chave, campos[chave]);
        }
    }
    return parametros.toString();
}

async function buscarDados(endereco, opcoes) {
    const resposta = await fetch(endereco, opcoes);
    const tipoConteudo = resposta.headers.get('content-type') || '';
    if (!tipoConteudo.includes('application/json')) {
        throw new Error('Sua sessão expirou. Faça login novamente.');
    }
    const dados = await resposta.json();
    if (!resposta.ok) {
        throw new Error(dados.erro || 'Erro ao buscar dados.');
    }
    return dados;
}

function mostrarErro(mensagem) {
    const caixa = document.getElementById('mensagemErro');
    caixa.textContent = mensagem;
    caixa.classList.remove('d-none');
}

function esconderErro() {
    document.getElementById('mensagemErro').classList.add('d-none');
}

function protegerTexto(texto) {
    const div = document.createElement('div');
    div.textContent = texto;
    return div.innerHTML;
}

function preencherSelect(id, opcoes) {
    const select = document.getElementById(id);
    const valorAtual = select.value;
    select.innerHTML = '<option value="">Todos</option>';
    opcoes.forEach(opcao => {
        const item = document.createElement('option');
        item.value = opcao;
        item.textContent = opcao;
        select.appendChild(item);
    });
    select.value = valorAtual;
}

async function carregarFiltros() {
    const filtros = await buscarDados('/api/dashboard/filtros');
    preencherSelect('filtroStatus', filtros.status);
    preencherSelect('filtroTipo', filtros.tipos);
    preencherSelect('filtroTecnico', filtros.tecnicos);
}

function mostrarIndicadores(dados) {
    document.getElementById('indTotal').textContent = dados.total;
    document.getElementById('indAbertos').textContent = dados.abertos;
    document.getElementById('indAndamento').textContent = dados.emAndamento;
    document.getElementById('indConcluidos').textContent = dados.concluidos;
    document.getElementById('indCancelados').textContent = dados.cancelados;
    document.getElementById('indTaxa').textContent = dados.taxaConclusao.toLocaleString('pt-BR') + '%';
    document.getElementById('indTempo').textContent = dados.tempoMedioHoras.toLocaleString('pt-BR') + ' h';
    document.getElementById('ultimaAtualizacao').textContent = dados.atualizadoEm;
    document.getElementById('avisoSemDados').classList.toggle('d-none', dados.total > 0);
}

function mostrarGraficoPeriodo(dados) {
    if (graficoPeriodo) {
        graficoPeriodo.destroy();
    }
    graficoPeriodo = new Chart(document.getElementById('graficoPeriodo'), {
        type: 'bar',
        data: {
            labels: dados.map(item => item.periodo),
            datasets: [
                {
                    label: 'Abertos',
                    data: dados.map(item => item.abertos),
                    backgroundColor: CORES.vermelho
                },
                {
                    label: 'Concluídos',
                    data: dados.map(item => item.concluidos),
                    backgroundColor: CORES.verde
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: { beginAtZero: true, ticks: { precision: 0 } }
            }
        }
    });
}

function mostrarGraficoStatus(dados) {
    if (graficoStatus) {
        graficoStatus.destroy();
    }
    graficoStatus = new Chart(document.getElementById('graficoStatus'), {
        type: 'doughnut',
        data: {
            labels: dados.map(item => item.status + ' (' + item.percentual + '%)'),
            datasets: [{
                data: dados.map(item => item.quantidade),
                backgroundColor: dados.map(item => CORES_STATUS[item.status]),
                borderColor: '#ffffff',
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'bottom' }
            }
        }
    });
}

function mostrarGraficoTipos(dados) {
    if (graficoTipos) {
        graficoTipos.destroy();
    }
    graficoTipos = new Chart(document.getElementById('graficoTipos'), {
        type: 'bar',
        data: {
            labels: dados.map(item => item.tipo),
            datasets: [{
                label: 'Chamados',
                data: dados.map(item => item.quantidade),
                backgroundColor: CORES.verde
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                x: { beginAtZero: true, ticks: { precision: 0 } }
            }
        }
    });
}

function mostrarTecnicos(dados) {
    const tabela = document.getElementById('tabelaTecnicos');
    if (dados.length === 0) {
        tabela.innerHTML = '<tr><td colspan="4" class="text-center text-muted">Sem dados</td></tr>';
        return;
    }
    tabela.innerHTML = dados.map(item => `
        <tr>
            <td>${protegerTexto(item.tecnico)}</td>
            <td class="text-center">${item.total}</td>
            <td class="text-center texto-verde fw-semibold">${item.concluidos}</td>
            <td class="text-center texto-vermelho fw-semibold">${item.pendentes}</td>
        </tr>
    `).join('');
}

function classeStatus(status) {
    if (status === 'Aberto') return 'etiqueta-aberto';
    if (status === 'Em andamento') return 'etiqueta-andamento';
    if (status === 'Concluído') return 'etiqueta-concluido';
    return 'etiqueta-cancelado';
}

function mostrarRecentes(dados) {
    const tabela = document.getElementById('tabelaRecentes');
    if (dados.length === 0) {
        tabela.innerHTML = '<tr><td colspan="7" class="text-center text-muted">Sem dados</td></tr>';
        return;
    }
    tabela.innerHTML = dados.map(item => `
        <tr>
            <td class="text-nowrap">${protegerTexto(item.dataAbertura)}</td>
            <td>${protegerTexto(item.descricao)}</td>
            <td>${protegerTexto(item.tipo)}</td>
            <td>${protegerTexto(item.tecnico)}</td>
            <td>${protegerTexto(item.prioridade)}</td>
            <td><span class="etiqueta ${classeStatus(item.status)}">${protegerTexto(item.status)}</span></td>
            <td class="texto-consideracao">${protegerTexto(item.consideracoesTecnicas)}</td>
        </tr>
    `).join('');
}

async function carregarDashboard() {
    const botao = document.getElementById('botaoAtualizar');
    botao.disabled = true;
    esconderErro();
    try {
        const filtros = montarParametros();
        const agrupamento = document.getElementById('filtroAgrupamento').value;
        const [indicadores, periodo, status, tipos, tecnicos, recentes] = await Promise.all([
            buscarDados('/api/dashboard/indicadores?' + filtros),
            buscarDados('/api/dashboard/por-periodo?' + montarParametros({ agrupamento: agrupamento })),
            buscarDados('/api/dashboard/por-status?' + filtros),
            buscarDados('/api/dashboard/por-tipo?' + filtros),
            buscarDados('/api/dashboard/por-tecnico?' + filtros),
            buscarDados('/api/dashboard/recentes?' + filtros)
        ]);
        mostrarIndicadores(indicadores);
        mostrarGraficoPeriodo(periodo);
        mostrarGraficoStatus(status);
        mostrarGraficoTipos(tipos);
        mostrarTecnicos(tecnicos);
        mostrarRecentes(recentes);
    } catch (erro) {
        mostrarErro(erro.message);
    } finally {
        botao.disabled = false;
    }
}

function controlarAtualizacaoAutomatica() {
    clearInterval(temporizador);
    if (document.getElementById('atualizacaoAutomatica').checked) {
        temporizador = setInterval(carregarDashboard, 60000);
    }
}

async function gerarExemplos() {
    const botao = document.getElementById('botaoExemplo');
    botao.disabled = true;
    try {
        await buscarDados('/api/dashboard/dados-exemplo?quantidade=60', { method: 'POST' });
        await carregarFiltros();
        await carregarDashboard();
    } catch (erro) {
        mostrarErro(erro.message);
    } finally {
        botao.disabled = false;
    }
}

document.getElementById('formFiltros').addEventListener('submit', evento => {
    evento.preventDefault();
    carregarDashboard();
});

document.getElementById('botaoLimpar').addEventListener('click', () => {
    document.getElementById('formFiltros').reset();
    carregarDashboard();
});

document.getElementById('botaoAtualizar').addEventListener('click', carregarDashboard);
document.getElementById('atualizacaoAutomatica').addEventListener('change', controlarAtualizacaoAutomatica);
document.getElementById('botaoExemplo').addEventListener('click', gerarExemplos);

async function iniciar() {
    try {
        await carregarFiltros();
    } catch (erro) {
        mostrarErro(erro.message);
    }
    await carregarDashboard();
    controlarAtualizacaoAutomatica();
}

iniciar();

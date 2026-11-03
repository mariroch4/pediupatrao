(function () {
    'use strict';

    const API = '/api/dashboard';
    const INTERVALO_ATUALIZACAO_MS = 60000;

    const CORES = {
        amarelo: '#ffc107',
        escuro: '#23272f',
        grade: 'rgba(0, 0, 0, 0.06)'
    };
    const CORES_STATUS = {
        RECEBIDO: '#0dcaf0',
        EM_PREPARACAO: '#ffc107',
        PRONTO: '#0d6efd',
        SAIU_PARA_ENTREGA: '#6f42c1',
        RETIRADO: '#6c757d',
        FINALIZADO: '#198754',
        CANCELADO: '#dc3545'
    };
    const CORES_TIPO = ['#ffc107', '#23272f'];

    const moeda = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
    const inteiro = new Intl.NumberFormat('pt-BR');
    const decimal1 = new Intl.NumberFormat('pt-BR', {minimumFractionDigits: 1, maximumFractionDigits: 1});

    const estado = {
        agrupamento: 'DIA',
        ordenarPor: 'QUANTIDADE',
        carregando: false,
        timer: null,
        graficos: {}
    };

    const el = (id) => document.getElementById(id);
    const graficosDisponiveis = typeof window.Chart !== 'undefined';

    if (graficosDisponiveis) {
        Chart.defaults.font.family = "'Poppins', sans-serif";
        Chart.defaults.color = '#6c757d';
    }

    class ErroApi extends Error {
        constructor(status, mensagem) {
            super(mensagem);
            this.status = status;
        }
    }

    function lerFiltros() {
        const params = new URLSearchParams();
        const campos = {
            dataInicio: el('filtroDataInicio').value,
            dataFim: el('filtroDataFim').value,
            tipoEntrega: el('filtroTipoEntrega').value,
            status: el('filtroStatus').value,
            responsavel: el('filtroResponsavel').value.trim()
        };
        Object.entries(campos).forEach(([nome, valor]) => {
            if (valor) {
                params.set(nome, valor);
            }
        });
        return params;
    }

    async function buscar(caminho, extras) {
        const params = lerFiltros();
        if (extras) {
            Object.entries(extras).forEach(([nome, valor]) => params.set(nome, valor));
        }

        let resposta;
        try {
            resposta = await fetch(`${API}/${caminho}?${params}`, {headers: {'Accept': 'application/json'}});
        } catch (e) {
            throw new ErroApi(0, 'Não foi possível conectar ao servidor. Verifique se o sistema está no ar.');
        }

        if (resposta.status === 401) {
            throw new ErroApi(401, 'Sua sessão expirou. Faça login novamente.');
        }
        if (!resposta.ok) {
            let mensagem = `Erro ${resposta.status} ao consultar o dashboard.`;
            try {
                const corpo = await resposta.json();
                if (corpo && corpo.mensagem) {
                    mensagem = corpo.mensagem;
                }
            } catch (e) {
            }
            throw new ErroApi(resposta.status, mensagem);
        }

        const tipo = resposta.headers.get('Content-Type') || '';
        if (!tipo.includes('application/json')) {
            throw new ErroApi(401, 'Sua sessão expirou. Faça login novamente.');
        }
        return resposta.json();
    }

    async function carregarTudo() {
        if (estado.carregando || !periodoValido()) {
            return;
        }
        estado.carregando = true;
        mostrarCarregando(true);
        try {
            const [indicadores, vendas, horas, tipos, status, ranking, recentes] = await Promise.all([
                buscar('indicadores'),
                buscar('vendas', {agrupamento: estado.agrupamento}),
                buscar('vendas-por-hora'),
                buscar('vendas-por-tipo'),
                buscar('status'),
                buscar('produtos-mais-vendidos', {limite: 5, ordenarPor: estado.ordenarPor}),
                buscar('pedidos-recentes', {limite: 10})
            ]);

            esconderErro();
            renderizarIndicadores(indicadores.dados);
            renderizarVendas(vendas.dados);
            renderizarHoras(horas.dados);
            renderizarTipos(tipos.dados);
            renderizarStatus(status.dados);
            renderizarRanking(ranking.dados);
            renderizarRecentes(recentes.dados);
            atualizarCabecalho(indicadores);
            sincronizarUrl();
        } catch (erro) {
            mostrarErro(erro);
        } finally {
            estado.carregando = false;
            mostrarCarregando(false);
        }
    }

    async function carregarParte(acao) {
        if (!periodoValido()) {
            return;
        }
        mostrarCarregando(true);
        try {
            await acao();
            esconderErro();
        } catch (erro) {
            mostrarErro(erro);
        } finally {
            mostrarCarregando(false);
        }
    }

    function periodoValido() {
        const inicio = el('filtroDataInicio').value;
        const fim = el('filtroDataFim').value;
        if (inicio && fim && fim < inicio) {
            mostrarErro(new ErroApi(422, 'A data final não pode ser anterior à data inicial.'));
            return false;
        }
        return true;
    }

    function mostrarCarregando(ativo) {
        el('painelDashboard').classList.toggle('carregando', ativo);
        const botao = el('btnAtualizar');
        botao.disabled = ativo;
        botao.querySelector('i').classList.toggle('girando', ativo);
    }

    function mostrarErro(erro) {
        const status = erro instanceof ErroApi ? erro.status : -1;
        const mensagem = erro instanceof ErroApi ? erro.message : 'Ocorreu um erro inesperado ao montar o dashboard.';
        const filtroInvalido = status === 400 || status === 422;

        let titulo = 'Não foi possível atualizar o dashboard.';
        let complemento = 'Os dados exibidos são da última atualização bem-sucedida.';
        if (filtroInvalido) {
            titulo = 'Filtro inválido.';
            complemento = 'Corrija os filtros e tente novamente.';
        } else if (status === 401) {
            titulo = 'Sessão expirada.';
            complemento = '';
        } else if (status === 403) {
            titulo = 'Acesso negado.';
            complemento = '';
        }

        const alerta = el('alertaErro');
        alerta.className = 'alert ' + (filtroInvalido ? 'alert-warning' : 'alert-danger');
        alerta.replaceChildren();

        const icone = document.createElement('i');
        icone.className = 'bi bi-exclamation-triangle-fill me-2';
        const forte = document.createElement('strong');
        forte.textContent = titulo + ' ';
        const texto = document.createElement('span');
        texto.textContent = mensagem + (complemento ? ' ' + complemento : '');
        alerta.append(icone, forte, texto);

        if (status === 401) {
            const link = document.createElement('a');
            link.href = '/login';
            link.className = 'alert-link ms-1';
            link.textContent = 'Ir para o login';
            alerta.append(link);
        }
        if (status === 401 || status === 403) {
            el('chkAutoAtualizar').checked = false;
            configurarAutoAtualizacao();
        }
        console.error('[Dashboard]', erro);
    }

    function esconderErro() {
        el('alertaErro').className = 'alert d-none';
    }

    function atualizarCabecalho(resposta) {
        el('txtUltimaAtualizacao').textContent = dataHora(resposta.atualizadoEm);

        const f = resposta.filtros;
        const dias = Math.round((Date.parse(f.dataFim) - Date.parse(f.dataInicio)) / 86400000) + 1;
        const ateHoje = f.dataFim === isoLocal(new Date());
        el('periodoInicio').textContent = data(f.dataInicio);
        el('periodoFim').textContent = data(f.dataFim);
        el('periodoDias').textContent = (dias === 1 ? '1 dia' : `${inteiro.format(dias)} dias`)
                + (ateHoje ? ' · até hoje' : '');

        const anterior = resposta.dados && resposta.dados.periodoAnterior;
        el('periodoComparacao').textContent = anterior
                ? `Variações comparadas com ${data(anterior.dataInicio)} a ${data(anterior.dataFim)}`
                : '';

        const chips = el('periodoFiltros');
        chips.replaceChildren();
        const adicionarChip = (rotulo, valor, ativo, icone) => {
            const chip = document.createElement('span');
            chip.className = 'periodo-chip' + (ativo ? ' ativo' : '');
            const i = document.createElement('i');
            i.className = 'bi ' + icone;
            chip.append(i);
            if (rotulo) {
                const forte = document.createElement('strong');
                forte.textContent = rotulo + ':';
                chip.append(forte);
            }
            chip.append(document.createTextNode(valor));
            chips.append(chip);
        };
        if (f.tipoEntrega) {
            adicionarChip('Atendimento', textoDaOpcao('filtroTipoEntrega', f.tipoEntrega), true, 'bi-signpost-split');
        }
        if (f.status) {
            adicionarChip('Status', textoDaOpcao('filtroStatus', f.status), true, 'bi-diagram-3');
        }
        if (f.responsavel) {
            adicionarChip('Responsável', f.responsavel, true, 'bi-person');
        }
        if (chips.children.length === 0) {
            adicionarChip(null, 'Todos os pedidos', false, 'bi-funnel');
        }
    }

    function textoDaOpcao(idSelect, valor) {
        const opcao = Array.from(el(idSelect).options).find(o => o.value === valor);
        return opcao ? opcao.text : valor;
    }

    function renderizarIndicadores(d) {
        el('kpiFaturamento').textContent = moeda.format(d.faturamento);
        variacao(el('kpiFaturamentoVar'), d.periodoAnterior.variacaoFaturamento);

        el('kpiPedidos').textContent = inteiro.format(d.totalPedidos);
        variacao(el('kpiPedidosVar'), d.periodoAnterior.variacaoPedidos);

        el('kpiTicket').textContent = moeda.format(d.ticketMedio);
        variacao(el('kpiTicketVar'), d.periodoAnterior.variacaoTicketMedio);

        el('kpiItens').textContent = inteiro.format(d.itensVendidos);
        el('kpiItensSub').textContent = `em ${inteiro.format(d.pedidosValidos)} pedido(s) válido(s)`;

        const bruto = Number(d.faturamento) + Number(d.totalDescontos);
        const percDesconto = bruto > 0 ? Number(d.totalDescontos) / bruto * 100 : 0;
        el('kpiDescontos').textContent = moeda.format(d.totalDescontos);
        el('kpiDescontosSub').textContent =
                `${inteiro.format(d.pedidosComDesconto)} pedido(s) · ${decimal1.format(percDesconto)}% do valor bruto`;

        el('kpiCancelados').textContent = inteiro.format(d.pedidosCancelados);
        el('kpiCanceladosSub').textContent = `taxa de cancelamento: ${decimal1.format(d.taxaCancelamento)}%`;
        el('kpiCanceladosBarra').style.width = Math.min(100, Number(d.taxaCancelamento)) + '%';
    }

    function variacao(elemento, valor) {
        if (valor === null || valor === undefined) {
            elemento.className = 'kpi-variacao neutra';
            elemento.textContent = 'sem dados do período anterior';
            return;
        }
        const numero = Number(valor);
        const icone = numero > 0 ? 'bi-arrow-up-right' : (numero < 0 ? 'bi-arrow-down-right' : 'bi-dash');
        const classe = numero > 0 ? 'positiva' : (numero < 0 ? 'negativa' : 'neutra');
        elemento.className = 'kpi-variacao ' + classe;
        elemento.innerHTML = `<i class="bi ${icone}"></i> ${numero > 0 ? '+' : ''}${decimal1.format(numero)}% vs. período anterior`;
    }

    function renderizarVendas(serie) {
        desenhar('graficoVendas', () => ({
            type: 'bar',
            data: {
                labels: serie.map(p => p.rotulo),
                datasets: [
                    {
                        type: 'bar', label: 'Faturamento', yAxisID: 'y', order: 2,
                        data: serie.map(p => Number(p.faturamento)),
                        backgroundColor: CORES.amarelo, borderRadius: 4
                    },
                    {
                        type: 'line', label: 'Pedidos', yAxisID: 'y1', order: 1,
                        data: serie.map(p => p.pedidos),
                        borderColor: CORES.escuro, backgroundColor: CORES.escuro,
                        tension: 0.3, pointRadius: 2, borderWidth: 2
                    }
                ]
            },
            options: opcoesBase({
                scales: {
                    y: {beginAtZero: true, grid: {color: CORES.grade}, ticks: {callback: v => moedaCurta(v)}},
                    y1: {beginAtZero: true, position: 'right', grid: {drawOnChartArea: false}, ticks: {precision: 0}},
                    x: {grid: {display: false}}
                },
                plugins: {
                    tooltip: {
                        callbacks: {
                            label: ctx => ctx.dataset.yAxisID === 'y'
                                ? ` Faturamento: ${moeda.format(ctx.parsed.y)}`
                                : ` Pedidos: ${ctx.parsed.y}`
                        }
                    }
                }
            })
        }), serie.some(p => p.pedidos > 0));
    }

    function renderizarHoras(serie) {
        const pico = Math.max(0, ...serie.map(p => p.pedidos));
        desenhar('graficoHoras', () => ({
            type: 'bar',
            data: {
                labels: serie.map(p => p.rotulo),
                datasets: [{
                    label: 'Pedidos',
                    data: serie.map(p => p.pedidos),
                    backgroundColor: serie.map(p => (p.pedidos === pico && pico > 0) ? CORES.amarelo : CORES.escuro),
                    borderRadius: 4
                }]
            },
            options: opcoesBase({
                scales: {
                    y: {beginAtZero: true, grid: {color: CORES.grade}, ticks: {precision: 0}},
                    x: {grid: {display: false}}
                },
                plugins: {
                    legend: {display: false},
                    tooltip: {
                        callbacks: {
                            label: ctx => ` ${ctx.parsed.y} pedido(s) · ${moeda.format(serie[ctx.dataIndex].faturamento)}`
                        }
                    }
                }
            })
        }), serie.length > 0);
    }

    function renderizarTipos(serie) {
        const total = serie.reduce((soma, p) => soma + p.pedidos, 0);
        desenhar('graficoTipo', () => ({
            type: 'doughnut',
            data: {
                labels: serie.map(p => p.rotulo),
                datasets: [{
                    data: serie.map(p => p.pedidos),
                    backgroundColor: CORES_TIPO, borderColor: '#fff', borderWidth: 2
                }]
            },
            options: opcoesBase({
                cutout: '62%',
                interaction: {mode: 'nearest', intersect: true},
                plugins: {
                    tooltip: {
                        callbacks: {
                            label: ctx => ` ${ctx.label}: ${ctx.parsed} pedido(s) · ${moeda.format(serie[ctx.dataIndex].faturamento)}`
                        }
                    }
                }
            })
        }), total > 0);
    }

    function renderizarStatus(s) {
        el('stEmAndamento').textContent = inteiro.format(s.emAndamento);
        el('stFinalizados').textContent = inteiro.format(s.finalizados);
        el('stCancelados').textContent = inteiro.format(s.cancelados);
        el('stTempoPreparo').textContent = minutos(s.tempoMedioPreparoMinutos);
        el('stTempoSaida').textContent = minutos(s.tempoMedioAteSaidaMinutos);
        el('stTempoTotal').textContent = minutos(s.tempoMedioTotalMinutos);

        const comPedidos = s.porStatus.filter(q => q.quantidade > 0);
        desenhar('graficoStatus', () => ({
            type: 'doughnut',
            data: {
                labels: comPedidos.map(q => q.descricao),
                datasets: [{
                    data: comPedidos.map(q => q.quantidade),
                    backgroundColor: comPedidos.map(q => CORES_STATUS[q.status] || '#adb5bd'),
                    borderColor: '#fff', borderWidth: 2
                }]
            },
            options: opcoesBase({
                cutout: '62%',
                interaction: {mode: 'nearest', intersect: true},
                plugins: {
                    tooltip: {
                        callbacks: {
                            label: ctx => ` ${ctx.label}: ${ctx.parsed} (${decimal1.format(comPedidos[ctx.dataIndex].percentual)}%)`
                        }
                    }
                }
            })
        }), comPedidos.length > 0);
    }

    function renderizarRanking(ranking) {
        const corpo = el('tabelaRanking');
        corpo.replaceChildren();
        if (ranking.length === 0) {
            corpo.append(linhaVazia(5, 'Nenhum produto vendido no período.'));
            return;
        }
        const porFaturamento = estado.ordenarPor === 'FATURAMENTO';
        const valorDe = p => porFaturamento ? Number(p.faturamento) : p.quantidade;
        const maior = Math.max(...ranking.map(valorDe)) || 1;

        ranking.forEach(p => {
            const tr = document.createElement('tr');
            tr.append(
                    celula(`${p.posicao}º`, 'ps-3 fw-bold text-muted'),
                    celulaProduto(p.nome, valorDe(p) / maior * 100),
                    celula(inteiro.format(p.quantidade), 'text-end' + (porFaturamento ? '' : ' fw-semibold')),
                    celula(moeda.format(p.faturamento), 'text-end' + (porFaturamento ? ' fw-semibold' : '')),
                    celula(`${decimal1.format(p.participacaoFaturamento)}%`, 'text-end pe-3 text-muted')
            );
            corpo.append(tr);
        });
    }

    function celulaProduto(nome, percentual) {
        const td = document.createElement('td');
        const nomeEl = document.createElement('div');
        nomeEl.className = 'fw-medium';
        nomeEl.textContent = nome || '—';
        const barra = document.createElement('div');
        barra.className = 'barra-ranking';
        const preenchimento = document.createElement('div');
        preenchimento.style.width = percentual.toFixed(1) + '%';
        barra.append(preenchimento);
        td.append(nomeEl, barra);
        return td;
    }

    function renderizarRecentes(pedidos) {
        const corpo = el('tabelaRecentes');
        corpo.replaceChildren();
        if (pedidos.length === 0) {
            corpo.append(linhaVazia(6, 'Nenhum pedido no período selecionado.'));
            return;
        }
        pedidos.forEach(p => {
            const tdPedido = document.createElement('td');
            tdPedido.className = 'ps-3';
            const link = document.createElement('a');
            link.href = '/pedidos/' + encodeURIComponent(p.id);
            link.className = 'fw-semibold text-decoration-none';
            link.textContent = '#' + String(p.id).slice(-6).toUpperCase();
            tdPedido.append(link);

            const tdStatus = document.createElement('td');
            const badge = document.createElement('span');
            badge.className = 'badge ' + (p.corBadge || 'bg-secondary');
            badge.textContent = p.statusDescricao || p.status || '—';
            tdStatus.append(badge);

            const tr = document.createElement('tr');
            tr.append(
                    tdPedido,
                    celula(dataHoraCurta(p.dataHoraEntrada)),
                    celula(p.clienteNome || '—'),
                    celula(p.tipoEntregaDescricao || '—'),
                    tdStatus,
                    celula(moeda.format(p.valorTotal), 'text-end pe-3 fw-semibold')
            );
            corpo.append(tr);
        });
    }

    function desenhar(idCanvas, criarConfig, temDados) {
        const area = el(idCanvas).closest('.grafico-area');
        area.classList.toggle('sem-dados', !temDados);
        if (!graficosDisponiveis) {
            area.classList.add('sem-dados');
            area.querySelector('.grafico-vazio').textContent = 'Gráfico indisponível: a biblioteca Chart.js não carregou.';
            return;
        }
        const config = criarConfig();
        const existente = estado.graficos[idCanvas];
        if (existente && existente.config.type === config.type) {
            existente.data = config.data;
            existente.options = config.options;
            existente.update();
        } else {
            if (existente) {
                existente.destroy();
            }
            estado.graficos[idCanvas] = new Chart(el(idCanvas), config);
        }
    }

    function opcoesBase(extra = {}) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {mode: 'index', intersect: false},
            ...extra,
            plugins: {
                legend: {position: 'bottom', labels: {usePointStyle: true, boxWidth: 8}},
                ...(extra.plugins || {})
            }
        };
    }

    function celula(texto, classe) {
        const td = document.createElement('td');
        td.textContent = texto;
        if (classe) {
            td.className = classe;
        }
        return td;
    }

    function linhaVazia(colunas, texto) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = colunas;
        td.className = 'text-center text-muted py-4';
        td.textContent = texto;
        tr.append(td);
        return tr;
    }

    function moedaCurta(valor) {
        return valor >= 1000 ? `R$ ${decimal1.format(valor / 1000)} mil` : `R$ ${inteiro.format(valor)}`;
    }

    function minutos(valor) {
        return valor === null || valor === undefined ? '—' : `${decimal1.format(valor)} min`;
    }

    function data(iso) {
        if (!iso) {
            return '—';
        }
        const [ano, mes, dia] = iso.substring(0, 10).split('-');
        return `${dia}/${mes}/${ano}`;
    }

    function dataHora(iso) {
        return iso ? `${data(iso)} ${iso.substring(11, 19)}` : '—';
    }

    function dataHoraCurta(iso) {
        if (!iso) {
            return '—';
        }
        const [, mes, dia] = iso.substring(0, 10).split('-');
        return `${dia}/${mes} ${iso.substring(11, 16)}`;
    }

    function isoLocal(d) {
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }

    const CAMPOS_URL = [
        ['dataInicio', 'filtroDataInicio'],
        ['dataFim', 'filtroDataFim'],
        ['tipoEntrega', 'filtroTipoEntrega'],
        ['status', 'filtroStatus'],
        ['responsavel', 'filtroResponsavel']
    ];

    function aplicarFiltrosDaUrl() {
        const params = new URLSearchParams(window.location.search);
        CAMPOS_URL.forEach(([param, id]) => {
            if (params.has(param)) {
                el(id).value = params.get(param);
            }
        });
    }

    function sincronizarUrl() {
        const params = lerFiltros().toString();
        window.history.replaceState(null, '', window.location.pathname + (params ? '?' + params : ''));
    }

    function configurarAutoAtualizacao() {
        clearInterval(estado.timer);
        estado.timer = null;
        if (el('chkAutoAtualizar').checked) {
            estado.timer = setInterval(() => {
                if (!document.hidden) {
                    carregarTudo();
                }
            }, INTERVALO_ATUALIZACAO_MS);
        }
    }

    function ativarBotao(botoes, ativo) {
        botoes.forEach(b => b.classList.toggle('active', b === ativo));
    }

    el('formFiltros').addEventListener('submit', evento => {
        evento.preventDefault();
        carregarTudo();
    });

    el('btnLimpar').addEventListener('click', () => {
        el('formFiltros').reset();
        carregarTudo();
    });

    el('btnAtualizar').addEventListener('click', carregarTudo);
    el('chkAutoAtualizar').addEventListener('change', configurarAutoAtualizacao);

    document.querySelectorAll('.btn-periodo').forEach(botao => {
        botao.addEventListener('click', () => {
            const dias = Number(botao.dataset.dias);
            const fim = new Date();
            const inicio = new Date();
            inicio.setDate(fim.getDate() - (dias - 1));
            el('filtroDataInicio').value = isoLocal(inicio);
            el('filtroDataFim').value = isoLocal(fim);
            carregarTudo();
        });
    });

    const botoesAgrupamento = document.querySelectorAll('[data-agrupamento]');
    botoesAgrupamento.forEach(botao => {
        botao.addEventListener('click', () => {
            estado.agrupamento = botao.dataset.agrupamento;
            ativarBotao(botoesAgrupamento, botao);
            carregarParte(async () => {
                const resposta = await buscar('vendas', {agrupamento: estado.agrupamento});
                renderizarVendas(resposta.dados);
            });
        });
    });

    const botoesOrdenar = document.querySelectorAll('[data-ordenar]');
    botoesOrdenar.forEach(botao => {
        botao.addEventListener('click', () => {
            estado.ordenarPor = botao.dataset.ordenar;
            ativarBotao(botoesOrdenar, botao);
            carregarParte(async () => {
                const resposta = await buscar('produtos-mais-vendidos', {limite: 5, ordenarPor: estado.ordenarPor});
                renderizarRanking(resposta.dados);
            });
        });
    });

    document.addEventListener('visibilitychange', () => {
        if (!document.hidden && el('chkAutoAtualizar').checked) {
            carregarTudo();
        }
    });

    aplicarFiltrosDaUrl();
    configurarAutoAtualizacao();
    carregarTudo();
})();
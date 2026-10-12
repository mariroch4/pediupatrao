(function () {
    const POR_PAGINA_PADRAO = 10;

    function paginasVisiveis(atual, total) {
        if (total <= 7) {
            return Array.from({length: total}, function (_, i) { return i + 1; });
        }
        const paginas = [1];
        const inicio = Math.max(2, atual - 1);
        const fim = Math.min(total - 1, atual + 1);
        if (inicio > 2) {
            paginas.push('...');
        }
        for (let p = inicio; p <= fim; p++) {
            paginas.push(p);
        }
        if (fim < total - 1) {
            paginas.push('...');
        }
        paginas.push(total);
        return paginas;
    }

    function itemPagina(rotulo, destino, desabilitado, ativo) {
        return '<li class="page-item' + (desabilitado ? ' disabled' : '') + (ativo ? ' active' : '') + '">'
                + '<a class="page-link" href="#" data-pagina="' + destino + '">' + rotulo + '</a></li>';
    }

    function paginar(tabela) {
        const corpo = tabela.tBodies[0];
        if (!corpo) {
            return;
        }

        if (tabela._navPaginacao) {
            tabela._navPaginacao.remove();
            tabela._navPaginacao = null;
        }

        const porPagina = parseInt(tabela.dataset.paginar, 10) || POR_PAGINA_PADRAO;
        const linhas = Array.from(corpo.rows);
        linhas.forEach(function (linha) { linha.style.display = ''; });

        const totalPaginas = Math.ceil(linhas.length / porPagina);
        if (totalPaginas <= 1) {
            return; 
        }

        const nav = document.createElement('nav');
        nav.setAttribute('aria-label', 'Paginação');
        nav.className = 'd-flex justify-content-between align-items-center flex-wrap gap-2 px-3 py-2 border-top';
        (tabela.closest('.table-responsive') || tabela).after(nav);
        tabela._navPaginacao = nav;

        function mostrar(pagina) {
            const inicio = (pagina - 1) * porPagina;
            const fim = Math.min(inicio + porPagina, linhas.length);

            linhas.forEach(function (linha, indice) {
                linha.style.display = (indice >= inicio && indice < fim) ? '' : 'none';
            });

            let html = '<small class="text-muted">Mostrando ' + (inicio + 1) + '–' + fim
                    + ' de ' + linhas.length + '</small>'
                    + '<ul class="pagination pagination-sm mb-0">'
                    + itemPagina('&laquo;', pagina - 1, pagina === 1, false);

            paginasVisiveis(pagina, totalPaginas).forEach(function (p) {
                html += (p === '...')
                        ? '<li class="page-item disabled"><span class="page-link">…</span></li>'
                        : itemPagina(p, p, false, p === pagina);
            });

            html += itemPagina('&raquo;', pagina + 1, pagina === totalPaginas, false) + '</ul>';
            nav.innerHTML = html;
        }

        nav.addEventListener('click', function (evento) {
            const link = evento.target.closest('[data-pagina]');
            if (!link) {
                return;
            }
            evento.preventDefault();
            const pagina = parseInt(link.dataset.pagina, 10);
            if (pagina >= 1 && pagina <= totalPaginas) {
                mostrar(pagina);
            }
        });

        mostrar(1);
    }

    function iniciar() {
        document.querySelectorAll('main table').forEach(function (tabela) {
            if (!tabela.tHead || !tabela.tHead.classList.contains('table-dark')) {
                return;
            }
            paginar(tabela);

            if (tabela.tBodies[0]) {
                let agendamento = null;
                new MutationObserver(function () {
                    clearTimeout(agendamento);
                    agendamento = setTimeout(function () { paginar(tabela); }, 50);
                }).observe(tabela.tBodies[0], {childList: true});
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }

    window.paginarTabela = paginar;
})();
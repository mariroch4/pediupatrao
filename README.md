# PediuPatrão - Controle de Perfis, Pedidos, Auditoria e Dashboard Administrativo

**Aluna:** Mariana da Rocha Pereira Moreira
**Curso:** Engenharia de Software - Universidade de Mogi das Cruzes (UMC)

---

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.4.4 (Web, Data MongoDB, Security, Thymeleaf) |
| Segurança | Spring Security (autenticação por formulário, autorização por URL e por método, CSRF) |
| Banco de dados | MongoDB |
| Interface | Thymeleaf + Bootstrap 5 + Bootstrap Icons |
| Gráficos | Chart.js 4.4.1 |
| Testes | JUnit 5, Mockito, Spring MockMvc e Spring Security Test |
| Versionamento | Git e GitHub com Gitflow |

---

## Como executar

### Pré-requisitos
- JDK 21 (ex.: Eclipse Temurin 21)
- MongoDB Community Server rodando em `localhost:27017`
- Não é necessário instalar o Maven: o projeto inclui o **Maven Wrapper** (`mvnw` / `mvnw.cmd`)

### Passos
1. Clone o repositório:
```
   git clone https://github.com/mariroch4/pediupatrao.git
   cd pediupatrao
```
2. Confira a conexão com o banco em `src/main/resources/application.properties`:
```properties
   spring.data.mongodb.uri=mongodb://localhost:27017
   spring.data.mongodb.database=pediupatrao
```
3. Execute:
   - NetBeans: abrir o projeto e executar com **Run** (F6)
4. Acesse http://localhost:8080

Na primeira execução:
- o `DataInitializer` cria automaticamente um usuário de cada perfil e alguns produtos de exemplo;
- o `DemoDataInitializer` gera 180 pedidos de demonstração nos últimos 60 dias, para alimentar o dashboard. Isso só acontece se a coleção de pedidos estiver vazia. Para desligar, use `pediupatrao.demo.gerar-pedidos=false` no `application.properties`.

---

### Usuários de teste

| Perfil | Usuário | Senha | Dashboard |
|---|---|---|:-:|
| ADMIN | `admin` | `teste123` | ✅ |
| GERENTE | `gerente01` | `gerente123` | ✅ |
| ATENDENTE | `atendente01` | `atendente123` | ❌ |

> As senhas acima existem apenas para ambiente de desenvolvimento/demonstração e são armazenadas com BCrypt.

---

### Executar os testes
1. Abra o projeto no NetBeans.
2. Clique com o botão direito no projeto e escolha Test.
3. Acompanhe o resultado na janela Test Results (Window → IDE Tools → Test Results), que lista
   cada teste pelo nome descritivo, como *"Nem o ADMIN aplica desconto (403)"*.

Resultado esperado: `Tests run: 61, Failures: 0, Errors: 0` e `BUILD SUCCESS`.

---

# Dashboard Administrativo

Acesso pelo menu **Gestão > Dashboard** (perfis ADMIN e GERENTE). A tela não acessa o banco diretamente: todos os dados vêm da API `/api/dashboard/**`, que filtra os pedidos no MongoDB e calcula os indicadores no servidor.

A documentação completa (requisitos, endpoints com exemplos e erros, decisões de projeto e evidências de testes) está em **[docs/Documentacao-Dashboard-PediuPatrao.pdf](docs/Documentacao-Dashboard-PediuPatrao.pdf)**.

## Requisitos implementados

| RF | Requisito | Implementação |
|---|---|---|
| RF01 | Indicadores gerais de vendas | Faturamento, pedidos, ticket médio, itens vendidos, descontos e cancelamentos, com variação em relação ao período anterior |
| RF02 | Análise de vendas (gráficos) | Evolução das vendas por dia, semana ou mês, pedidos por hora do dia e entrega x retirada |
| RF03 | Situação dos pedidos | Quantidade e percentual por status, pedidos em andamento e tempos médios de preparo, saída e total |
| RF04 | Produtos mais vendidos | Ranking por quantidade ou faturamento, com participação de cada produto |
| RF05 | Filtros de consulta | Período, tipo de atendimento, status e responsável, com atalhos Hoje / 7 / 30 / 60 dias e o período exibido em destaque |
| RF06 | Integração com a API | 7 endpoints REST integrados ao MongoDB, consumidos pela tela em paralelo |
| RF07 | Atualização e tratamento de dados | Atualização automática a cada 60 s, botão Atualizar, horário da última atualização e mensagens para 400, 401, 403, 422 e falha de conexão |

## Endpoints do Dashboard

Todos são `GET`, exigem login com perfil ADMIN ou GERENTE e aceitam os filtros `dataInicio`, `dataFim` (AAAA-MM-DD), `tipoEntrega`, `status` e `responsavel`. Sem datas, o período padrão são os últimos 30 dias, e o período máximo é de 366 dias.

| Rota | RF | Parâmetros extras |
|---|---|---|
| `/api/dashboard/indicadores` | RF01 | - |
| `/api/dashboard/vendas` | RF02 | `agrupamento` = `DIA` \| `SEMANA` \| `MES` |
| `/api/dashboard/vendas-por-hora` | RF02 | - |
| `/api/dashboard/vendas-por-tipo` | RF02 | - |
| `/api/dashboard/status` | RF03 | - |
| `/api/dashboard/produtos-mais-vendidos` | RF04 | `limite` (1 a 50), `ordenarPor` = `QUANTIDADE` \| `FATURAMENTO` |
| `/api/dashboard/pedidos-recentes` | apoio | `limite` (1 a 50) |

Formato das respostas: `{ "filtros": {...}, "atualizadoEm": "...", "dados": ... }`.
Erros: 400 parâmetro inválido · 401 sem login · 403 perfil sem permissão · 422 regra violada (ex.: data final antes da inicial).

## Principais decisões
- **Cálculo no servidor** com `BigDecimal`: a tela só exibe, e as regras ficam em um lugar só, testado.
- **Período filtrado no banco**, com índice em `dataHoraEntrada`.
- **Pedidos cancelados** não entram no faturamento, mas entram na taxa de cancelamento.
- **Segurança em duas camadas**: regra de URL no `SecurityConfig` + `@PreAuthorize` no `DashboardService`.
- **Proteção contra XSS** na tela: textos do banco exibidos com `textContent`.

---

# Perfis, Pedidos e Auditoria

## Requisitos implementados

### RF01 - Controle de usuários e permissões
- Três perfis definidos no `enum Perfil`: ADMIN, GERENTE e ATENDENTE.
- O login (`UsuarioDetailsService`) usa o perfil gravado no banco (antes, todos recebiam `USER`).
- Autorização no backend em duas camadas:
  1. regras por rota no `SecurityConfig` (telas e APIs);
  2. `@PreAuthorize` nos métodos dos services (`@EnableMethodSecurity`).
- Somente o GERENTE aplica descontos e cancela pedidos.
- Proteção contra autoelevação: apenas o ADMIN administra usuários, ninguém altera o próprio perfil
  ou username, ninguém exclui a si mesmo e o sistema sempre mantém ao menos um ADMIN.
- Tentativas indevidas: páginas respondem 403, APIs respondem JSON 403
  (ou 401 sem login). Toda tentativa é registrada no log.

#### Matriz de permissões

| Recurso | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Usuários (listar, criar, editar, perfis) | ✅ | ❌ | ❌ |
| Auditoria (consultar) | ✅ | ✅ | ❌ |
| **Dashboard (tela e API)** | ✅ | ✅ | ❌ |
| Pedidos — consultar | ✅ | ✅ | ✅ |
| Pedidos — registrar e mudar status | ❌ | ✅ | ✅ |
| Pedidos — **cancelar e aplicar desconto** | ❌ | ✅ | ❌ |
| Clientes — consultar | ❌ | ✅ | ✅ |
| Clientes — cadastrar e alterar | ❌ | ❌ | ✅ |
| Clientes — excluir | ❌ | ✅ | ❌ |
| Produtos — consultar / alterar | ✅ / ✅ | ✅ / ✅ | ✅ / ❌ |

### RF02 - Evolução do cadastro de pedidos
- O `Pedido` passou a ter: itens (produto, quantidade, preço unitário e subtotal), subtotal,
  percentual e valor de desconto, valor total, responsável pelo registro e data/hora de entrada,
  tipo de atendimento (entrega/retirada), observação e histórico de status.
- O total é calculado no servidor.
- Validações: cliente existente, ao menos um item, produto ativo e com preço, quantidade entre 1 e 50.

### RF03 - Entrada, saída e transições
Fluxo obrigatório implementado como máquina de estados no `enum StatusPedido`:
RECEBIDO → EM PREPARAÇÃO → PRONTO → SAIU PARA ENTREGA (entrega) / RETIRADO (retirada) → FINALIZADO
- Avanço de uma etapa por vez.
- A saída depende do tipo de atendimento (entrega → Saiu para entrega; retirada → Retirado).
- Entrada e saída registram obrigatoriamente data/hora e responsável (definidos pelo servidor).
- `FINALIZADO` e `CANCELADO` são estados finais: nenhuma alteração de status é aceita depois deles.
- `CANCELADO` não é uma transição comum: só é alcançado pela operação de cancelamento do GERENTE.

### RF04 - Cancelamentos e descontos (exclusivos do GERENTE)
- Cancelamento: justificativa obrigatória, permitido apenas antes da saída,
  preserva os dados históricos (itens, valores e andamento) e impede alterações posteriores.
- Desconto: percentual, maior que 0 e limitado a 20% do valor do pedido, permitido apenas antes da
  saída e o valor do desconto e o novo total são recalculados no servidor.
- Ambas as operações são registradas na auditoria com usuário, data/hora e valores anteriores e novos.

### RF05 - Auditoria
- Entidade, repositório e serviço próprios, persistidos na coleção `auditoria` do MongoDB.
- Operações auditadas: inclusão de pedido, mudanças de status, desconto, cancelamento, inclusão,
  alteração e exclusão de clientes, e, adicionalmente, a gestão de usuários e perfis.
- Cada registro contém: usuário autenticado e perfil, data/hora, tipo de operação, entidade e
  identificador, campos alterados com valores anteriores e novos e justificativa (quando exigida).
- Consulta exclusiva para ADMIN e GERENTE.
- Integridade: o `AuditoriaRepository` não estende `MongoRepository`, a entidade não tem setters e a API só aceita `GET` (`PUT`/`DELETE` → 405).
- Proteção de dados: senhas, hashes e tokens nunca são gravados (troca de senha aparece como
  `[protegida] → [alterada]`). Seguindo o princípio da minimização de dados (LGPD), o ADMIN não
  tem acesso ao cadastro de clientes, vê os dados pessoais de clientes mascarados na auditoria e
  o GERENTE os vê completos. O registro original no banco permanece íntegro.

---

## Principais alterações em relação ao projeto original

| Problema encontrado no projeto original | Correção |
|---|---|
| `/api/usuarios` liberada sem login | Rota restrita ao ADMIN + `@PreAuthorize` no service |
| Todos os usuários recebiam a role `USER` | Enum `Perfil` e login com o perfil real |
| CSRF desativado | CSRF ativo em formulários e chamadas `fetch` |
| Banco compartilhado pela turma (MongoDB Atlas com credencial no código) | MongoDB local; credencial removida do `application.properties` |
| Hash da senha retornado pela API e enviado ao formulário de edição | Senha `WRITE_ONLY` no JSON e campo vazio no formulário |
| Pedido com `tipoPizza`, `sabor` e `status` em texto livre, sem valores | Novo modelo com itens, totais calculados no servidor e máquina de estados |
| Regras de negócio nos controllers | Regras centralizadas nos services e controllers separados por domínio |
| Lista de clientes vulnerável a XSS (dados inseridos como HTML) | Dados escapados antes da exibição |
| Ausência de tratamento padronizado de erros | `ApiExceptionHandler` (400/404/422 em JSON) e página 403 |
| Sem testes automatizados | 61 testes (unitários, de segurança e da API do dashboard) |

---

## Principais endpoints da API

| Método | Rota | Perfis | Descrição |
|---|---|---|---|
| GET | `/api/pedidos` | ADMIN, GERENTE, ATENDENTE | Lista pedidos |
| POST | `/api/pedidos` | GERENTE, ATENDENTE | Registra pedido (total calculado no servidor) |
| PUT | `/api/pedidos/{id}/status` | GERENTE, ATENDENTE | Avança o status (`{"status": "EM_PREPARACAO"}`) |
| POST | `/api/pedidos/{id}/desconto` | GERENTE | Aplica desconto (`{"percentual": 10, "motivo": "..."}`) |
| POST | `/api/pedidos/{id}/cancelar` | GERENTE | Cancela (`{"justificativa": "..."}`) |
| GET | `/api/clientes` | GERENTE, ATENDENTE | Lista clientes |
| POST / PUT | `/api/clientes`, `/api/clientes/{id}` | ATENDENTE | Cadastra / altera cliente |
| DELETE | `/api/clientes/{id}` | GERENTE | Exclui cliente |
| GET / POST / PUT / DELETE | `/api/usuarios/**` | ADMIN | Gestão de usuários |
| GET | `/api/auditoria` | ADMIN, GERENTE | Consulta auditoria (filtros: `entidade`, `entidadeId`, `usuario`, `operacao`, `dataInicio`, `dataFim`) |
| GET | `/api/dashboard/**` | ADMIN, GERENTE | Dashboard administrativo (ver seção da Atividade 2) |

Códigos de resposta: 400 parâmetro ou corpo inválido · 401 sem login · 403 perfil sem permissão ou CSRF inválido ·
404 registro inexistente · 405 método não permitido · 422 regra de negócio violada.

---

## Testes automatizados

**61 testes** no total.

| Classe | O que verifica |
|---|---|
| `StatusPedidoTest` | Máquina de estados: fluxos de entrega e retirada, bloqueio de pular/voltar etapas, estados finais |
| `PedidoServiceTest` | Total calculado com preço do banco, limite de 20%, desconto/cancelamento só antes da saída, justificativa obrigatória, dados preservados, responsável na saída, auditoria |
| `UsuarioServiceTest` | Bloqueio de alteração do próprio perfil e de autoexclusão, senha mínima, username único |
| `AuditoriaServiceTest` | Registro só dos campos alterados, usuário vindo do login, mascaramento de dados pessoais por perfil |
| `SegurancaApiTest` | APIs: 401 sem login, 403 para ATENDENTE e ADMIN em desconto/cancelamento, 403 sem CSRF, 403 na autoelevação, auditoria restrita, 405 ao excluir auditoria |
| `DashboardServiceTest` | Cálculos do dashboard: faturamento sem cancelados, ticket médio, variação, séries completadas com zero, vendas por hora, tempos médios, ranking e validação dos filtros |
| `DashboardApiTest` | API do dashboard: 401 sem login, 403 para ATENDENTE, 200 para GERENTE e ADMIN, filtros convertidos, 400 para parâmetro inválido e 422 para regra violada |

---

## Gitflow

| Branch | Finalidade |
|---|---|
| `main` | Versão estável entregue |
| `develop` | Integração das funcionalidades |
| `feature/api-dashboard` | API do dashboard: dados de demonstração, service, endpoints e testes |
| `feature/dashboard-tela` | Tela do dashboard |
| `feature/documentacao` | Documentação em PDF e README |
| `release/2.0.0` | Preparação da versão entregue (merge na `main` + tag `v2.0.0`) |

---

## Estrutura do projeto

```
src/main/java/com/umc/pediupatrao
├── config        → SecurityConfig, DataInitializer, DemoDataInitializer, MongoInitConfig, SegurancaUtil
├── controller    → APIs REST (*Controller) e telas Thymeleaf (*ViewController), incluindo o Dashboard
├── dto           → Dados aceitos/retornados pela API (NovoPedidoRequest, DescontoRequest...)
│   └── dashboard → Filtro e respostas do dashboard (IndicadoresDashboard, SituacaoPedidos...)
├── entity        → Pedido, ItemPedido, Cliente, Usuario, Auditoria, enums (Perfil, StatusPedido...)
├── exception     → RegraNegocioException, RecursoNaoEncontradoException, ApiExceptionHandler
├── repository    → Repositórios MongoDB (AuditoriaRepository somente inserção/leitura)
└── service       → Regras de negócio, auditoria e cálculos do dashboard (DashboardService)
src/main/resources
├── templates/dashboard → Tela do dashboard
└── static              → css/dashboard.css, js/dashboard.js
src/test/java/com/umc/pediupatrao → Testes automatizados
docs              → Documentação do Dashboard (PDF)
```

# PediuPatrão - Controle de Perfis, Pedidos e Auditoria

Evolução do sistema PediuPatrão (Atividade Prática M1 - Parte 2), usado por uma pizzaria para cadastrar
clientes, gerenciar produtos e registrar pedidos. Esta versão diferencia as responsabilidades dos funcionários
por perfil de acesso, controla a entrada, as transições e a saída dos pedidos, restringe descontos e
cancelamentos ao gerente e mantém uma auditoria persistente e imutável das alterações de pedidos e clientes.

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
| Testes | JUnit 5, Mockito, Spring MockMvc e Spring Security Test |

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

Na primeira execução, o `DataInitializer` cria automaticamente um usuário de cada perfil e alguns produtos de exemplo.

---

### Usuários de teste

| Perfil | Usuário | Senha |
|---|---|---|
| ADMIN | `admin` | `teste123` |
| GERENTE | `gerente01` | `gerente123` |
| ATENDENTE | `atendente01` | `atendente123` |

> As senhas acima existem apenas para ambiente de desenvolvimento/demonstração e são armazenadas com BCrypt.

---

### Executar os testes
1. Abra o projeto no NetBeans.
2. Clique com o botão direito no projeto e escolha Test.
3. Acompanhe o resultado na janela Test Results (Window → IDE Tools → Test Results), que lista
   cada teste pelo nome descritivo, como *"Nem o ADMIN aplica desconto (403)"*.

Resultado esperado: `Tests run: 37, Failures: 0, Errors: 0` e `BUILD SUCCESS`.

---

## Requisitos implementados

### RF01 — Controle de usuários e permissões
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
| Pedidos — consultar | ✅ | ✅ | ✅ |
| Pedidos — registrar e mudar status | ❌ | ✅ | ✅ |
| Pedidos — **cancelar e aplicar desconto** | ❌ | ✅ | ❌ |
| Clientes — consultar | ❌ | ✅ | ✅ |
| Clientes — cadastrar e alterar | ❌ | ❌ | ✅ |
| Clientes — excluir | ❌ | ✅ | ❌ |
| Produtos — consultar / alterar | ✅ / ✅ | ✅ / ✅ | ✅ / ❌ |

### RF02 — Evolução do cadastro de pedidos
- O `Pedido` passou a ter: itens (produto, quantidade, preço unitário e subtotal), subtotal,
  percentual e valor de desconto, valor total, responsável pelo registro e data/hora de entrada,
  tipo de atendimento (entrega/retirada), observação e histórico de status.
- O total é calculado no servidor.
- Validações: cliente existente, ao menos um item, produto ativo e com preço, quantidade entre 1 e 50.

### RF03 — Entrada, saída e transições
Fluxo obrigatório implementado como máquina de estados no `enum StatusPedido`:
RECEBIDO → EM PREPARAÇÃO → PRONTO → SAIU PARA ENTREGA (entrega) / RETIRADO (retirada) → FINALIZADO
- Avanço de uma etapa por vez.
- A saída depende do tipo de atendimento (entrega → Saiu para entrega; retirada → Retirado).
- Entrada e saída registram obrigatoriamente data/hora e responsável (definidos pelo servidor).
- `FINALIZADO` e `CANCELADO` são estados finais: nenhuma alteração de status é aceita depois deles.
- `CANCELADO` não é uma transição comum: só é alcançado pela operação de cancelamento do GERENTE.

### RF04 — Cancelamentos e descontos (exclusivos do GERENTE)
- Cancelamento: justificativa obrigatória, permitido apenas antes da saída,
  preserva os dados históricos (itens, valores e andamento) e impede alterações posteriores.
- Desconto: percentual, maior que 0 e limitado a 20% do valor do pedido, permitido apenas antes da
  saída e o valor do desconto e o novo total são recalculados no servidor.
- Ambas as operações são registradas na auditoria com usuário, data/hora e valores anteriores e novos.

### RF05 — Auditoria
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

Códigos de resposta: 401 sem login · 403 perfil sem permissão ou CSRF inválido ·
405 método não permitido · 422 regra de negócio violada · 404 registro inexistente.

---

## Testes automatizados

| Classe | O que verifica |
|---|---|
| `StatusPedidoTest` | Máquina de estados: fluxos de entrega e retirada, bloqueio de pular/voltar etapas, estados finais |
| `PedidoServiceTest` | Total calculado com preço do banco, limite de 20%, desconto/cancelamento só antes da saída, justificativa obrigatória, dados preservados, responsável na saída, auditoria |
| `UsuarioServiceTest` | Bloqueio de alteração do próprio perfil e de autoexclusão, senha mínima, username único |
| `AuditoriaServiceTest` | Registro só dos campos alterados, usuário vindo do login, mascaramento de dados pessoais por perfil |
| `SegurancaApiTest` | APIs: 401 sem login, 403 para ATENDENTE e ADMIN em desconto/cancelamento, 403 sem CSRF, 403 na autoelevação, auditoria restrita, 405 ao excluir auditoria |

---

## Estrutura do projeto

```
src/main/java/com/umc/pediupatrao
├── config        → SecurityConfig, DataInitializer, SegurancaUtil
├── controller    → APIs REST (*Controller) e telas Thymeleaf (*ViewController)
├── dto           → Dados aceitos/retornados pela API (NovoPedidoRequest, DescontoRequest...)
├── entity        → Pedido, ItemPedido, Cliente, Usuario, Auditoria, enums (Perfil, StatusPedido...)
├── exception     → RegraNegocioException, RecursoNaoEncontradoException, ApiExceptionHandler
├── repository    → Repositórios MongoDB (AuditoriaRepository somente inserção/leitura)
└── service       → Regras de negócio e auditoria
src/test/java/com/umc/pediupatrao → Testes automatizados
```


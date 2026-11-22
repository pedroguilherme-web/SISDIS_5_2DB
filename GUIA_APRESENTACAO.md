# AISafe — guia curto de apresentação

## Resumo para decorar

> O AISafe é uma API em Java e Spring Boot para gerir aeroportos, rotas, aeronaves, manutenção e voos. Está dividido em três serviços executáveis. O `shared-core` é uma biblioteca comum, não um serviço.

## Serviços

| Serviço | Responsabilidade | Porta local |
|---|---|---:|
| `airports-routes-service` | Aeroportos e rotas | 8092 |
| `aircraft-maintenance-service` | Aeronaves e manutenção | 8081 |
| `flight-operations-service` | Operações de voo | 8071 |
| `shared-core` | Código partilhado | — |

As portas podem ser alteradas por configuração. `shared-core` não arranca sozinho.

## DDD e divisão dos serviços

**DDD** ajuda a organizar o sistema pelos domínios do negócio, com modelos e regras próprios. Não obriga a usar microserviços: também pode ser aplicado num monólito modular.

> Dividimos o sistema por responsabilidades do negócio. Isso permite evoluir e executar cada serviço separadamente. Em contrapartida, aumenta a comunicação pela rede e a dificuldade de manter dados consistentes.

Esta divisão é defensável como arquitetura didática. Para produção, é preciso justificar os limites dos domínios e tratar falhas, persistência e comunicação entre serviços. Se não houver necessidade de escalar ou publicar serviços separadamente, um monólito modular pode ser mais simples.

## Consistência entre serviços

- **Dentro de um serviço:** a transação protege as alterações na sua base de dados local.
- **Entre serviços:** não há consistência forte nem replicação geral de escritas. Cada instância mantém os seus dados.
- **P2P:** algumas leituras podem consultar outro peer; isso não sincroniza as escritas.
- **RabbitMQ:** existe uma projeção específica para o humor das aeronaves. Se as mensagens forem publicadas e AMQP estiver ativo, essa projeção é eventualmente consistente. Não se aplica a todos os dados.
- **H2 em memória:** os dados podem desaparecer quando a aplicação reinicia.

> Resposta curta: “A consistência é local por serviço. Entre serviços não garantimos que todas as escritas sejam replicadas. Só há consistência eventual na projeção específica alimentada por eventos.”

## Preparar a demonstração

Requisito: **Java 21**. Não é necessário instalar Maven; o projeto inclui o Maven Wrapper.

Na raiz do projeto, inicia Airports & Routes:

```bash
bash ./mvnw -pl airports-routes-service spring-boot:run
```

Espera pela mensagem de arranque na porta `8092`. Mantém esse terminal aberto.

- Swagger: <http://localhost:8092/swagger-ui/index.html>
- Postman: importa `docs/postman/psoft.postman_collection.json`.
- Na coleção Postman, define `base_url` como `http://localhost:8092`.
- Em **Auth**, executa **Login as Admin**. A coleção guarda o JWT para os pedidos seguintes.
- Credenciais locais por omissão: `admin` / `admin123`. Variáveis de ambiente podem substituí-las.

O Swagger permite explorar a API, mas não está configurado para aplicar automaticamente o JWT aos pedidos protegidos. Para esses pedidos, usa a coleção Postman ou envia o header `Authorization: Bearer <token>`.

H2 é local e em memória. Confirma que os dados necessários à demonstração existem; podem não estar lá depois de reiniciar o serviço. RabbitMQ está desativado por omissão e não é necessário para os pedidos básicos.

## Guião curto: Airports & Routes

1. **Apresentar:** “Este serviço gere aeroportos e as rotas que os ligam.”
2. **Demonstrar:** criar e consultar um aeroporto; depois criar e consultar uma rota.
3. **Explicar permissões:** “O JWT autentica o utilizador; o papel determina o que pode fazer.”
4. **Explicar `shared-core`:** “É uma biblioteca de componentes reutilizados, não um serviço de rede.”

Usa **Backoffice Operator** para registar aeroportos e **ATCC** para criar rotas. A consulta deve devolver `200 OK`; uma criação bem-sucedida devolve `201 Created`.

## Respostas rápidas

**Porque Spring Boot e REST?**  
“Spring Boot simplifica a criação dos serviços. REST permite aceder às funcionalidades por HTTP.”

**Porque H2?**  
“É simples para desenvolvimento e demonstração. A desvantagem é que, em memória, os dados não persistem após o reinício.”

**Porque JWT e papéis?**  
“O JWT identifica o utilizador em cada pedido. Os papéis limitam as operações permitidas.”

**O sistema garante consistência eventual entre todas as réplicas?**  
“Não. As bases de dados são separadas e as escritas não são replicadas em geral. A pesquisa P2P não significa replicação.”

**O enunciado exigia `shared-core`?**  
“Não. Foi uma escolha da equipa para reutilizar código comum; o serviço usa essa biblioteca.”

**A demonstração local usa HTTPS?**  
“Não, usa HTTP. HTTPS requer o perfil TLS e um keystore configurado.”

## Antes de apresentar

- Confirma Java 21 e inicia o serviço antes de abrir o Swagger/Postman.
- Testa uma consulta e uma escrita de aeroporto e de rota.
- Confirma os dados e os utilizadores antes da demonstração.
- Não afirmes que todas as escritas são replicadas ou que todas as instâncias convergem.

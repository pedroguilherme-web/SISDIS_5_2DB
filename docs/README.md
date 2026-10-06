# Transição de Arquitetura &amp; Divisão de Trabalho — AISafe Flight Management System

Documento técnico de especificação arquitetural e divisão de responsabilidades para o projeto prático P1 da disciplina de Sistemas Distribuídos (LETI-SIDIS 2026/2027, ISEP — Data limite de submissão: 25 de outubro de 2026).

---

## 1\. Contexto e Motivação: A Limitação do Monólito

No âmbito da disciplina de Projeto de Software (PSOFT), o sistema de gestão de voos **AISafe Flight Management System** foi originalmente desenvolvido sobre uma arquitetura monolítica RESTful. Embora essa abordagem tenha permitido gerir as entidades core do domínio — tais como modelos e instâncias de aeronaves, registos de aeroportos, rotas de voo, planos de manutenção e voos agendados —, a evolução das exigências operacionais evidenciou limitações estruturais do modelo monolítico:

* **Ponto Único de Falha (Single Point of Failure)**: Numa unidade de execução única com base de dados partilhada, a falha do processo principal torna todo o sistema AISafe indisponível, paralisando a gestão da frota e a operação de voos.
* **Gargalos de Escalabilidade**: A escalabilidade do monólito exige dimensionar verticalmente toda a aplicação de forma indivisível, mesmo quando o aumento de carga afeta apenas um domínio específico (como o agendamento de rotas em horas de ponta).
* **Risco e Complexidade na Manutenção**: Alterações ou correções num módulo exigem a redistribuição (*redeploy*) integral da aplicação, aumentando o risco de paragens não planeadas (*downtime*).

A transição para uma arquitetura de serviços separados visou eliminar estes gargalos, garantindo o desacoplamento das entidades de negócio, a evolução independente dos módulos e a alta disponibilidade do sistema.

---

## 2\. A Escolha da Arquitetura Distribuída e Fundamentos CAP

A opção por reestruturar o AISafe num sistema distribuído, descentralizado e tolerante a falhas baseia-se em princípios fundamentais de computação distribuída e no **Teorema CAP**:

### Compromisso CAP e Modelo de Consistência

O Teorema CAP estabelece que um sistema distribuído não pode garantir em simultâneo a **Consistência Estrita** (Consistency), a **Disponibilidade** (Availability) e a **Tolerância a Partições** (Partition Tolerance). Para o ambiente operacional do sistema AISafe:

* **Priorização de Disponibilidade (A) e Tolerância a Partições (P)**: O sistema é desenhado para manter os serviços funcionais e acessíveis mesmo na presença de falhas parciais de rede ou indisponibilidade de nós individuais.
* **Adoção de Consistência Eventual (Eventual Consistency)**: O sistema abdica da consistência síncrona imediata entre todas as réplicas. Quando ocorre uma atualização (ex.: alteração do estado de uma aeronave), os dados são propagados e as réplicas convergem gradualmente para um estado consistente.

| Propriedade CAP                                  | Estratégia no AISafe                               | Impacto Operacional                                                                               |
| ------------------------------------------------ | -------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| **Disponibilidade (Availability)**               | Elevada prioridade via réplicas/clones de serviços | As consultas e operações continuam operacionais mesmo com falhas de instâncias.                   |
| **Tolerância a Partições (Partition Tolerance)** | Elevada prioridade via reencaminhamento P2P        | O sistema funciona mesmo que a comunicação entre certos nós seja interrompida.                    |
| **Consistência (Consistency)**                   | Consistência Eventual (Eventual Consistency)       | Pequenos atrasos na sincronização de alterações são aceites em favor da disponibilidade contínua. |

---

## 3\. Segregação Baseada em Domínios (Domain-Driven Design)

Seguindo os princípios de *Domain-Driven Design* (DDD), a aplicação monolítica foi decomposta em três serviços autónomos e colaborativos que comunicam através de APIs HTTP/REST:

### 1\. Serviço de Aeronaves e Manutenção (*Aircraft &amp; Maintenance Service*)

Responsável pela gestão da frota e do seu ciclo de vida operativo:

* Registo e consulta de modelos e instâncias individuais de aeronaves.
* Atualização do estado operacional das aeronaves (Ativa, Inativa, Em Manutenção).
* Definição de modelos de manutenção e registo/conclusão de operações de manutenção.

### 2\. Serviço de Aeroportos e Rotas (*Airports &amp; Routes Service*)

Focado na infraestrutura aeroportuária e no mapeamento de ligações:

* Registo e gestão operacional de aeroportos, dados de pistas e certificações.
* Criação, atualização, desativação e histórico de rotas de voo.
* Verificação de compatibilidade técnica entre requisitos de aeroportos e especificações das aeronaves.

### 3\. Serviço de Operações de Voo (*Flight Operations Service*)

Atua como o orquestrador das operações de transporte aéreo:

* Agendamento e planeamento de voos operacionais.
* Atribuição de aeronaves a rotas específicas respeitando restrições operacionais de alcance e capacidade.
* Consultas em tempo real de disponibilidade de frota e voos agendados.

---

## 4\. Mecanismos de Infraestrutura e Resiliência Distribuída

Para garantir a tolerância a falhas e a descentralização do armazenamento, a arquitetura integra os seguintes padrões:

1. **Clonagem de Instâncias (Replication)**: Execução de múltiplos clones independentes por serviço (ex.: portas 8081, 8082, 8083), cada um com o seu próprio armazenamento isolado (em memória ou base de dados independente).
2. **Reencaminhamento Peer-to-Peer (HTTP GET Forwarding)**: Quando um pedido `GET` chega a uma instância e a informação não existe localmente, a instância pesquisa nos seus pares (*peers*), agrega as respostas e devolve o resultado final ao cliente.
3. **Prevenção de Loops e Erros**: O mecanismo de *forwarding* evita encaminhamentos circulares (excluindo a própria instância da lista de destinos) e trata graciosamente *timeouts* e erros de rede.
4. **Segurança em Camadas**: Proteção dos dados operacionais sensíveis através de autenticação entre serviços, encriptação de dados em trânsito (TLS 1.3), controlo de acessos e registos de auditoria (*audit logging*).

---

## 5\. Divisão de Trabalho — Projeto SIDIS (2026/2027)

Para assegurar uma cobertura equilibrada das funcionalidades de domínio, dos requisitos de segurança e da infraestrutura distribuída, as responsabilidades da equipa foram estruturadas conforme detalhado a seguir:

### Resumo das Atribuições por Membro da Equipa

#### **@Martim — Serviço 1 &amp; Segurança**

* **Serviço de Domínio**: Desenvolvimento integral do serviço de **Aeronaves e Manutenção** (*Aircraft &amp; Maintenance Service*).
* **Funcionalidades Primárias**: Gestão de modelos/instâncias de aeronaves, controlo de estados operacionais, criação e consulta de planos e registos de manutenção.
* **Módulo de Segurança**: Implementação da **autenticação entre serviços** (*inter-service authentication*), garantindo a verificação de identidade no acesso aos endpoints dos serviços.

#### **@Pedro Guilherme — Serviço 2 &amp; Segurança**

* **Serviço de Domínio**: Desenvolvimento integral do serviço de **Aeroportos e Rotas** (*Airports &amp; Routes Service*).
* **Funcionalidades Primárias**: Registo de aeroportos e pistas, criação e histórico de rotas, verificação de compatibilidade técnica entre infraestrutura de aeroporto e aeronave.
* **Módulo de Segurança**: Implementação da **encriptação de dados em trânsito** (comunicação segura HTTP/TLS) e controlo de acessos (*access control*).

#### **@Ricki — Serviço 3 &amp; Segurança**

* **Serviço de Domínio**: Desenvolvimento integral do serviço de **Operações de Voo** (*Flight Operations Service*).
* **Funcionalidades Primárias**: Agendamento de voos, associação de aeronaves a rotas respeitando limites de autonomia, verificação inter-serviços de disponibilidade.
* **Módulo de Segurança**: Criação do sistema centralizado/distribuído de **registos de auditoria** (*audit logging*), assegurando a rastreabilidade e integridade das operações efetuadas.

#### **@Teixeira — Infraestrutura Distribuída &amp; Coordenação**

* **Apoio Técnico**: Suporte transversal aos restantes membros da equipa na integração e desenvolvimento dos serviços de domínio.
* **Mecanismo Peer-to-Peer**: Desenvolvimento do motor distribuído de **replicação, reencaminhamento de pesquisas (HTTP GET Forwarding) e agregação de respostas** entre clones de serviços.
* **Documentação e Deployment**: Elaboração da documentação técnica e arquitetural (justificação das decisões de design, modelos de resiliência e consistência eventual) e criação das instruções de instalação e execução (*deployment*).
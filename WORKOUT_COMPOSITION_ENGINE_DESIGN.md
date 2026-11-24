# Especificação de Arquitetura: Workout Composition Engine (WCE)
**Documento Ref:** ARCH-WCE-001  
**Status:** PROPOSTO & REVISADO  
**Autor:** Principal Software Architect, Staff Strength Coach, Recommendation Systems Lead  

---

## 1. Introdução e Contexto Arquitetural

Com o advento da **Training Experience Layer (TEL)** e do **Exercise Knowledge Graph (EKG)**, o pipeline de recomendação do projeto Max Performance tornou-se extremamente modular. No entanto, identificou-se um "vazio estrutural" entre o cálculo de variáveis puramente fisiológicas (volume total de séries, intensidade e progresso) e o refinamento estético/espacial de experiência.

Para preencher essa lacuna, introduzimos o **Workout Composition Engine (WCE)**.

O WCE atua como o **Core Choreographer** do treino. Ele pega a matemática crua de volume e fadiga do Recommendation Engine e a converte em uma composição de treino fluida, natural, balanceada biomecanicamente e estruturalmente impecável.

### O Pipeline de Execução Atualizado:

```
+-----------------------------------+
|       1. DECISION ENGINE          | -> Avalia prontidão biológica e caps de segurança
+-----------------+-----------------+
                  |
                  v
+-----------------------------------+
|   2. CORE RECOMMENDATION ENGINE   | -> Calcula volumes alvos (séries) e intensidade (RPE)
+-----------------+-----------------+
                  | [Metas Fisiológicas Cruas]
                  v
+-----------------------------------+
| 3. WORKOUT COMPOSITION ENGINE(WCE)| -> Determina quantidade de exercícios, distribuição
|                                   |    de séries, padrões biomecânicos e fluxo de esforço
+-----------------+-----------------+
                  | [Skeleton de Composição do Treino]
                  v
+-----------------------------------+
| 4. TRAINING EXPERIENCE LAYER (TEL)| -> Adapta ao ambiente real (Academia vs Home),
|                                   |    calcula o Perceived Completeness Score (PCS)
+-----------------+-----------------+
                  | [Skeleton Refinado de Experiência]
                  v
+-----------------------------------+
| 5. EXERCISE KNOWLEDGE GRAPH (EKG) | -> Mapeia os padrões abstratos para IDs do banco de dados
+-----------------+-----------------+
                  | [Lista de Exercícios Concretos]
                  v
+-----------------------------------+
|       6. WORKOUT BUILDER          | -> Hidrata pesos alvos, repetições e gera o payload
+-----------------------------------+
```

---

## 2. Princípios de Design do WCE

O WCE é regido por três leis fundamentais:
1.  **Fidelidade Fisiológica Estrita:** O WCE nunca altera o volume total de séries, o RPE alvo ou as intensidades prescritas pelo Recommendation Engine. Ele apenas distribui e organiza esse volume.
2.  **Preservação Biomecânica:** A seleção de padrões de movimento deve maximizar o recrutamento de diferentes vetores de força e perfis de resistência, sem causar estresse repetitivo no mesmo arco articular.
3.  **Fluxo de Esforço Natural:** O sequenciamento de exercícios segue a curva de fadiga do sistema nervoso central (SNC) e periférico, movendo-se do mais complexo/instável para o mais estável/isolado.

---

## 3. Heurísticas e Mecanismo de Decisão do WCE

### 3.1. Quantidade Ideal de Exercícios Adaptativa
Em vez de usar limites estáticos (como "máximo de 4 exercícios para 45 minutos"), o WCE utiliza um modelo de cálculo dinâmico baseado em múltiplas variáveis de entrada.

#### Variáveis de Entrada:
*   $T_{\text{avail}}$: Tempo disponível em minutos.
*   $V_{\text{target}}$: Volume total de séries prescrito pelo Recommendation Engine para a sessão.
*   $G$: Objetivo (Força, Hipertrofia, Resistência).
*   $E$: Nível de Experiência do Usuário (Iniciante, Intermediário, Avançado).
*   $S_{\text{split}}$: Tipo de divisão de treino (Full Body, Upper/Lower, Push/Pull/Legs).
*   $F_{\text{week}}$: Frequência semanal do grupo muscular.

#### Modelo de Resolução da Quantidade de Exercícios ($N_e$):

O WCE calcula o número ideal de exercícios através da função:

$$N_e = \text{Clamp}\left( \frac{T_{\text{avail}} - T_{\text{setup}}}{\overline{T}_{\text{rest}}(G, E) + T_{\text{set}}}, N_{\text{min}}(G, S_{\text{split}}), N_{\text{max}}(G, S_{\text{split}}, E) \right)$$

Onde as faixas recomendadas e os tempos médios de descanso ($\overline{T}_{\text{rest}}$) são extraídos de uma matriz dinâmica:

| Objetivo ($G$) | Tempo de Descanso Médio ($\overline{T}_{\text{rest}}$) | $N_{\text{min}}$ (Full Body / Split) | $N_{\text{max}}$ (Iniciante / Avançado) |
| :--- | :--- | :--- | :--- |
| **Força (Powerlifting)** | $180s - 300s$ | 2 / 3 | 3 / 5 |
| **Hipertrofia** | $90s - 120s$ | 3 / 4 | 4 / 6 |
| **Resistência / Densidade**| $30s - 45s$ | 4 / 5 | 5 / 8 |

---

### 3.2. Distribuição Inteligente e Fisiológica de Séries
A distribuição de séries em musculação tradicional segue padrões de fadiga acumulada. Distribuir séries de forma perfeitamente plana (ex: $4+4+4$) em treinos volumosos de alta intensidade pode gerar fadiga precoce.

O WCE implementa quatro perfis de distribuição:

1.  **Modelo Pirâmide Decrescente de Séries (Padrão de Força/Hipertrofia Avançada):**
    *   *Estrutura:* $5 + 4 + 3$ ou $4 + 3 + 3$.
    *   *Fisiologia:* Aloca o maior volume e energia no primeiro movimento (composto/pesado), reduzindo o volume de séries nos exercícios subsequentes conforme o SNC se esgota.
2.  **Modelo Equitativo Concentrado (Iniciantes/Intermediários):**
    *   *Estrutura:* $3 + 3 + 3$.
    *   *Fisiologia:* Excelente para consolidação de padrão motor em iniciantes que não conseguem atingir exaustão profunda no primeiro exercício.
3.  **Modelo Carga de Pico Invertida (Volume Acumulado):**
    *   *Estrutura:* $3 + 4 + 3$.
    *   *Fisiologia:* Usado para isolar e dar foco especial a uma porção anatômica intermediária (ex: focar no feixe inclinado do peito no segundo exercício da sessão).

---

### 3.3. Cobertura Biomecânica e Vetores de Força
Para evitar treinos redundantes (ex: Supino Reto Barra + Supino Reto Halteres + Flexão), o WCE categoriza e rastreia os **Vetores Biomecânicos** e os **Perfis de Resistência** (curva ascendente, descendente ou plana).

#### Matriz de Cobertura Biomecânica (Exemplo: Peitoral)
Quando o Recommendation Engine solicita volume para o Peitoral, o WCE preenche os slots gerados na etapa 3.1 com a seguinte regra de diversificação:

```
[Volume Alvo: Peito]
      |
      +---> Slot 1: Padrão "Horizontal Press" (Foco: Alongamento / Torque de Base)
      |
      +---> Slot 2: Padrão "Incline Press" (Foco: Feixe Clavicular / Angulação Superior)
      |
      +---> Slot 3: Padrão "Horizontal Fly / Adduction" (Foco: Contração Máxima / Pico de Tensão)
```

Essa matriz garante que nenhuma sessão possua exercícios repetidos com a mesma curva de força ou o mesmo vetor de movimento.

---

### 3.4. Diversidade de Implementos e Classes de Equipamento
O WCE define regras para garantir a correta distribuição de implementos para evitar sobrecarga articular ou monotonia de setup.

#### Regra de Distribuição de Implementos:
Se $N_e \ge 3$:
*   Não mais de **50%** dos exercícios podem compartilhar o mesmo tipo de implemento (ex: Barra livre).
*   A sessão deve mesclar classes com diferentes demandas de estabilização:
    1.  *Peso Livre (Barra/Halteres):* Alta demanda de estabilização neural.
    2.  *Cabos (Polias):* Tensão constante ao longo de todo o arco do movimento.
    3.  *Máquinas Pivotadas:* Estabilidade máxima, isolamento muscular e baixo risco de lesão sob alta fadiga.

---

### 3.5. Fluxo de Esforço e Sequenciamento Neural (Neuromuscular Flow)
O WCE organiza os exercícios selecionados de forma sequencial com base no seu **Índice de Demanda Neuromuscular (IDN)**.

```
+----------------------------------------------------------------------------+
|                       SEQUENCIAMENTO NEUROMUSCULAR (IDN)                    |
+----------------------------------------------------------------------------+
  [IDN: Alto]                                                    [IDN: Baixo]
  Multiarticular Livre ---> Multiarticular Máquina ---> Isolador ---> Finisher
  (Ex: Agachamento Barra)   (Ex: Leg Press)             (Ex: Extensora) (Metabólico)
```

#### Regras de Sequenciamento:
1.  **Compostos Primeiro:** Exercícios com maior recrutamento de massa muscular e carga axial sempre iniciam a sessão.
2.  **Unilaterais no Meio:** Exercícios unilaterais que demandam foco compensatório de estabilização lateral devem ser executados antes que a fadiga sistêmica comprometa a postura.
3.  **Isoladores no Final:** Exercícios isolados e monoarticulares entram para acumular estresse metabólico quando o SNC já está exaurido para realizar empurres/puxadas pesadas de forma segura.

---

## 4. Pseudo-Algoritmo do WCE

```python
def compose_workout_structure(physiological_targets, user_context):
    """
    Entradas:
      - physiological_targets: { target_muscles: List[Muscle], total_sets: Int, target_rpe: Int, goal: String }
      - user_context: { time_available: Int, experience: String, weekly_frequency: Int, split_type: String }
    Saída:
      - session_composition: List[AbstractExerciseSlot]
    """
    goal = physiological_targets.goal
    experience = user_context.experience
    total_sets = physiological_targets.total_sets
    time_avail = user_context.time_available
    
    # 1. Determinar quantidade ideal de exercícios (Heurística 3.1)
    N_e = calculate_adaptive_exercise_count(time_avail, total_sets, goal, experience, user_context.split_type)
    
    # 2. Gerar Distribuição de Séries (Heurística 3.2)
    sets_distribution = distribute_sets_to_slots(total_sets, N_e, goal, experience)
    
    # 3. Mapear Vetores Biomecânicos necessários (Heurística 3.3)
    required_vectors = resolve_biomechanical_vectors(physiological_targets.target_muscles, N_e)
    
    # 4. Alocar Classes de Implementos para evitar monotonia (Heurística 3.4)
    implement_classes = assign_balanced_implements(N_e, goal)
    
    # 5. Criar Slots Abstratos de Sessão
    slots = []
    for idx in range(N_e):
        slot = AbstractExerciseSlot(
            slot_index=idx,
            target_sets=sets_distribution[idx],
            movement_vector=required_vectors[idx],
            implement_class=implement_classes[idx],
            target_rep_range=resolve_rep_range_for_goal(goal, idx, N_e),
            target_rpe=physiological_targets.target_rpe
        )
        slots.append(slot)
        
    # 6. Organizar Fluxo por Demanda Neuromuscular (Heurística 3.5)
    sequenced_session = sequence_slots_by_neuromuscular_demand(slots)
    
    return sequenced_session
```

---

## 5. Exemplos Práticos Comparativos

### Cenário 1: Avançado, Hipertrofia, Academia Completa, 60 Minutos
*   **Recommendation Engine:** Aloca Peito como alvo principal e prescreve **12 séries totais**, com RPE alvo de 8-9.

#### Decisão do WCE:
1.  **Quantidade de Exercícios:** Determina que o ideal são **4 exercícios** de forma a cobrir toda a anatomia do peitoral.
2.  **Distribuição de Séries:** Modelo Pirâmide Decrescente para priorizar energia inicial ($4 + 3 + 3 + 2$ séries).
3.  **Padrões Biomecânicos:**
    *   Slot 1 (4 séries): Press Horizontal Composto (Foco: Sternal)
    *   Slot 2 (3 séries): Press Inclinado Composto (Foco: Clavicular)
    *   Slot 3 (3 séries): Adutor Horizontal Isolado (Foco: Encurtamento máximo)
    *   Slot 4 (2 séries): Press Declinado ou Dips (Foco: Costal/Inferior)
4.  **Implementos:** Mescla de Barra (Slot 1), Haltere (Slot 2), Cabo (Slot 3) e Máquina/Articulado (Slot 4) -> Monotony Score perfeito de $0.25$.
5.  **Sequenciamento:** Organizado do composto com barra pesada livre até a polia metabólica e mergulho no final.

*   *Diferença para o modelo antigo:* Em vez de apenas 3 exercícios planos, o WCE gera uma jornada com 4 movimentos estrategicamente divididos por volume e fadiga, fazendo o praticante avançado sentir que o treino foi montado com capricho absoluto.

---

## 6. Casos Extremos e Resolução de Conflitos

### Conflito 1: Volume Muito Alto com Tempo Muito Baixo
*   *Cenário:* O Recommendation Engine prescreve **16 séries** devido a uma necessidade de supercompensação, mas o usuário possui apenas **30 minutos**.
*   *Resolução do WCE:* O WCE reduz a quantidade de exercícios ($N_e$) para o limite mínimo estrutural (ex: 2 exercícios) e redistribui as séries em um modelo de super-sets ou séries rest-pause ($8+8$). O volume total é mantido, mas a densidade do treino é maximizada de forma inteligente para caber no tempo do usuário.

### Conflito 2: Divisão Full Body com Alto Volume Geral
*   *Cenário:* Treino Full Body onde são prescritos volumes para Pernas, Costas e Peito no mesmo dia, totalizando **18 séries**.
*   *Resolução do WCE:* O WCE cria uma estrutura do tipo **Antagonist Alternated Flow**. Ele sequencia os exercícios alternando os agrupamentos musculares (ex: Push vertical seguido de Pull vertical), permitindo que a fadiga neuromuscular local se dissipe enquanto o outro agrupamento trabalha, evitando a queda drástica de rendimento na segunda metade do treino.

# MANUAL MESTRE — MAX PERFORMANCE (MAX GYM & MAX RUN)

**Documento de Referência Técnica, Funcional, Visual e Fisiológica**  
**Versão:** 2026.10 (Build Híbrido Unificado)

---

## 1. O Que o Aplicativo é Capaz de Fazer (Visão Geral de Capacidades)

O **MAX PERFORMANCE** é uma plataforma híbrida de treinamento de alta performance que integra **Musculação Científica (`MAX GYM`)**, **Corrida Guiada por GPS e Telemetria (`MAX RUN`)**, **Fisiologia & Recuperação Muscular**, **Nutrição Esportiva** e **Análise de Dados** em uma única arquitetura nativa Android (Jetpack Compose).

### 1.1. Módulo MAX GYM (Musculação & Força)
- **Motor de Composição de Treino (`TrainingEngine001` & `WCE`):**
  - Sequenciamento fisiológico automático baseado no **Índice de Demanda Neuromuscular (IDN)**: Multiarticulares Livres $\rightarrow$ Multiarticulares Guiados/Máquinas $\rightarrow$ Unilaterais $\rightarrow$ Isoladores $\rightarrow$ Corretivos/Core.
  - **Regra 9 (Inteligência de Pernas):** Ordenação biomecânica em 8 Tiers (Agachamento Livre/Frontal $\rightarrow$ Leg Press/Hack $\rightarrow$ Afundo/Búlgaro $\rightarrow$ Stiff/Terra/Hip Thrust $\rightarrow$ Mesa/Cadeira Flexora $\rightarrow$ Cadeira Extensora $\rightarrow$ Panturrilha $\rightarrow$ Abdômen).
  - **Alternância Inteligente de Equipamentos:** Evita 3 exercícios consecutivos no mesmo implemento dentro do mesmo grupamento muscular, otimizando o fluxo de salão (*Gym Traffic Score*).
- **Calibração Real de Equipamentos de Academia (Modal `[ CARGAS ]`):**
  - **6 Categorias Reais:** `Halteres`, `Polia / Torre de Placas`, `Máquina Articulada / Anilhas`, `Barra / Smith`, `Leg Press (Anilhas)` e `Peso Corporal`.
  - **Modo Torre em Libras (`LBS` $\rightarrow$ `KG`):** Suporte nativo para máquinas importadas (ex.: Smart Fit) com saltos de placa em libras (`+5`, `+10`, `+15`, `+20`, `+25 lbs`). Os botões `+` e `-` avançam exatamente de placa em placa em libras, exibindo o peso real convertido em quilogramas e a referência em libras: `45,4 kg (100 lbs)`.
  - **Tara / Peso Base da Máquina:** Cadastro do peso inicial do carrinho/braço articulado (`0`, `10`, `15`, `20`, `25 kg`) e modo *"Treinar apenas com o peso da máquina (Sem Anilhas)"*.
- **Player de Execução (*Flow State Player*):**
  - Registro série a série (`S1`, `S2`, `S3`...) com cálculo dinâmico de carga por fadiga acumulada na sessão, cronômetro de descanso inteligente com alarme sonoro/vibração em background e auto-scroll suave para a série ativa.
  - **Pílula de Batimentos Cardíacos (`♥ BPM`):** Monitoramento via Bluetooth Low Energy (BLE) em tempo real durante o treino de força e corrida.
  - **Coach Fisiologista Integrado:** Análise em tempo real da sessão, sugerindo progressão dupla, manutenção ou deload com base nas repetições concluídas e RPE.

### 1.2. Módulo MAX RUN (Corrida & Cardio Outdoor/Indoor)
- **HUD de Corrida em Tempo Real:**
  - Rastreamento GPS de alta precisão com filtro de suavização (Kalman/velocidade), cálculo de **Pace Atual e Médio (`min/km`)**, **Distância (`km`)**, **Cadência (`SPM` via acelerômetro/sensor de passos)**, **Calorias** e **Zonas de Frequência Cardíaca (`Z1` a `Z5`)**.
- **Treinos Intervalados e Guiados por Áudio (`MaxSoundManager`):**
  - Fases estruturadas de **Aquecimento**, **Corrida**, **Caminhada**, **Tiro/Sprint** e **Desaquecimento**, com avisos sonoros e visuais na transição de cada bloco.
- **Mapa Interativo por Sprints (`AnimatedRouteMapView`):**
  - Traçado colorido por fase e velocidade, marcação automática do **Melhor Sprint (PR)**, replay animado do percurso e modal de tela cheia respeitando as barras do sistema Android (`systemBarsPadding` / `navigationBarsPadding`).

### 1.3. Modo Híbrido (Sincronia Inteligente Musculação $\leftrightarrow$ Corrida)
- **Intercalação Híbrida Automática (Janela de 48h pós-Full Body):**
  - Identifica automaticamente quando o usuário realizou um treino Full Body (ou protocolo estruturado) no dia anterior e converte a recomendação principal de **Hoje** em **Dia de Corrida & Recuperação**, protegendo o Sistema Nervoso Central (SNC) e promovendo supercompensação.
- **Cronograma Semanal Fixo (Segunda a Domingo):**
  - Exibe a semana corrente completa com histórico real verificado (`✓` verde nos dias concluídos com acesso ao resumo do treino realizado) e projeção futura intercalando `GYM` e `RUN`.

### 1.4. Corpo, Fadiga, Diário, Nutrição e Ecossistema
- **Mapa Anatômico & Fadiga (`RecoveryAndTrophies`):** Monitoramento de recuperação percentual (`0%` a `100%`) e horas restantes para os 8 grupamentos canônicos, além de leitura de bioimpedância (`BioimpedanceParser`).
- **Nutrição Esportiva:** Cálculo de Taxa Metabólica Basal (Mifflin-St Jeor), Gasto Energético Diário Total (TDEE) e divisão de macronutrientes (Proteínas, Carboidratos, Gorduras) alinhados ao objetivo (Cutting, Manutenção, Bulking).
- **Share Cards & Exportação:** Geração nativa de cards visuais de compartilhamento (`GymShareCardGenerator`, `SessionReportBitmapGenerator`), captura longa de tela e exportação CSV/JSON de telemetria.

---

## 2. As Modificações Mais Recentes

1. **Unificação da Identidade Visual Dark + Azul/Ciano (`#00B0FF` / `#00E5FF`):**
   - Substituição completa do tema legado Laranja/Âmbar no Player de Musculação, botões `REGISTRAR SÉRIE`, linha da série ativa (`S1`), badges de grupo muscular, lupa da miniatura e modais `[ CARGAS ]`, `[ DESCANSO ]` e `[ ANÁLISES ]` pela paleta oficial **Dark Neutro (`#0D0F14`) + Azul/Ciano Vibrante (`#00B0FF` / `#00E5FF`)**.
   - Preservação rigorosa das cores semânticas fisiológicas: **Vermelho/Coral (`#FF4444` / `#FF5252`)** exclusivo para batimentos cardíacos (`♥ BPM`) e alertas críticos, e **Verde (`#00E676` / `#34C759`)** para séries concluídas e status 100% recuperado.
2. **Escala Proporcional Automática Global (`LocalDensity` em `MainActivity.kt`):**
   - Implementação de adaptação responsiva global baseada na largura de referência do emulador (`411dp`), garantindo que celulares físicos compactos ou largos exibam exatamente o mesmo alinhamento visual, com `fontScale = 1.0f` travado para impedir quebras de pílulas e botões em duas linhas.
3. **Polimento Visual e Compactação Vertical do Hero Card Híbrido (`Screens.kt` & `WeeklyScheduleComponents.kt`):**
   - **Remoção de Duplo Ícone:** O botão principal `"IR PARA CORRIDA DE HOJE"` agora exibe exclusivamente o ícone vetorial `Icons.Default.DirectionsRun` à esquerda do texto, sem emoji duplicado.
   - **Prevenção de Truncamento na Pílula Superior:** A badge superior esquerda foi enxugada para `"INTERCALAÇÃO HÍBRIDA • CORRIDA"` com `Modifier.weight(1f, fill = false)`, garantindo que a pílula da direita (`"Plano Fixado"`, `"100% Pronto"`, `"Personalizado"`) apareça sempre 100% inteira sem cortar para `"P..."`.
   - **Compactação de Altura na Home:** Redução da área de capa do Hero Card de `200.dp` para `165.dp`, compactação dos paddings internos e ajuste das pílulas do calendário para `78.dp`, garantindo que o bloco **`CRONOGRAMA 7 DIAS (SEG A DOM)`** fique 100% visível acima da barra de navegação inferior sem precisar rolar a tela.
4. **Limpeza da Tabela de Séries e Modal `[ CARGAS ]` Completo:**
   - Cabeçalho da tabela de séries restaurado com 4 colunas limpas (`SÉRIE | CARGA (KG) | REPETIÇÕES | CHECK`).
   - Subtítulo discreto abaixo do nome do exercício informando a calibração ativa (ex.: `Polia / Torre de Placas • Torre em Libras (+15 lbs ≈ +6,8 kg)`).
5. **Correção de System Bars e Legenda no Mapa Interativo por Sprints (`AnimatedRouteMapView.kt`):**
   - Aplicação de `statusBarsPadding()` e `navigationBarsPadding()` no modal de tela cheia do mapa para que os 3 botões inferiores fiquem totalmente acima da barra de navegação do Android, além de espaçamento individualizado na pílula de legenda (`Aquec. | Caminhada | Corrida | Melhor Sprint (PR)`).

---

## 3. As Regras de Ouro (Golden Rules)

1. **Regra da Sessão Única Ativa (*Single Active Session Lock*):**
   - Nunca permitir que duas sessões concorrentes corram em paralelo sem resolução explícita. Se houver um treino em andamento, o app exibe o diálogo de retomada ou descarte seguro antes de iniciar outro.
2. **Regra de Preservação de Séries Concluídas:**
   - Ao reordenar exercícios, recalcular fadiga da sessão (`TrainingEngine001.calculateLoadsForSession`) ou alterar a calibração de carga no modal `[ CARGAS ]`, **séries já marcadas como concluídas (`set.completed == true`) jamais são alteradas retroativamente**.
3. **Regra da Janela Fisiológica de 48h (Sincronia Hero Card $\leftrightarrow$ Cronograma 7 Dias):**
   - A recomendação exibida no Hero Card (`Index 0`) e o dia `"HOJE"` na barra `"CRONOGRAMA 7 DIAS (SEG A DOM)"` devem estar **sempre 100% sincronizados em tempo real**. No modo Híbrido, treinos Full Body exigem janela de 48h de recuperação do SNC, intercalando automaticamente com `DIA DE CORRIDA & RECUPERAÇÃO` no dia seguinte.
4. **Regra de Invariância de Escala e Textos Sem Quebra (`Single-Line Pill Rule`):**
   - Todos os botões lado a lado (`Row`) devem usar `Modifier.height(IntrinsicSize.Min)` no container e `Modifier.fillMaxHeight()` nos botões filhos, com `maxLines = 1`, `softWrap = false` e `TextOverflow.Ellipsis` em pílulas (`Coach`, `Cargas`, `Descanso`, `Análises`, `Trocar treino`, `Iniciar treino`).
5. **Regra de Cálculo de Dias do Calendário (`calculateCalendarDaysBetween`):**
   - Sempre comparar datas zerando `HOUR_OF_DAY`, `MINUTE`, `SECOND` e `MILLISECOND` no `Calendar` local. Nunca usar divisão inteira de horas (`diffMs / 24h`) para determinar se um treino foi "hoje" ou "ontem" (evitando que um treino feito ontem às 20h seja lido como "hoje" às 10h da manhã seguinte).
6. **Regra de Cores Semânticas:**
   - **Azul/Ciano (`#00B0FF` / `#00E5FF`):** Ação principal, foco ativo, navegação e identidade da marca.
   - **Verde (`#00E676` / `#34C759`):** Série concluída, dia de treino cumprido (`✓`) e músculo 100% recuperado.
   - **Vermelho (`#FF4444` / `#FF5252`):** Exclusivo para frequência cardíaca (`♥ BPM`), falha muscular/dor ou ação destrutiva.

---

## 4. Fontes, Tamanhos, Dimensões e Paleta de Cores

### 4.1. Família Tipográfica e Escala Oficial (`Type.kt` & `Screens.kt`)
- **Família Principal:** `FontFamily.SansSerif` (Roboto / Inter System Sans-Serif com pesos atléticos de alto contraste) e `FontFamily.Monospace` para cronômetros e telemetria numérica.
- **Travamento de Escala de Fonte:** `fontScale = 1.0f` fixo via `CompositionLocalProvider(LocalDensity)` para imunidade contra distorções de fonte grande do sistema Android.

| Elemento / Estilo | Tamanho (`sp`) | Line Height (`sp`) | Peso (`FontWeight`) | Letter Spacing | Uso Principal |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **`displayLarge`** | `32.sp` | `38.sp` | `Black (900)` | `-0.5.sp` | Métricas centrais de HUD, Pace/Tempo principal |
| **`displayMedium`** | `24.sp` | `30.sp` | `Black (900)` | `0.sp` | Títulos de modais e destaques numéricos |
| **Saudação Home** | `23.sp` | `28.sp` | `Black (900)` | `-0.3.sp` | Cabeçalho `"Olá, Atleta"` na Home |
| **`titleLarge` / Hero Title** | `20.sp`–`22.sp` | `26.sp` | `Bold` / `Black` | `0.sp` | Nome do treino no Hero Card e cabeçalhos de tela |
| **`bodyLarge`** | `16.sp` | `24.sp` | `Normal (400)` | `0.5.sp` | Textos de leitura principal e instruções |
| **`bodyMedium` / Botões CTA** | `13.sp`–`14.sp` | `20.sp` | `Medium` / `ExtraBold` | `0.25.sp`–`0.6.sp` | Botões `"INICIAR TREINO"`, `"IR PARA CORRIDA DE HOJE"` |
| **`labelMedium` / Subtítulos**| `12.sp` | `16.sp` | `Bold (700)` | `0.5.sp` | Cabeçalhos de seções (`CRONOGRAMA 7 DIAS`), tags |
| **`labelSmall` / Legendas** | `11.sp` | `16.sp` | `Medium` / `Bold` | `0.4.sp`–`0.5.sp` | Legenda do mapa de sprints, subtítulo de equipamento |
| **Badges Compactas** | `9.5.sp`–`10.sp`| `12.sp` | `ExtraBold (800)` | `0.4.sp` | Badge superior do Hero Card e status pills |

### 4.2. Dimensões Estruturais Padronizadas (`dp`)
- **Largura Base de Referência (Smartphones):** `411.dp` (Tablets: `widthIn(max = 840.dp)`).
- **Altura da Capa do Hero Card:** `165.dp` (compactado para caber o Cronograma 7 Dias acima da dobra).
- **Altura do Botão Principal CTA do Hero Card:** `42.dp` (com `RoundedCornerShape(12.dp)`).
- **Altura do Card de Dia no Cronograma Semanal (`CalendarDayPillCard`):** `78.dp`.
- **Alvo Mínimo de Toque (Acessibilidade):** `48.dp x 48.dp`.

### 4.3. Tokens de Cores (`Color.kt` & `RunColors.kt`)
- **Background Geral:** `#0D0F14` $\rightarrow$ `#11151D` (`BackgroundStart` / `BackgroundEnd`)
- **Superfície de Cards:** `#161922` (`GraphiteSurface` / `Surface`)
- **Containers Secundários / Pílulas:** `#1E2430` (`SecondaryElement` / `WarmPillContainer`)
- **Bordas Slate:** `#262B38` (`BorderColor`) + Borda Especular Ciano (`#00E5FF` com `alpha = 0.28f` $\rightarrow$ `0.04f`)
- **Acento Primário (Azul Oficial):** `#00B0FF` (`Primary` / `MaxOrange`)
- **Acento Iluminado (Ciano Neon):** `#00E5FF` (`Secondary` / `CopperRimLight`)
- **Sucesso / Concluído:** `#34C759` e `#00E676` (`Success`)
- **Erro / Batimentos (`♥ BPM`):** `#FF4444` e `#FF5252` (`Error` / `alert`)

---

## 5. Fórmulas Matemáticas e Fisiológicas Principais

### 5.1. Escala Responsiva Global de Densidade (`MainActivity.kt`)
Para garantir fidelidade visual idêntica entre qualquer smartphone físico (`screenWidthDp < 600`) e o emulador de referência (`411dp`):

$$\text{targetScale} = \text{ clamp}\left(\frac{\text{screenWidthDp}}{411.0},\ 0.82,\ 1.15\right)$$

$$\text{adaptedDensity} = \text{Density}\left(\text{density} = \text{currentDensity} \times \text{targetScale},\ \text{fontScale} = 1.0\right)$$

---

### 5.2. Conversão de Torres em Libras (`LBS` $\rightarrow$ `KG`) e Calibração de Carga (`WeightCalculator.kt`)
- **Fator Oficial de Conversão Internacional:**
  $$1\text{ lb} = 0.45359237\text{ kg}$$
- **Passo Efetivo em Quilogramas no Modo Libras:**
  $$\Delta W_{\text{kg}} = \text{clamp}\left(\Delta W_{\text{lbs}} \times 0.45359237,\ 0.25,\ 50.0\right)$$
- **Reversão para Exibição de Placa em Libras na Série (`S1, S2, S3`):**
  $$W_{\text{lbs}} = \text{round}\left(\frac{W_{\text{kg}}}{0.45359237}\right)$$
- **Regra de Progressão de Halteres Padrão:**
  $$\Delta W_{\text{halter}} = \begin{cases} 1.0\text{ kg}, & \text{se } W_{\text{atual}} < 10\text{ kg} \\ 2.0\text{ kg (pares)}, & \text{se } W_{\text{atual}} \ge 10\text{ kg} \end{cases}$$

---

### 5.3. Carga Inicial Biométrica Individualizada (`WeightCalculator.calculateBiometricStartingWeight`)
Quando o usuário realiza um exercício pela primeira vez, a carga inicial sugerida $W_{\text{start}}$ é calculada a partir do peso de referência biomecânico do exercício ($W_{\text{ref}}$) modulado por 4 fatores biométricos:

$$W_{\text{start}} = \text{roundToEquipment}\left(W_{\text{ref}} \times F_{\text{peso}} \times F_{\text{sexo}} \times F_{\text{idade}} \times F_{\text{exp}}\right)$$

Onde:
1. **Fator de Peso Corporal ($F_{\text{peso}}$):** Com $P_{\text{clamped}} = \text{clamp}(\text{Peso}_{\text{kg}}, 40, 150)$:
   $$F_{\text{peso}} = 0.45 + 0.55 \times \left(\frac{P_{\text{clamped}}}{75.0}\right)$$
2. **Fator Biológico/Sexo ($F_{\text{sexo}}$):**
   $$F_{\text{sexo}} = \begin{cases} 0.72, & \text{Feminino (Membros Inferiores)} \\ 0.52, & \text{Feminino (Membros Superiores)} \\ 1.00, & \text{Masculino} \end{cases}$$
3. **Fator Etário ($F_{\text{idade}}$):**
   $$F_{\text{idade}} = \begin{cases} 1.00, & \text{se Idade} \le 45 \\ \text{clamp}\big(1.0 - (\text{Idade} - 45) \times 0.006,\ 0.75,\ 1.0\big), & \text{se Idade} > 45 \end{cases}$$
4. **Fator de Experiência ($F_{\text{exp}}$):**
   - Iniciante: $0.70$
   - Intermediário: $1.00$
   - Avançado / Elite: $1.22$

---

### 5.4. Atenuação de Carga por Fadiga Acumulada na Sessão (`TrainingEngine001.calculateLoadsForSession`)
Conforme o atleta avança na sessão, cada exercício principal concluído acumula fadiga no SNC, ajustando a carga sugerida dos exercícios subsequentes:

$$\text{Fadiga}_{\text{ex}} = \begin{cases} 1.6, & \text{se Exercício Composto (Agachamento, Supino, Terra, Remada, Leg Press, etc.)} \\ 1.0, & \text{se Exercício Isolador} \end{cases}$$

$$M_{\text{posição}} = \text{clamp}\left(1.0 - 0.04 \times \sum \text{Fadiga}_{\text{precedentes}},\ 0.75,\ 1.00\right)$$

$$W_{\text{ajustado}} = \text{roundToStep}\left(W_{\text{base}} \times M_{\text{posição}},\ \text{step}_{\text{equipamento}}\right)$$

---

### 5.5. Estimativa de 1RM (Uma Repetição Máxima) e Progressão Linear/Dupla
- **Fórmula de Epley (Padrão Principal de Força):**
  $$\text{1RM}_{\text{Epley}} = W \times \left(1 + \frac{\text{Reps}}{30.0}\right)$$
- **Fórmula de Brzycki (Validação em Faixas Submáximas $\le 10$ reps):**
  $$\text{1RM}_{\text{Brzycki}} = \frac{W}{1.0278 - 0.0278 \times \text{Reps}}$$
- **Sobrecarga Progressiva (`calculateProgressedWeight`):**
  $$W_{\text{novo}} = \begin{cases} W_{\text{base}} + 2.0\text{ kg}, & \text{Halteres} \\ W_{\text{base}} + 5.0\text{ kg}, & \text{Compostos de Membros Inferiores} \\ W_{\text{base}} + 2.5\text{ kg}, & \text{Demais Implementos (Barra/Máquina/Polia)} \\ W_{\text{base}} \times 0.90, & \text{Em Semana de Deload (-10\%)} \end{cases}$$

---

### 5.6. Recuperação Muscular e Janela Crítica de Supercompensação (`RecoveryAndTrophies.kt` & `TrainingEngine001.kt`)
- **Tempo Total de Recuperação ($T_{\text{rec}}$) por Porte Muscular:**
  - **Grandes Grupamentos (`Peito`, `Costas`, `Pernas`):** $T_{\text{rec}} = 72\text{ horas}$
  - **Pequenos Grupamentos / Sinergistas (`Ombros`, `Bíceps`, `Tríceps`, `Trapézio`, `Abdominais`):** $T_{\text{rec}} = 24\text{ horas}$
- **Percentual de Recuperação Atual ($R_{\%}$):**
  $$R_{\%} = \text{clamp}\left(\frac{t_{\text{decorrido (h)}}}{T_{\text{rec}}},\ 0.0,\ 1.0\right) \times 100\%$$
  - **Classificação de Status:**
    - $R_{\%} < 35\% \implies \text{"Fadigado"}$
    - $35\% \le R_{\%} < 100\% \implies \text{"Recuperando"}$
    - $R_{\%} = 100\% \implies \text{"Recuperado"}$
- **Janela de Alerta/Intercalação Pós-Full Body:**
  - Dispara recomendação de **Dia de Corrida & Recuperação** quando $\Delta \text{Dias}_{\text{calendário}} \le 1$ **e** $t_{\text{decorrido}} < 48\text{ horas}$ (ou $< 36\text{ horas}$ para repetição direta do mesmo grupamento em divisões Split).

---

### 5.7. Quantidade Adaptativa de Exercícios (`Workout Composition Engine - WCE`)
Para adequar o treino ao tempo disponível ($T_{\text{avail}}$), tempo de setup ($T_{\text{setup}}$), tempo médio de descanso ($\overline{T}_{\text{rest}}$) e tempo de execução por série ($T_{\text{set}}$):

$$N_e = \text{clamp}\left(\frac{T_{\text{avail}} - T_{\text{setup}}}{\overline{T}_{\text{rest}}(G, E) + T_{\text{set}}},\ N_{\min}(G, S_{\text{split}}),\ N_{\max}(G, S_{\text{split}}, E)\right)$$

---

### 5.8. Telemetria de Corrida, Zonas Cardíacas e Nutrição Esportiva
- **Pace (`min/km`) a partir da Velocidade ($v$ em $\text{m/s}$):**
  $$\text{Pace}_{\text{segundos/km}} = \frac{1000.0}{v_{\text{m/s}}} \quad \left(\text{Formatado como } \lfloor \text{Pace}/60 \rfloor\text{'}\ (\text{Pace} \bmod 60)\text{''/km}\right)$$
- **Frequência Cardíaca Máxima Estimada ($\text{FC}_{\max}$) e Zonas de Treino:**
  $$\text{FC}_{\max} = 220 - \text{Idade} \quad (\text{ou } 208 - 0.7 \times \text{Idade})$$
  - **Z1 (Recuperação):** $50\% - 60\%\ \text{FC}_{\max}$
  - **Z2 (Base Aeróbica / Queima de Gordura):** $60\% - 70\%\ \text{FC}_{\max}$
  - **Z3 (Tempo / Limiar Aeróbico):** $70\% - 80\%\ \text{FC}_{\max}$
  - **Z4 (Limiar Anaeróbico / Pace Forte):** $80\% - 90\%\ \text{FC}_{\max}$
  - **Z5 (Esforço Máximo / $\text{VO}_2\max$):** $90\% - 100\%\ \text{FC}_{\max}$
- **Gasto Calórico por Equivalente Metabólico ($\text{MET}$):**
  $$\text{Kcal} = \text{MET} \times \text{Peso}_{\text{kg}} \times \left(\frac{\text{Duração}_{\text{min}}}{60.0}\right)$$
- **Taxa Metabólica Basal (Equação de Mifflin-St Jeor):**
  $$\text{TMB}_{\text{Homem}} = (10 \times \text{Peso}_{\text{kg}}) + (6.25 \times \text{Altura}_{\text{cm}}) - (5 \times \text{Idade}) + 5$$
  $$\text{TMB}_{\text{Mulher}} = (10 \times \text{Peso}_{\text{kg}}) + (6.25 \times \text{Altura}_{\text{cm}}) - (5 \times \text{Idade}) - 161$$
  $$\text{TDEE} = \text{TMB} \times \text{Fator de Atividade}\ (1.2\text{ a }1.9)$$

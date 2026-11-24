# MAX PERFORMANCE — Manual de Identidade Visual, Arquitetura UI & Fórmulas
**Versão Atualizada:** 2026.10 (Paleta Unificada Dark Neutro + Azul/Ciano `#00B0FF` / `#00E5FF`)

Este documento consolida a identidade visual oficial do **MAX PERFORMANCE (MAX GYM & MAX RUN)**, as modificações estruturais recentes, as **Regras de Ouro** de layout e estado, a escala tipográfica e as fórmulas matemáticas/fisiológicas do aplicativo.

> Para a documentação funcional e fisiológica expandida de todos os módulos, consulte também o arquivo [`MANUAL_MESTRE_MAX_PERFORMANCE.md`](./MANUAL_MESTRE_MAX_PERFORMANCE.md).

---

## 🎨 1. Guia de Estilo de Cores (`Color.kt` & `RunColors.kt`)

### 1.1. Camada Atmosférica Dark Neutra
*   **Fundo Principal (`BackgroundStart` $\rightarrow$ `BackgroundEnd`):** `#0D0F14` $\rightarrow$ `#11151D`
*   **Superfícies de Cards (`GraphiteSurface` / `Surface`):** `#161922` com degradê interno sutil (`#161922` $\rightarrow$ `#11151D`)
*   **Pílulas e Containers Secundários (`SecondaryElement`):** `#1E2430`
*   **Bordas e Divisores (`BorderColor`):** `#262B38` (`1.dp`) + **Borda Especular Iluminada (`warmSpecularRimBorder`)** em Ciano `#00E5FF` (`alpha = 0.28f` no topo esquerdo decaindo para `0.04f` na base direita)

### 1.2. Paleta de Acento Oficial & Semântica Fisiológica
*   **Primária Oficial (Azul Max Performance):** `#00B0FF` (`Primary` / `MaxOrange`)
    *   *Uso:* Botões principais (`INICIAR TREINO`, `REGISTRAR SÉRIE`, `IR PARA CORRIDA DE HOJE`), linha da série ativa (`S1`), badges de grupamento muscular e indicadores de foco.
*   **Secundária / Brilho Ciano (`Secondary` / `CopperRimLight`):** `#00E5FF`
    *   *Uso:* Degradês de volume (`#00E5FF` $\rightarrow$ `#00B0FF`), telemetria de corrida, lupa de miniatura e destaques ativos nos modais `[ CARGAS ]`, `[ DESCANSO ]` e `[ ANÁLISES ]`.
*   **Sucesso / Série Concluída (`Success`):** `#34C759` / `#00E676`
    *   *Uso:* Séries finalizadas, selo `✓` no Cronograma 7 Dias e status muscular 100% recuperado.
*   **Alerta Fisiológico / Frequência Cardíaca (`Error` / `alert`):** `#FF4444` / `#FF5252`
    *   *Uso:* Exclusivo para a pílula de batimentos cardíacos (`♥ BPM`), alertas de dor/falha e ações destrutivas.
*   **Destaque Especial (`PremiumColor`):** `#AF52DE` (Roxo elétrico para conquistas e análises avançadas)

### 1.3. Hierarquia de Textos Neutros
*   **Texto Primário (`TextPrimary`):** `#FFFFFF` / `#F8FAFC`
*   **Texto Secundário (`TextSecondary`):** `#94A3B8` (Slate suave para subtítulos e descrições)
*   **Texto Terciário (`TextTertiary`):** `#64748B` (Slate médio para metadados e legendas)
*   **Texto Desabilitado (`TextDisabled`):** `#475569`

---

## 🔤 2. Tipografia, Tamanhos (`sp`) e Dimensões (`dp`)

### 2.1. Escala Tipográfica (`Type.kt`)
*   **Família Principal:** `FontFamily.SansSerif` (pesos `Medium`, `Bold`, `ExtraBold` e `Black`)
*   **Família Numérica/Cronômetros:** `FontFamily.Monospace`
*   **Proteção de Escala:** `fontScale = 1.0f` fixado globalmente em `MainActivity.kt` para evitar que configurações de fonte grande do sistema quebrem pílulas e cabeçalhos.

| Token / Componente | Tamanho (`sp`) | Line Height (`sp`) | Peso (`FontWeight`) | Uso no App |
| :--- | :---: | :---: | :---: | :--- |
| **`displayLarge`** | `32.sp` | `38.sp` | `Black (900)` | HUD de Corrida, Pace e Cronômetro central |
| **`displayMedium`** | `24.sp` | `30.sp` | `Black (900)` | Títulos de destaque e métricas principais |
| **Saudação da Home** | `23.sp` | `28.sp` | `Black (900)` | Título `"Olá, Atleta"` na Home |
| **`titleLarge` / Hero Title** | `20.sp`–`22.sp` | `26.sp` | `Bold` / `Black` | Título principal do Hero Card e cabeçalhos |
| **`bodyLarge`** | `16.sp` | `24.sp` | `Normal (400)` | Descrições gerais e orientações do Coach |
| **`bodyMedium` / Botões CTA** | `13.sp`–`14.sp` | `20.sp` | `Medium` / `ExtraBold` | Botões de ação (`Iniciar treino`, `Trocar treino`) |
| **`labelMedium`** | `12.sp` | `16.sp` | `Bold (700)` | Títulos de seção (`CRONOGRAMA 7 DIAS`), pílulas |
| **`labelSmall`** | `11.sp` | `16.sp` | `Medium` / `Bold` | Legenda do mapa de sprints (`maxLines = 1`), subtítulos |
| **Micro-Badges** | `9.5.sp`–`10.sp`| `12.sp` | `ExtraBold (800)` | Badge `"INTERCALAÇÃO HÍBRIDA • CORRIDA"` |

### 2.2. Dimensões Críticas de Layout (`dp`)
*   **Escala Global de Tela (`MainActivity.kt`):** Largura base de referência `411.dp` (`targetScale = (screenWidthDp / 411f).coerceIn(0.82f, 1.15f)`).
*   **Hero Card (Capa Superior):** `165.dp` de altura, permitindo que o Hero Card + `CRONOGRAMA 7 DIAS (SEG A DOM)` caibam integralmente na primeira dobra da tela.
*   **Botão Principal do Hero Card:** `42.dp` de altura com `RoundedCornerShape(12.dp)`.
*   **Card Diário do Cronograma Semanal (`CalendarDayPillCard`):** `78.dp` de altura.
*   **Alvo Mínimo de Toque WCAG:** `48.dp x 48.dp`.

---

## 🏆 3. Regras de Ouro de Engenharia e UX

1.  **Unificação Azul/Ciano & Preservação Fisiológica:** Nenhum componente do Player de Musculação deve usar o laranja legado; toda ação ativa usa `#00B0FF`/`#00E5FF`, mantendo o Vermelho para `♥ BPM`/falha e o Verde para séries concluídas (`✓`).
2.  **Sincronia Absoluta Hero Card $\leftrightarrow$ Cronograma 7 Dias:** O card `"HOJE"` na barra semanal (`SEG A DOM`) reflete em tempo real o item ativo do Hero Card, respeitando a intercalação híbrida de 48h pós-Full Body.
3.  **Alinhamento Rígido de Botões e Pílulas:** Botões lado a lado utilizam `Modifier.height(IntrinsicSize.Min)` na `Row` e `Modifier.fillMaxHeight()` nos botões filhos, com `maxLines = 1`, `softWrap = false` e `TextOverflow.Ellipsis`.
4.  **Respeito às System Bars (`Edge-to-Edge`):** Telas cheias e modais de mapa (`MAPA INTERATIVO POR SPRINTS`) aplicam `statusBarsPadding()` e `navigationBarsPadding()` para nunca esconder botões atrás da barra de navegação do Android.
5.  **Imutabilidade de Séries Concluídas:** Recálculos de fadiga ou trocas de equipamento no meio do treino jamais alteram o peso ou as repetições de séries já marcadas como concluídas.

---

## 🧮 4. Resumo das Fórmulas Principais

*   **Escala de Densidade Responsiva:** $\text{scale} = \text{clamp}(\text{screenWidthDp} / 411.0,\ 0.82,\ 1.15)$
*   **Conversão Libras $\rightarrow$ Quilogramas:** $W_{\text{kg}} = W_{\text{lbs}} \times 0.45359237$
*   **1RM Epley:** $\text{1RM} = W \times (1 + \text{Reps} / 30)$
*   **1RM Brzycki:** $\text{1RM} = W / (1.0278 - 0.0278 \times \text{Reps})$
*   **Carga Inicial Biométrica:** $W_{\text{start}} = W_{\text{ref}} \times F_{\text{peso}} \times F_{\text{sexo}} \times F_{\text{idade}} \times F_{\text{exp}}$
*   **Fadiga Acumulada na Sessão:** $M_{\text{pos}} = \text{clamp}(1.0 - 0.04 \times \sum \text{Fadiga}_{\text{ant}},\ 0.75,\ 1.0)$
*   **Recuperação Muscular:** $R_{\%} = \text{clamp}(t_{\text{decorrido}} / T_{\text{rec}},\ 0.0,\ 1.0) \times 100\%$ ($72\text{h}$ músculos grandes / $24\text{h}$ pequenos)
*   **TMB (Mifflin-St Jeor):** $10 P_{\text{kg}} + 6.25 A_{\text{cm}} - 5 I + s$ ($s = +5$ masculino, $-161$ feminino)

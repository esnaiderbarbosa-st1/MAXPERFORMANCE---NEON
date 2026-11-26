# MAX PERFORMANCE — Guia de Design (MAX V3 Neon)

Documento de referência visual e de interface do **MAX PERFORMANCE**, alinhado exclusivamente ao código atual em Jetpack Compose (Material Design 3).

Para arquitetura técnica, banco de dados Room, backup, exportações e regras de engenharia, consulte `MANUAL_MESTRE_MAX_PERFORMANCE.md`.

---

## 1. Identidade Visual — MAX V3 Neon

O aplicativo adota estética dark de alto contraste (**MAX V3 Neon**) com superfícies neutras escuras, acentos em azul elétrico e ciano neon, e semântica estrita por cor.

### 1.1. Paleta de Cores

| Papel | Hex | Uso no Aplicativo |
| :--- | :--- | :--- |
| Fundo Obsidian (ícone e camadas profundas) | `#070B14` | Fundo do ícone adaptativo V3 Neon e superfícies profundas |
| Fundo Principal (`BackgroundStart` / `RunColors.bg`) | `#0D0F14` | Background padrão de telas e containers raiz |
| Fundo Secundário (`BackgroundEnd`) | `#11151D` | Degradê de fundo e base inferior de cards |
| Superfície de Cards (`GraphiteSurface` / `RunColors.surface`) | `#161922` | Cards de treino, histórico, atividades recentes e painéis |
| Container Secundário (`SecondaryElement`) | `#1E2430` | Chips, pílulas e campos secundários |
| Borda Padrão (`BorderColor` / `RunColors.border`) | `#262B38` | Divisores e contornos sutis (`1.dp`) |
| Acento Primário — Azul Elétrico (`Primary` / `RunColors.brand`) | `#00B0FF` / `#2563EB` | Botões CTA principais, foco ativo, navegação e identidade |
| Acento Secundário — Ciano Neon (`Secondary` / `RunColors.brandText`) | `#00D4FF` / `#00E5FF` | Brilho especular (`warmSpecularRimBorder`), telemetria, indicadores ativos e ícone V3 Neon |
| Classificação de Prova (Âmbar) | `#F59E0B` | Padrão único e exclusivo para badge `PROVA` e borda de destaque de corridas classificadas como Prova |
| Sucesso / Concluído (`Success` / `RunColors.success`) | `#34C759` / `#00E676` | Séries concluídas, dias cumpridos, fase de caminhada e status recuperado |
| Alerta / Frequência Cardíaca (`Error` / `RunColors.alert`) | `#FF4444` / `#FF5252` | Batimentos cardíacos (BPM), alertas críticos de GPS/dor e ações destrutivas |
| Texto Primário (`TextPrimary` / `RunColors.textPrimary`) | `#FFFFFF` / `#F8FAFC` | Títulos, métricas principais e rótulos ativos |
| Texto Secundário (`TextSecondary`) | `#94A3B8` | Subtítulos, datas e descrições |
| Texto Terciário (`TextTertiary`) | `#64748B` | Metadados discretos e legendas auxiliares |

### 1.2. Política Zero Emojis

- Não utilizar emojis do sistema operacional em títulos, botões, badges, modais, rótulos ou notificações de interface.
- Toda sinalização visual deve usar ícones vetoriais do Material Symbols (`Icons.Default.*` e `Icons.AutoMirrored.*`) ou vetores customizados em XML/Canvas.

---

## 2. Tipografia, Densidade e Escala `Aa+ 120%`

### 2.1. Escala Tipográfica (`Type.kt`)

- Família principal: `FontFamily.SansSerif` (`displayLarge` 32sp Black, `displayMedium` 24sp Black, `titleLarge` 20sp Bold, `bodyLarge` 16sp Normal, `bodyMedium` 14sp Medium, `labelMedium` 12sp Bold, `labelSmall` 11sp Medium).
- Números de cronômetro, pace e telemetria utilizam `FontFamily.Monospace` ou `fontFeatureSettings = "tnum"` para evitar jitter horizontal.

### 2.2. Suporte Obrigatório à Escala `Aa+ 120%`

- Em `MainActivity.kt`, a densidade base adapta smartphones pela largura de referência de `411.dp` (`targetScale` entre `0.82f` e `1.15f`) com `fontScale = 1.0f` no container raiz; tablets (`>= 600.dp`) usam `targetScale = 1.0f` com largura máxima de `840.dp`.
- Sobre essa base, o módulo de corrida oferece controle explícito de escala de fonte (`RunFontScaleManager` + `ProvideRunFontScale` + botão `Aa+`) com 3 níveis persistidos: `100%` (`1.00f`), **`120%` (`1.20f`)** e `135%` (`1.35f`), sem multiplicação aninhada (`LocalBaseRunFontScale`). O próprio controle `Aa+` permanece em `1.0f` para preservar o cabeçalho.
- O player de musculação (`FlowStatePlayerComponents`) também suporta ajuste dedicado de escala entre `85%` e `140%`.
- **Regra obrigatória de layout:** todo card, chip, cabeçalho e modal deve ser validado com `Aa+ 120%` ativo — sem sobreposição de texto, sem corte de pace/data e respeitando alvo mínimo de toque de `48.dp`.

---

## 3. Padrões de Layout e Componentes

### 3.1. Modais em 3 Zonas com Rodapé Fixo

Modais extensos ou com formulários/seletores (como o modal **Configuração da Corrida**) seguem obrigatoriamente a arquitetura em 3 zonas:

1. **Zona 1 — Cabeçalho Fixo:** título claro e botão de fechar (`X`) com área de toque >= `48.dp`, sempre visível no topo.
2. **Zona 2 — Corpo Rolável:** container intermediário com `Modifier.weight(1f, fill = false)` e rolagem interna (`verticalScroll` ou `LazyColumn`), evitando grandes espaços vazios entre seções e impedindo cortes quando `Aa+ 120%` estiver ativo.
3. **Zona 3 — Rodapé Fixo:** ação primária de confirmação explícita sempre ancorada na base, respeitando `navigationBarsPadding()` em layout Edge-to-Edge.

### 3.2. Cards de Atividades Recentes e Histórico de Corridas

Os cards de corrida seguem um padrão visual unificado entre a aba Início (**Atividades recentes**) e o **Histórico**:

- **Padrão único de Prova:** corridas classificadas como Prova (via modal Treino/Prova, tipo `RACE_5K`/`RACE_10K` ou vínculo legado em `OfficialRaceRecord`) exibem exclusivamente o badge esquerdo âmbar (`#F59E0B`) com rótulo `PROVA` e borda âmbar sutil no card. Badges duplicados à direita ou no rodapé são proibidos.
- **Alinhamento vertical por badge fixo:** em Atividades Recentes, o badge da esquerda (`PROVA`, `LIVRE`, `5K`, etc.) possui largura fixa de **`66.dp`**, centralizado e com `maxLines = 1`, garantindo alinhamento vertical uniforme da coluna de títulos entre todos os cards.
- **Distribuição em duas linhas sem truncamento:**
  - **Linha 1:** título da sessão à esquerda (`maxLines = 1`, `TextOverflow.Ellipsis`) e data compacta (`dd.MM.yy`) ancorada à direita.
  - **Linha 2:** métricas limpas no formato `distância · duração · pace`, liberando largura horizontal suficiente para evitar cortes mesmo em `Aa+ 120%`.
- **Coerência de título para Provas:** quando uma corrida genérica (ex.: `"Corrida Livre"`) é classificada como Prova sem nome personalizado (`raceName` vazio), o título exibido passa a ser `"Prova"` (evitando a contradição `PROVA` + `"Corrida Livre"`). Ao desmarcar de volta para Treino, o título original (`"Corrida Livre"`) é restaurado.

### 3.3. Edge-to-Edge e Navegação

- `enableEdgeToEdge()` com status bar e navigation bar escuras transparentes.
- Telas cheias, overlays de mapa e rodapés de modais aplicam `statusBarsPadding()` e `navigationBarsPadding()` para não colidir com as barras do sistema.

---

## 4. Ícone do Launcher — MAX V3 Neon

Configurado como ícone adaptativo em `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` e `ic_launcher_round.xml`, mantendo exatamente **1** entrada de launcher ativa (`SplashActivity`).

- **Camada de fundo:** obsidian escuro (`#070B14`) com acento luminoso em azul elétrico/ciano neon (`#00D4FF` / `#2563EB`).
- **Camada frontal (`ic_launcher_foreground.xml`, viewport `108dp x 108dp`):**
  - Monograma geométrico `M` inclinado: ala esquerda em branco de alto contraste (`#FFFFFF`) e ala direita em gradiente linear ciano neon para azul elétrico (`#00D4FF` -> `#0EA5E9` -> `#2563EB`).
  - Chevron ascendente central de performance em ciano neon (`#00D4FF`).

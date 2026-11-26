# MANUAL MESTRE — MAX PERFORMANCE

Documento técnico oficial do aplicativo Android **MAX PERFORMANCE** (Jetpack Compose + Kotlin), refletindo exclusivamente a arquitetura e as regras implementadas no código atual.

Para paleta de cores, tipografia, escala `Aa+ 120%`, modais e ícone do launcher, consulte `DESIGN_GUIDE.md`.

---

## 1. Identidade do App, Build e Assinatura

Referência: `app/build.gradle.kts` e `app/src/main/AndroidManifest.xml`.

- **Application ID (imutável):** `com.aistudio.maxperformance.yocvic`
- **Namespace (imutável):** `com.example` (preserva a classe `R` e a estrutura de pacotes sem quebrar imports)
- **SDKs:** `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36.1`, Java 11
- **Versionamento:** controlado em `version.properties` (`VERSION_CODE` auto-incrementado em tasks `assemble`/`bundle`/`install`)
- **FileProvider:** authority padronizada como `${applicationId}.fileprovider` no manifesto e `"${context.packageName}.fileprovider"` no código Kotlin
- **Assinatura (`signingConfigs`):**
  - `debugConfig`: usa `${rootDir}/debug.keystore` (`keyAlias = "androiddebugkey"`)
  - `release`: usa `KEYSTORE_PATH` (fallback `${rootDir}/my-upload-key.jks`), `STORE_PASSWORD`, `KEY_PASSWORD` (`keyAlias = "upload"`)
  - **Regra crítica:** nunca modificar `debug.keystore`, `debug.keystore.base64`, `signingConfigs` nem o `applicationId`, garantindo atualização direta sobre instalações existentes no aparelho sem perda de dados.

---

## 2. Banco de Dados Room (Versão 23) e Política de Migração

Referência: `app/src/main/java/com/example/data/database/AppDatabase.kt`.

- **Nome do banco:** `max_performance_db`
- **Versão atual do schema:** **`23`** (`exportSchema = true`, schemas exportados em `app/schemas`)
- **Entidades registradas (19):**
  `UserProfileData`, `CustomExerciseEntity`, `Workout`, `WorkoutHistoryEntry`, `CardioSession`, `Classroom`, `ChatMessage`, `FeedItem`, `PendingSyncAction`, `AppUser`, `AccessRequest`, `AuditLog`, `TelemetryEventEntity`, `CrashReportEntity`, `GymProfile`, `BetaEvaluationEntity`, `WorkoutSessionEntity`, `RunningSession`, `GpsTrackPoint`.
- **Conversores (`Converters`):** serialização JSON (Moshi/org.json) para listas de strings, `PreRunCheckIn`, `SessionExecutionAudit`, `MetricProvenance`, `List<ExecutedInterval>` e `AdvancedRunningMetrics`.
- **Cadeia de migrações explícitas:** `MIGRATION_1_6` até `MIGRATION_22_23` todas registradas em `AppDatabase.getDatabase()`.
  - `MIGRATION_20_21`: colunas científicas pré/pós-corrida em `running_sessions` (`preRunCheckIn`, `rpeCr10`, `sRpeLoadUa`, `executionAudit`, `hrProvenance`).
  - `MIGRATION_21_22`: `executedIntervals` e `advancedMetrics` em `running_sessions`.
  - `MIGRATION_22_23`: calibração fisiológica e metas de bioimpedância em `user_profile` (`fcMaxCalibradaBpm`, `limiarLt1Bpm`, `limiarLt2Bpm`, `metaPercentualGordura`, `metaPercentualMusculo`, `metaAguaLitros`).

### Política Estrita de Segurança de Dados

1. **Nunca adicionar `fallbackToDestructiveMigration()` em upgrades.** O builder mantém exclusivamente `fallbackToDestructiveMigrationOnDowngrade()`.
2. Sempre que possível, estender estruturas JSON já persistidas (como `AdvancedRunningMetrics` na coluna `advancedMetrics`) para evoluir funcionalidades sem elevar a versão do Room.
3. Quando uma nova coluna SQL for inevitável, criar `Migration(N, N+1)` estritamente aditiva (`ALTER TABLE ... ADD COLUMN ... DEFAULT ...`), checando colunas existentes via `PRAGMA table_info(...)` para idempotência.

---

## 3. Módulo de Corrida (MAX RUN)

Referências: `RunningModels.kt`, `RunningProgramManager.kt`, `RunningModuleScreen.kt`, `RunningHistoryScreen.kt`, `RunningSessionDetailScreen.kt`, `RunSessionClassificationDialog.kt`.

### 3.1. Fluxo Instant GO (Corrida Livre e Modo Prova)

- Ao iniciar uma **Corrida Livre** ou **Modo Prova** (`FreeRunRaceMode`), o app pula 100% o diálogo de prontidão pré-corrida (`PreRunExpressReadinessDialog`) e abre diretamente o HUD de corrida pronto para cronometragem imediata.
- Treinos estruturados da planilha mantêm a anamnese/prontidão pré-treino quando aplicável.
- O salvamento final da sessão, telemetria GPS e métricas avançadas permanecem idênticos em ambos os fluxos.

### 3.2. Classificação Unificada Treino <-> Prova

- Qualquer corrida salva no histórico (inclusive uma Corrida Livre) pode ser marcada como **Prova** (com nome opcional) ou revertida para **Treino** pelo diálogo `RunSessionClassificationDialog`, sem alteração de schema do Room.
- **Armazenamento:** campos retrocompatíveis em `AdvancedRunningMetrics` (coluna `advancedMetrics` de `running_sessions`):
  - `isRaceOverride: Boolean? = null`
  - `raceName: String? = null`
- **Fonte única de verdade (`RunningModels.kt`):**
  - `RunningSession.isClassifiedAsRace(isLinkedOfficialRace: Boolean = false)`: respeita `advancedMetrics?.isRaceOverride` quando definido (`true` ou `false`); quando `null`, usa fallback `isLinkedOfficialRace || sessionType == "RACE_5K" || sessionType == "RACE_10K"`.
  - `RunningSession.customRaceName(isLinkedOfficialRace: Boolean = false)`: retorna `raceName` trimado apenas se a sessão estiver classificada como Prova.
  - `RunningSession.withRaceClassification(isRace, raceName)`: grava `isRaceOverride` e limpa `raceName` automaticamente quando `isRace == false`.
- **Regras de exibição (`RunningProgramManager`):**
  - Se classificada como Prova com nome personalizado/vinculado, exibe o nome da prova.
  - Se classificada como Prova sem nome e o título original for genérico (`Corrida Livre`, regenerativo livre ou caminhada), exibe `"Prova"`.
  - Se desmarcada para Treino, restaura o título original (`"Corrida Livre"`).

### 3.3. Telemetria, Áudio e Proteção em Execução

- Rastreamento GPS contínuo via `RunningTrackingService` (`foregroundServiceType = "location"`), gravando pontos em `gps_track_points` e `routePointsJson`.
- Alerta de voz de frequência cardíaca calibrado dinamicamente pela `fcMaxCalibradaBpm` do perfil (com fallback pela fórmula da idade quando não calibrada).
- Acumulador de passos/cadência congelado durante `isPaused == true`.
- Modo munhequeira em `MainActivity.kt`: protege contra toques acidentais de câmera/volume e mantém foco de tela durante corrida ativa, com janela de navegação externa intencional (60s) para controle de música (prioridade ao Spotify via `MediaMetadataListenerService` / `dispatchMediaKeyEvent`) e foco de áudio transitório com atenuação no TTS.

---

## 4. Módulo de Musculação (MAX GYM) e Integração Híbrida

- Execução em foreground via `WorkoutForegroundService` (`foregroundServiceType = "specialUse"`) com timer de descanso persistente e preservação estrita de séries já concluídas.
- Composição, adaptação de carga e modo híbrido distribuídos entre `WorkoutEngine`, `GymContextManager`, `HybridLoadCoachEngine` e `WeightCalculator` (suporte a kg, barra/anilhas, halteres e torres em libras).
- Acompanhamento de composição corporal/bioimpedância (`BodyRecompScreen`, `BioimpedanceParser`, `BioimpedancePdfExporter`) e integração opcional com Health Connect (`androidx.health.connect`).

---

## 5. Padronização de Pastas de Exportação (`AppExportPaths`)

Referência: `app/src/main/java/com/example/utils/AppExportPaths.kt`.

Todas as exportações públicas no armazenamento compartilhado do Android convergem para uma única pasta raiz **`MAXPERFORMANCE`**:

| Constante | Caminho Relativo | Conteúdo |
| :--- | :--- | :--- |
| `PUBLIC_FOLDER_NAME` | `MAXPERFORMANCE` | Nome canônico único (sem variações de caixa) |
| `PICTURES_RELATIVE_PATH` | `Pictures/MAXPERFORMANCE` | Share cards e relatórios em imagem |
| `PICTURES_CAPTURES_RELATIVE_PATH` | `Pictures/MAXPERFORMANCE/Capturas` | Capturas longas de tela |
| `DOWNLOADS_RELATIVE_PATH` | `Download/MAXPERFORMANCE` | Relatórios PDF (ex.: bioimpedância) |
| `DOWNLOADS_CSV_RELATIVE_PATH` | `Download/MAXPERFORMANCE/CSV` | Planilhas CSV analíticas de corrida |
| `MOVIES_RELATIVE_PATH` | `Movies/MAXPERFORMANCE` | Vídeos 3D de percurso |

---

## 6. Backup/Restauração (`.json`) e Exportação CSV

### 6.1. Backup Completo em JSON (`AppBackupManager`)

- **Formato:** JSON UTF-8 serializado via Moshi (`CURRENT_BACKUP_VERSION = 1`), acessado via Storage Access Framework (`CreateDocument` / `OpenDocument`).
- **Nome sugerido:** `MAXPerformanceRun_Backup_yyyyMMdd_HHmm.json`.
- **Escopo incluído em `AppBackupPayload`:** `userProfile`, `runningSessions` (incluindo `advancedMetrics` com `isRaceOverride` e `raceName`), `workouts`, `workoutHistory`, `workoutSessions`, `gymSessions`, `bioimpedanceRecords` e `upcomingRaces`.
- **Restauração idempotente:** realiza upsert por ID sem apagar dados locais existentes, valida chaves conhecidas contra JSON malformado/de outros apps e normaliza `raceName` para `null` quando `isRaceOverride == false`.
- Disponível tanto nas Configurações quanto diretamente na tela inicial `ProfileSetupScreen` (`ProfileGate.SetupNeeded`).

### 6.2. Exportação CSV Analítica (`RunningCsvExporter`)

- **Arquivo:** `MAX_RUN_Analitico_yyyyMMdd_HHmm.csv` em UTF-8 com BOM (`\uFEFF`) e separador `;` (compatível com Excel e Google Sheets).
- **Destino:** salvo em `Download/MAXPERFORMANCE/CSV` (MediaStore no Android 10+ ou diretório público legado) e compartilhado imediatamente via `FileProvider` (`${context.packageName}.fileprovider`).
- **Estrutura (30 colunas em 5 grupos):**
  1. Identificação: `Data`, `Hora`, `Atleta`, `Classificacao` (`Prova` ou `Treino`), `Titulo_Sessao`, `Semana`, `Dia`, `Estrutura`, `Cidade_GPS`.
  2. Treino Principal: `Dist_Principal_KM`, `Tempo_Principal_Seg`, `Tempo_Principal_Min_Dec`, `Tempo_Principal_MMSS`, `Pace_Principal`.
  3. Sessão Total: `Dist_Total_KM`, `Tempo_Total_Seg`, `Tempo_Total_Min_Dec`, `Tempo_Total_MMSS`, `Pace_Total`.
  4. Desempenho nos Tiros: `Pace_Medio_Tiros`, `Pace_Melhor_Tiro`, `Tempo_Correndo_Seg`, `Tempo_Caminhando_Seg`.
  5. Fisiologia e Equipamento: `BPM_Medio`, `BPM_Max`, `Cadencia_SPM`, `Tenis_Utilizado`, `KM_Acumulado_Tenis`, `RPE_Manual`, `Dor_Manual`.

---

## 7. Regras de Proteção para Edições Futuras

1. **Identidade e assinatura intocáveis:** não alterar `applicationId` (`com.aistudio.maxperformance.yocvic`), `namespace` (`com.example`), `signingConfigs`, `debug.keystore`, `debug.keystore.base64` nem `allowBackup`.
2. **Integridade do Room:** não alterar retroativamente migrações existentes nem introduzir `fallbackToDestructiveMigration()`.
3. **Fluxos críticos protegidos:** preservar `ProfileGate`, `ProfileSetupScreen`, `AppBackupManager`, `DeveloperTools`, lógica de progressão de carga da musculação e fluxo *Instant GO* na Corrida Livre e Modo Prova.
4. **Higiene visual:** zero emojis em código de UI, padrão único de badge âmbar `PROVA` (`66.dp` em Atividades Recentes), modais em 3 zonas com rodapé fixo e validação obrigatória em `Aa+ 120%`.

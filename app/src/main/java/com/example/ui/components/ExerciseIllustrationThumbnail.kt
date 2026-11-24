package com.example.ui.components

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.Normalizer
import java.util.Locale

// Paleta Oficial Dark + Azul/Ciano (#00B0FF / #29B6F6 / #0288D1)
private val MaxOrange = Color(0xFF00B0FF)
private val MaxOrangeBright = Color(0xFF29B6F6)
private val CopperRim = Color(0xFF00E5FF)
private val CopperVolumeTop = Color(0xFF29B6F6)
private val CopperVolumeBottom = Color(0xFF0288D1)
private val WarmObsidianDeep = Color(0xFF0D0F14)
private val WarmCardTop = Color(0xFF161922)
private val WarmCardBottom = Color(0xFF11151D)
private val WarmPillSurface = Color(0xFF1E2430)
private val WarmSandSecondary = Color(0xFF94A3B8)
private val ClayBodyLight = Color(0xFFF1F5F9)
private val ClayBodyMid = Color(0xFF94A3B8)
private val EquipmentSteel = Color(0xFF334155)
private val EquipmentHighlight = Color(0xFF64748B)

/**
 * Dicionário Oficial dos 32 Avatares Biomecânicos distribuídos nos 4 Blocos de Treino (4 x 8 = 32).
 *
 * Diretório nativo Android para os arquivos PNG/WebP:
 * `app/src/main/res/drawable/` (ex: `app/src/main/res/drawable/ex_agachamento_livre.png`)
 *
 * - Bloco 1 (8 exercícios): Membros Inferiores (Pernas, Glúteos e Panturrilhas)
 * - Bloco 2 (8 exercícios): Costas e Bíceps (Tração Vertical, Horizontal e Flexão de Cotovelo)
 * - Bloco 3 (8 exercícios): Peito e Tríceps (Empurrão Horizontal, Isoladores e Extensão de Cotovelo)
 * - Bloco 4 (8 exercícios): Ombros, Trapézio, Core e Mobilidade
 */
enum class ExerciseIllustrationType(
    val blockNumber: Int,
    val primaryDrawableKey: String,
    val drawableAliases: List<String>,
    val title: String,
    val primaryFocus: String,
    val biomechanicalTip: String
) {
    // ================= BLOCO 1: 8 MOVIMENTOS DE PERNAS E GLÚTEOS (01 a 08) =================
    SQUAT(
        blockNumber = 1,
        primaryDrawableKey = "ex_agachamento_livre",
        drawableAliases = listOf(
            "ex_agachamento_livre",
            "ex_agachamento",
            "ex_agachamento_barra",
            "ex_agachamento_smith",
            "ex_agachamento_hack",
            "ex_hack_machine",
            "ex_barbell_squat"
        ),
        title = "Agachamento Livre / Barra",
        primaryFocus = "Quadríceps, Glúteos e Core",
        biomechanicalTip = "Mantenha o peito aberto, abdômen travado (bracing) e desça controlando o quadril para trás e para baixo, alinhando os joelhos na direção da ponta dos pés."
    ),
    LEG_PRESS(
        blockNumber = 1,
        primaryDrawableKey = "ex_leg_press",
        drawableAliases = listOf(
            "ex_leg_press",
            "ex_leg_press_45",
            "ex_leg_press_45_graus",
            "ex_legpress"
        ),
        title = "Leg Press 45°",
        primaryFocus = "Quadríceps e Glúteos",
        biomechanicalTip = "Mantenha a lombar e o quadril totalmente apoiados no banco durante toda a descida. Empurre a plataforma pelos calcanhares sem travar os joelhos no topo."
    ),
    LEG_EXTENSION(
        blockNumber = 1,
        primaryDrawableKey = "ex_cadeira_extensora",
        drawableAliases = listOf(
            "ex_cadeira_extensora",
            "ex_extensora",
            "ex_leg_extension"
        ),
        title = "Cadeira Extensora",
        primaryFocus = "Quadríceps (Isolado)",
        biomechanicalTip = "Alinhe o eixo da máquina com a linha do seu joelho. Estenda totalmente as pernas contraindo o quadríceps por 1 segundo no topo e desça de forma controlada."
    ),
    LEG_CURL(
        blockNumber = 1,
        primaryDrawableKey = "ex_mesa_flexora",
        drawableAliases = listOf(
            "ex_mesa_flexora",
            "ex_cadeira_flexora",
            "ex_flexora",
            "ex_leg_curl"
        ),
        title = "Mesa / Cadeira Flexora",
        primaryFocus = "Posteriores de Coxa (Isquiotibiais)",
        biomechanicalTip = "Mantenha o quadril pressionado contra o banco para isolar os isquiotibiais. Flexione os joelhos em amplitude máxima e segure a fase excêntrica."
    ),
    STIFF_RDL(
        blockNumber = 1,
        primaryDrawableKey = "ex_stiff",
        drawableAliases = listOf(
            "ex_stiff",
            "ex_terra_romeno",
            "ex_stiff_rdl",
            "ex_rdl",
            "ex_levantamento_terra"
        ),
        title = "Stiff / Terra Romeno (RDL)",
        primaryFocus = "Posteriores de Coxa, Glúteos e Lombar",
        biomechanicalTip = "Inicie o movimento projetando o quadril para trás (dobradiça) com os joelhos levemente destravados, mantendo a barra colada às pernas e a coluna neutra."
    ),
    LUNGE_BULGARIAN(
        blockNumber = 1,
        primaryDrawableKey = "ex_afundo",
        drawableAliases = listOf(
            "ex_afundo",
            "ex_agachamento_bulgaro",
            "ex_bulgaro",
            "ex_passada",
            "ex_afundo_halteres"
        ),
        title = "Afundo / Passada / Búlgaro",
        primaryFocus = "Quadríceps e Glúteos (Unilateral)",
        biomechanicalTip = "Concentre 80% da força no calcanhar da perna da frente. Desça até o joelho formar cerca de 90° mantendo o tronco firme e estável."
    ),
    HIP_THRUST(
        blockNumber = 1,
        primaryDrawableKey = "ex_elevacao_pelvica",
        drawableAliases = listOf(
            "ex_elevacao_pelvica",
            "ex_hip_thrust",
            "ex_hip_thrust_barra",
            "ex_cadeira_abdutora",
            "ex_gluteo"
        ),
        title = "Elevação Pélvica / Hip Thrust",
        primaryFocus = "Glúteo Máximo e Estabilizadores",
        biomechanicalTip = "Apoie a borda inferior das escápulas no banco, mantenha o queixo próximo ao peito e contraia fortemente os glúteos no topo até alinhar tronco e coxas."
    ),
    CALF_RAISE(
        blockNumber = 1,
        primaryDrawableKey = "ex_panturrilha",
        drawableAliases = listOf(
            "ex_panturrilha",
            "ex_elevacao_panturrilha",
            "ex_panturrilha_em_pe",
            "ex_panturrilha_sentado",
            "ex_gemeos"
        ),
        title = "Elevação de Panturrilha",
        primaryFocus = "Gastrocnêmio e Sóleo",
        biomechanicalTip = "Alongue bem o calcanhar na parte inferior da plataforma por 1 segundo e suba até a ponta máxima dos pés sem usar impulso dos joelhos."
    ),

    // ================= BLOCO 2: 8 MOVIMENTOS DE COSTAS E BÍCEPS (09 a 16) =================
    SEATED_CABLE_ROW(
        blockNumber = 2,
        primaryDrawableKey = "ex_remada_baixa",
        drawableAliases = listOf(
            "ex_remada_baixa",
            "ex_remada_baixa_triangulo",
            "ex_remada_triangulo",
            "ex_remada_sentada",
            "ex_remada_maquina",
            "ex_remada_articulada"
        ),
        title = "Remada Baixa na Polia / Triângulo",
        primaryFocus = "Grande Dorsal, Romboides e Trapézio Médio",
        biomechanicalTip = "Mantenha a coluna ereta e o peito estufado. Inicie a puxada retraindo as escápulas e traga o triângulo até o abdômen conduzindo pelos cotovelos."
    ),
    LAT_PULLDOWN(
        blockNumber = 2,
        primaryDrawableKey = "ex_puxada_alta",
        drawableAliases = listOf(
            "ex_puxada_alta",
            "ex_puxada_frontal",
            "ex_puxada_frontal_pulley",
            "ex_puxada_pulley",
            "ex_pulldown"
        ),
        title = "Puxada Alta / Frontal no Pulley",
        primaryFocus = "Grande Dorsal (Expansão em V) e Bíceps",
        biomechanicalTip = "Deprima os ombros longe das orelhas antes de flexionar os cotovelos. Puxe a barra em direção à parte superior do peito inclinando levemente o tronco."
    ),
    BENT_OVER_ROW(
        blockNumber = 2,
        primaryDrawableKey = "ex_remada_curvada",
        drawableAliases = listOf(
            "ex_remada_curvada",
            "ex_remada_curvada_barra",
            "ex_remada_barra",
            "ex_remada_cavalinho"
        ),
        title = "Remada Curvada com Barra / Cavalinho",
        primaryFocus = "Espessura Dorsal, Lombar e Romboides",
        biomechanicalTip = "Incline o tronco a cerca de 45° mantendo a coluna neutra e o core travado. Puxe a barra rente às coxas em direção à linha umbilical sem dar trancos."
    ),
    ONE_ARM_DUMBBELL_ROW(
        blockNumber = 2,
        primaryDrawableKey = "ex_remada_serrote",
        drawableAliases = listOf(
            "ex_remada_serrote",
            "ex_remada_unilateral",
            "ex_serrote"
        ),
        title = "Remada Unilateral / Serrote",
        primaryFocus = "Grande Dorsal Unilateral e Redondo Maior",
        biomechanicalTip = "Estabilize a coluna paralela ao banco. Puxe o halter descrevendo um arco em direção ao quadril (e não ao ombro) para máxima ativação da dorsal."
    ),
    PULL_UP_CHIN_UP(
        blockNumber = 2,
        primaryDrawableKey = "ex_barra_fixa",
        drawableAliases = listOf(
            "ex_barra_fixa",
            "ex_barra_fixa_pronada",
            "ex_barra_fixa_supinada",
            "ex_graviton",
            "ex_pull_up"
        ),
        title = "Barra Fixa / Graviton",
        primaryFocus = "Grande Dorsal, Redondo Maior e Bíceps",
        biomechanicalTip = "Parta da extensão controlada mantendo os ombros encaixados. Traga o peito em direção à barra apontando os cotovelos para o chão."
    ),
    BARBELL_CURL(
        blockNumber = 2,
        primaryDrawableKey = "ex_rosca_direta",
        drawableAliases = listOf(
            "ex_rosca_direta",
            "ex_rosca_direta_barra_w",
            "ex_rosca_barra",
            "ex_rosca_barra_w",
            "ex_rosca_w"
        ),
        title = "Rosca Direta com Barra / W",
        primaryFocus = "Bíceps Braquial (Cabeças Curta e Longa)",
        biomechanicalTip = "Mantenha os cotovelos colados ao lado das costelas sem projetá-los à frente. Contraia o bíceps no topo e controle a descida até estender quase totalmente."
    ),
    DUMBBELL_HAMMER_CURL(
        blockNumber = 2,
        primaryDrawableKey = "ex_rosca_martelo",
        drawableAliases = listOf(
            "ex_rosca_martelo",
            "ex_rosca_alternada",
            "ex_rosca_alternada_halteres",
            "ex_rosca_martelo_halteres",
            "ex_hammer_curl"
        ),
        title = "Rosca Martelo / Alternada com Halteres",
        primaryFocus = "Bíceps Braquial, Braquial e Braquiorradial",
        biomechanicalTip = "Evite girar o tronco para subir a carga. Na variação martelo, mantenha pegada neutra firme; na alternada, supine o punho durante a subida."
    ),
    PREACHER_CONCENTRATION_CURL(
        blockNumber = 2,
        primaryDrawableKey = "ex_rosca_scott",
        drawableAliases = listOf(
            "ex_rosca_scott",
            "ex_rosca_scott_barra_w",
            "ex_rosca_concentrada",
            "ex_preacher_curl"
        ),
        title = "Rosca Scott / Concentrada",
        primaryFocus = "Isolamento de Pico do Bíceps Braquial",
        biomechanicalTip = "Mantenha toda a parte posterior do braço apoiada no banco Scott (ou na coxa). Não retire os cotovelos do apoio na subida e desça controlando o alongamento."
    ),

    // ================= BLOCO 3: 8 MOVIMENTOS DE PEITO, TRÍCEPS E EMPURRÃO (17 a 24) =================
    BENCH_PRESS(
        blockNumber = 3,
        primaryDrawableKey = "ex_supino_reto",
        drawableAliases = listOf(
            "ex_supino_reto",
            "ex_supino_reto_barra",
            "ex_supino_reto_halteres",
            "ex_supino_maquina",
            "ex_supino_declinado",
            "ex_supino"
        ),
        title = "Supino Reto com Barra / Halteres",
        primaryFocus = "Peitoral Maior (Porção Esternal), Deltoide Anterior e Tríceps",
        biomechanicalTip = "Mantenha as escápulas retraídas e deprimidas no banco, pés firmes no solo e cotovelos levemente angulados (~45° a 75°) para proteger os ombros."
    ),
    INCLINE_BENCH_PRESS(
        blockNumber = 3,
        primaryDrawableKey = "ex_supino_inclinado",
        drawableAliases = listOf(
            "ex_supino_inclinado",
            "ex_supino_inclinado_halteres",
            "ex_supino_inclinado_barra"
        ),
        title = "Supino Inclinado com Halteres / Barra",
        primaryFocus = "Peitoral Superior (Porção Clavicular) e Deltoide Anterior",
        biomechanicalTip = "Ajuste o banco entre 30° e 45°. Desça os halteres ou a barra até a linha clavicular mantendo os punhos alinhados aos cotovelos."
    ),
    CHEST_FLYE_PECK_DECK(
        blockNumber = 3,
        primaryDrawableKey = "ex_voador_peitoral",
        drawableAliases = listOf(
            "ex_voador_peitoral",
            "ex_peck_deck",
            "ex_voador",
            "ex_crucifixo",
            "ex_crucifixo_reto",
            "ex_crucifixo_inclinado"
        ),
        title = "Voador Peitoral (Peck Deck) / Crucifixo",
        primaryFocus = "Peitoral Maior (Adução Horizontal e Alongamento)",
        biomechanicalTip = "Abra os braços em arco amplo mantendo uma leve semiflexão nos cotovelos. Feche contraindo o centro do peitoral com controle."
    ),
    CABLE_CROSSOVER(
        blockNumber = 3,
        primaryDrawableKey = "ex_crossover",
        drawableAliases = listOf(
            "ex_crossover",
            "ex_cross_over",
            "ex_cross_over_polia_media",
            "ex_crossover_polia"
        ),
        title = "Cross Over na Polia",
        primaryFocus = "Peitoral Maior (Tensão Contínua em Cabos)",
        biomechanicalTip = "Incline levemente o tronco à frente para estabilidade. Conduza as polias em arco fechado à frente do esterno espremendo as fibras peitorais."
    ),
    PUSH_UP(
        blockNumber = 3,
        primaryDrawableKey = "ex_flexao_bracos",
        drawableAliases = listOf(
            "ex_flexao_bracos",
            "ex_flexao_de_bracos",
            "ex_flexao",
            "ex_push_up"
        ),
        title = "Flexão de Braços / Apoio Solo",
        primaryFocus = "Peitoral Maior, Tríceps e Estabilização de Core",
        biomechanicalTip = "Mantenha o corpo em linha reta dos calcanhares à cabeça sem deixar o quadril cair. Desça até o peito se aproximar do solo e empurre com firmeza."
    ),
    TRICEPS_DIPS_EXTENSION(
        blockNumber = 3,
        primaryDrawableKey = "ex_mergulho_paralelas",
        drawableAliases = listOf(
            "ex_mergulho_paralelas",
            "ex_triceps_pulley",
            "ex_triceps_corda",
            "ex_triceps_testa",
            "ex_triceps_frances",
            "ex_triceps_banco",
            "ex_paralelas"
        ),
        title = "Mergulho em Paralelas / Extensão de Tríceps",
        primaryFocus = "Tríceps Braquial (Cabeças Lateral, Longa e Medial)",
        biomechanicalTip = "Mantenha os cotovelos alinhados junto ao corpo durante toda a extensão, buscando bloqueio controlado no final da repetição."
    ),
    SHOULDER_PRESS(
        blockNumber = 3,
        primaryDrawableKey = "ex_desenvolvimento_ombros",
        drawableAliases = listOf(
            "ex_desenvolvimento_ombros",
            "ex_desenvolvimento",
            "ex_desenvolvimento_halteres",
            "ex_desenvolvimento_militar",
            "ex_desenvolvimento_maquina",
            "ex_desenvolvimento_arnold"
        ),
        title = "Desenvolvimento de Ombros",
        primaryFocus = "Deltoides Anterior e Lateral, e Tríceps",
        biomechanicalTip = "Estabilize o core e a lombar no encosto. Empurre acima da cabeça em arco convergente sem bater os halteres e desça até a linha do queixo/orelhas."
    ),
    LATERAL_RAISE(
        blockNumber = 3,
        primaryDrawableKey = "ex_elevacao_lateral",
        drawableAliases = listOf(
            "ex_elevacao_lateral",
            "ex_elevacao_lateral_halteres",
            "ex_elevacao_lateral_polia"
        ),
        title = "Elevação Lateral com Halteres / Polia",
        primaryFocus = "Deltoide Lateral (Largura de Ombros)",
        biomechanicalTip = "Incline o tronco levemente à frente e conduza o movimento pelos cotovelos no plano escapular até a altura dos ombros, sem encolher o trapézio."
    ),

    // ================= BLOCO 4: 8 MOVIMENTOS DE OMBROS, TRAPÉZIO, CORE, CARDIO & RECUPERAÇÃO (25 a 32) =================
    FRONT_RAISE(
        blockNumber = 4,
        primaryDrawableKey = "ex_elevacao_frontal",
        drawableAliases = listOf(
            "ex_elevacao_frontal",
            "ex_elevacao_frontal_halteres",
            "ex_elevacao_frontal_barra",
            "ex_elevacao_frontal_cabo"
        ),
        title = "Elevação Frontal com Halteres / Barra",
        primaryFocus = "Deltoide Anterior (Isolado)",
        biomechanicalTip = "Suba a carga à frente do corpo até a linha dos olhos mantendo o tronco firme sem balançar o quadril, e controle a descida em 2 segundos."
    ),
    FACE_PULL_REVERSE_FLYE(
        blockNumber = 4,
        primaryDrawableKey = "ex_face_pull",
        drawableAliases = listOf(
            "ex_face_pull",
            "ex_crucifixo_invertido",
            "ex_crucifixo_invertido_halteres",
            "ex_posterior_ombro"
        ),
        title = "Face Pull / Crucifixo Invertido",
        primaryFocus = "Deltoide Posterior, Romboides e Manguito Rotador",
        biomechanicalTip = "Puxe a corda em direção à testa afastando as mãos e rotacionando externamente os ombros, focando no deltoide posterior e estabilidade escapular."
    ),
    SHRUG_TRAPEZIUS(
        blockNumber = 4,
        primaryDrawableKey = "ex_encolhimento_ombros",
        drawableAliases = listOf(
            "ex_encolhimento_ombros",
            "ex_encolhimento",
            "ex_encolhimento_halteres",
            "ex_remada_alta",
            "ex_remada_alta_polia"
        ),
        title = "Encolhimento de Ombros / Remada Alta",
        primaryFocus = "Trapézio Superior e Elevador da Escápula",
        biomechanicalTip = "Eleve os ombros verticalmente em direção às orelhas sem girar a articulação, segure 1 segundo de contração máxima no topo e alongue na descida."
    ),
    ABDOMINAL_SUPRA(
        blockNumber = 4,
        primaryDrawableKey = "ex_abdominal_supra",
        drawableAliases = listOf(
            "ex_abdominal_supra",
            "ex_abdominal_crunch",
            "ex_abdominal_crunch_solo",
            "ex_abdominal_polia",
            "ex_abdominal"
        ),
        title = "Abdominal Supra / Crunch",
        primaryFocus = "Reto Abdominal (Porção Supra e Média)",
        biomechanicalTip = "Enrole a coluna torácica aproximando as costelas da pelve enquanto solta todo o ar no topo, sem puxar o pescoço com as mãos."
    ),
    ABDOMINAL_INFRA(
        blockNumber = 4,
        primaryDrawableKey = "ex_abdominal_infra",
        drawableAliases = listOf(
            "ex_abdominal_infra",
            "ex_elevacao_pernas",
            "ex_elevacao_de_pernas",
            "ex_elevacao_pernas_suspenso"
        ),
        title = "Abdominal Infra / Elevação de Pernas",
        primaryFocus = "Reto Abdominal Inferior e Controle Pélvico",
        biomechanicalTip = "Eleve os joelhos ou pernas enrolando a pelve para cima no final do movimento para ativar o abdômen inferior em vez de apenas os flexores do quadril."
    ),
    PLANK_ISOMETRIC(
        blockNumber = 4,
        primaryDrawableKey = "ex_prancha",
        drawableAliases = listOf(
            "ex_prancha",
            "ex_prancha_isometrica",
            "ex_plank"
        ),
        title = "Prancha Isométrica (Core)",
        primaryFocus = "Transverso do Abdômen, Reto Abdominal e Lombar",
        biomechanicalTip = "Apoie os antebraços alinhados aos ombros, contraia glúteos e abdômen simultaneamente (retroversão pélvica sutil) e respire de forma cadenciada."
    ),
    ACTIVE_RECOVERY_WALK(
        blockNumber = 4,
        primaryDrawableKey = "ex_caminhada",
        drawableAliases = listOf(
            "ex_caminhada",
            "ex_active_recovery_walk",
            "ex_alongamento_90_graus_coxa",
            "ex_alongamento_posterior_coxa",
            "ex_alongamento_posterior_e_lombar4",
            "ex_alongamento_lombar",
            "ex_alongamento_quadriceps",
            "ex_descanso_ativo",
            "ex_mobilidade",
            "ex_caminhada_regenerativa"
        ),
        title = "Descanso Ativo / Mobilidade & Caminhada",
        primaryFocus = "Recuperação Sistêmica, Mobilidade Articular e Fluxo Sanguíneo",
        biomechanicalTip = "Mantenha respiração nasal cadenciada e movimentos fluidos sem forçar dor articular, promovendo descompressão vertebral e recuperação ativa."
    ),
    SPRINT_INTERVAL(
        blockNumber = 4,
        primaryDrawableKey = "ex_sprint_interval",
        drawableAliases = listOf(
            "ex_sprint_interval",
            "ex_corrida_ar_livre",
            "ex_longao_run",
            "ex_treino_na_esteira",
            "ex_treadmill_run",
            "ex_tempo_run",
            "ex_caminhada",
            "ex_corrida",
            "ex_hiit",
            "ex_cardio"
        ),
        title = "Sprint Intervalado / Corrida & HIIT",
        primaryFocus = "Potência Cardiorrespiratória, VO2 Máx e Explosão de Membros Inferiores",
        biomechanicalTip = "Mantenha postura alta, ataque o solo com o mediopé abaixo do centro de gravidade e coordene a impulsão vigorosa dos braços a 90°."
    )
}

/**
 * Normaliza o nome de qualquer exercício para o padrão de nome de recurso do Android (`ex_<slug>`).
 */
fun normalizeExerciseNameToDrawableSlug(rawName: String): String {
    val withoutAccents = Normalizer.normalize(rawName, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    val cleaned = withoutAccents
        .lowercase(Locale.ROOT)
        .replace("°", "")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
    return if (cleaned.startsWith("ex_")) cleaned else "ex_$cleaned"
}

/**
 * Resolve o tipo de ilustração biomecânica entre os 32 avatares oficiais (4 blocos de 8 exercícios).
 */
fun resolveExerciseIllustrationType(exerciseName: String, muscleGroup: String): ExerciseIllustrationType {
    val name = Normalizer.normalize(exerciseName, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
    val group = Normalizer.normalize(muscleGroup, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)

    return when {
        // ==================== CARDIO / SPRINT & DESCANSO ATIVO / MOBILIDADE ====================
        name.contains("sprint") || name.contains("tiro") || name.contains("hiit") || name.contains("fartlek") || name.contains("corrida") || name.contains("longao") || name.contains("tempo run") || name.contains("esteira") || name.contains("treadmill") || name.contains("interval") || group.contains("cardio") || group.contains("corrida") ->
            ExerciseIllustrationType.SPRINT_INTERVAL

        name.contains("active recovery") || name.contains("descanso ativo") || name.contains("caminhada") || name.contains("mobilidade") || name.contains("alongamento") || name.contains("rotacao") || name.contains("descompressao") || name.contains("cat-cow") || group.contains("mobilidade") || group.contains("recuperacao") || group.contains("alongamento") ->
            ExerciseIllustrationType.ACTIVE_RECOVERY_WALK

        // ==================== BLOCO 1: PERNAS E GLÚTEOS (01 a 08) ====================
        name.contains("panturrilha") || name.contains("gemeos") || name.contains("calf") || group.contains("panturrilha") ->
            ExerciseIllustrationType.CALF_RAISE

        name.contains("leg press") || name.contains("legpress") ->
            ExerciseIllustrationType.LEG_PRESS

        name.contains("extensora") || name.contains("leg extension") ->
            ExerciseIllustrationType.LEG_EXTENSION

        name.contains("flexora") || name.contains("leg curl") || (name.contains("posterior") && !name.contains("ombro")) ->
            ExerciseIllustrationType.LEG_CURL

        name.contains("stiff") || name.contains("romeno") || name.contains("rdl") || name.contains("terra") || name.contains("good morning") || name.contains("bom dia") || name.contains("hiperextensao") || name.contains("lombar") ->
            ExerciseIllustrationType.STIFF_RDL

        name.contains("afundo") || name.contains("passada") || name.contains("bulgaro") || name.contains("avanco") || name.contains("lunge") ->
            ExerciseIllustrationType.LUNGE_BULGARIAN

        name.contains("elevacao pelvica") || name.contains("hip thrust") || name.contains("gluteo") || name.contains("abdutora") || name.contains("adutora") || group.contains("gluteo") ->
            ExerciseIllustrationType.HIP_THRUST

        name.contains("agachamento") || name.contains("squat") || name.contains("hack") || name.contains("sissy") || group.contains("perna") || group.contains("quadriceps") || group.contains("inferior") ->
            ExerciseIllustrationType.SQUAT

        // ==================== BLOCO 2: COSTAS E BÍCEPS (09 a 16) ====================
        name.contains("serrote") || (name.contains("remada") && (name.contains("unilateral") || name.contains("halter") || name.contains("dumbbell"))) ->
            ExerciseIllustrationType.ONE_ARM_DUMBBELL_ROW

        name.contains("remada baixa") || name.contains("seated row") || (name.contains("remada") && (name.contains("triangulo") || name.contains("polia") || name.contains("maquina") || name.contains("articulada") || name.contains("apoio") || name.contains("cabo") || name.contains("sentad"))) ->
            ExerciseIllustrationType.SEATED_CABLE_ROW

        name.contains("remada curvada") || name.contains("bent over") || name.contains("cavalinho") || name.contains("t-bar") || (name.contains("remada") && !name.contains("alta")) ->
            ExerciseIllustrationType.BENT_OVER_ROW

        name.contains("barra fixa") || name.contains("graviton") || name.contains("gravitation") || name.contains("pull-up") || name.contains("pull up") || name.contains("chin-up") || name.contains("chin up") ->
            ExerciseIllustrationType.PULL_UP_CHIN_UP

        name.contains("puxada") || name.contains("pulldown") || (name.contains("pulley") && !name.contains("triceps")) || group.contains("costas") || group.contains("dorsal") ->
            ExerciseIllustrationType.LAT_PULLDOWN

        name.contains("scott") || name.contains("preacher") || name.contains("concentrada") || name.contains("spider") ->
            ExerciseIllustrationType.PREACHER_CONCENTRATION_CURL

        name.contains("alternada") || name.contains("martelo") || name.contains("hammer") || (name.contains("rosca") && name.contains("halter")) ->
            ExerciseIllustrationType.DUMBBELL_HAMMER_CURL

        name.contains("rosca") || name.contains("biceps") || group.contains("biceps") ->
            ExerciseIllustrationType.BARBELL_CURL

        // ==================== BLOCO 3: PEITO, TRÍCEPS E EMPURRÃO (17 a 24) ====================
        name.contains("flexao") || name.contains("push-up") || name.contains("push up") || name.contains("apoio") ->
            ExerciseIllustrationType.PUSH_UP

        name.contains("cross") || name.contains("crossover") ->
            ExerciseIllustrationType.CABLE_CROSSOVER

        (name.contains("crucifixo") && !name.contains("invertido")) || name.contains("peck") || name.contains("pec deck") || name.contains("voador") || name.contains("pullover") ->
            ExerciseIllustrationType.CHEST_FLYE_PECK_DECK

        name.contains("supino") && name.contains("inclinado") ->
            ExerciseIllustrationType.INCLINE_BENCH_PRESS

        name.contains("supino") || group.contains("peito") ->
            ExerciseIllustrationType.BENCH_PRESS

        name.contains("mergulho") || name.contains("paralela") || name.contains("triceps") || name.contains("testa") || name.contains("skull") || name.contains("frances") || name.contains("kickback") || name.contains("coice") || group.contains("triceps") ->
            ExerciseIllustrationType.TRICEPS_DIPS_EXTENSION

        // ==================== BLOCO 4: OMBROS, TRAPÉZIO E CORE (23 a 30) ====================
        name.contains("encolhimento") || name.contains("remada alta") || name.contains("shrug") || group.contains("trapezio") ->
            ExerciseIllustrationType.SHRUG_TRAPEZIUS

        name.contains("crucifixo invertido") || name.contains("face pull") || (name.contains("posterior") && name.contains("ombro")) ->
            ExerciseIllustrationType.FACE_PULL_REVERSE_FLYE

        name.contains("elevacao frontal") || name.contains("front raise") ->
            ExerciseIllustrationType.FRONT_RAISE

        name.contains("elevacao lateral") || name.contains("lateral raise") ->
            ExerciseIllustrationType.LATERAL_RAISE

        name.contains("desenvolvimento") || name.contains("arnold") || name.contains("militar") || group.contains("ombro") ->
            ExerciseIllustrationType.SHOULDER_PRESS

        name.contains("prancha") || name.contains("plank") ->
            ExerciseIllustrationType.PLANK_ISOMETRIC

        (name.contains("elevacao de pernas") || name.contains("elevacao pernas") || name.contains("infra")) &&
            (name.contains("solo") || name.contains("chao") || name.contains("colchonete") || name.contains("banco")) ->
            ExerciseIllustrationType.ABDOMINAL_SUPRA

        name.contains("elevacao de pernas") || name.contains("elevacao pernas") || name.contains("infra") || name.contains("suspenso") ->
            ExerciseIllustrationType.ABDOMINAL_INFRA

        name.contains("abdominal") || name.contains("crunch") || name.contains("supra") || group.contains("abd") || group.contains("core") ->
            ExerciseIllustrationType.ABDOMINAL_SUPRA

        else -> ExerciseIllustrationType.SQUAT
    }
}

/**
 * Gera a lista ordenada de chaves candidatas de drawable (`res/drawable/<key>.png`) priorizando:
 * 1. `explicitIllustrationId` (se fornecido)
 * 2. Slug exato e simplificado do nome do exercício (para que todo bonequinho específico criado em `res/drawable/ex_*.png` tenha prioridade máxima)
 * 3. Mapeamentos diretos de variações, alongamentos e corrida já existentes em `res/drawable/`
 * 4. Chave canônica do bloco (`illustrationType.primaryDrawableKey`)
 * 5. `drawableAliases` de compatibilidade
 */
fun resolveBiomechanicDrawableCandidates(
    exerciseName: String,
    muscleGroup: String,
    illustrationType: ExerciseIllustrationType = resolveExerciseIllustrationType(exerciseName, muscleGroup),
    explicitIllustrationId: String? = null
): List<String> {
    val candidates = LinkedHashSet<String>()

    if (!explicitIllustrationId.isNullOrBlank()) {
        val cleanId = explicitIllustrationId.trim().lowercase(Locale.ROOT).removeSuffix(".png").removeSuffix(".webp")
        if (cleanId.startsWith("ex_")) {
            candidates.add(cleanId)
        } else {
            candidates.add("ex_$cleanId")
            candidates.add(cleanId)
        }
    }

    // 1. Slug normalizado exato e slug compacto (sem preposições como com/no/na/de) do próprio nome do exercício
    val fullSlug = normalizeExerciseNameToDrawableSlug(exerciseName)
    val compactSlug = fullSlug
        .replace(Regex("_(com|no|na|nos|nas|de|do|da|dos|das|em|para|por)_"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')

    if (fullSlug != "ex_") {
        candidates.add(fullSlug)
    }
    if (compactSlug.isNotBlank() && compactSlug != "ex_") {
        candidates.add(compactSlug)
    }

    // 2. Mapeamentos diretos de prioridade máxima para todos os avatares específicos em res/drawable/
    val normName = Normalizer.normalize(exerciseName, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)

    // Membros Inferiores & Lombar específicos
    if (normName.contains("goblet") || normName.contains("globet")) {
        candidates.add("ex_agachamento_goblet")
        candidates.add("ex_agachamento_globet")
    }
    if (normName.contains("pendul")) {
        candidates.add("ex_agachamento_pendulo")
        candidates.add("ex_agachamento_pendulum")
    }
    if (normName.contains("pistol")) {
        candidates.add("ex_agachamento_pistol")
        candidates.add("ex_agachamento_pistola")
    }
    if (normName.contains("agachamento") && normName.contains("smith")) {
        candidates.add("ex_agachamento_smith")
    }
    if (normName.contains("agachamento") && normName.contains("frontal")) {
        candidates.add("ex_agachamento_frontal")
    }
    if (normName.contains("agachamento") && normName.contains("sumo")) {
        candidates.add("ex_agachamento_sumo")
    }
    if (normName.contains("hack")) {
        candidates.add("ex_agachamento_hack_machine")
        candidates.add("ex_agachamento_hack")
        candidates.add("ex_hack_machine")
    }
    if (normName.contains("bulgaro")) {
        candidates.add("ex_agachamento_bulgaro")
    }
    if (normName.contains("afundo") && normName.contains("reverso")) {
        candidates.add("ex_afundo_reverso")
    }
    if (normName.contains("avanco lateral")) {
        candidates.add("ex_avanco_lateral")
    }
    if (normName.contains("step-up") || normName.contains("step up") || normName.contains("caixote") || normName.contains("passada")) {
        candidates.add("ex_subida_no_caixote")
    }
    if (normName.contains("cadeira flexora") || (normName.contains("flexora") && normName.contains("sentad"))) {
        candidates.add("ex_cadeira_flexora_sentado")
        candidates.add("ex_cadeira_flexora")
    }
    if (normName.contains("mesa flexora")) {
        candidates.add("ex_mesa_flexora")
    }
    if (normName.contains("abdutora") || normName.contains("abducao")) {
        candidates.add("ex_cadeira_abdutora")
    }
    if (normName.contains("adutora") || normName.contains("aducao")) {
        candidates.add("ex_cadeira_adutora")
    }
    if (normName.contains("panturrilha") || normName.contains("gemeos")) {
        when {
            normName.contains("leg press") || normName.contains("legpress") -> candidates.add("ex_panturrilha_leg_press")
            normName.contains("sentad") -> candidates.add("ex_panturrilha_sentado")
            else -> candidates.add("ex_panturrilha")
        }
    }
    if (normName.contains("stiff") && normName.contains("halter")) {
        candidates.add("ex_stiff_halteres")
    }
    if (normName.contains("terra") && normName.contains("sumo")) {
        candidates.add("ex_terra_sumo")
        candidates.add("ex_levantamento_terra_sumo")
    } else if (normName.contains("levantamento terra") || (normName.contains("terra") && !normName.contains("romeno"))) {
        candidates.add("ex_levantamento_terra_tradicional")
        candidates.add("ex_levantamento_terra")
    }
    if (normName.contains("elevacao pelvica") && normName.contains("unilateral")) {
        candidates.add("ex_elevacao_pelvica_unilateral")
    }
    if (normName.contains("glute bridge") || normName.contains("ponte de gluteo") || normName.contains("ponte glute") || normName.startsWith("ponte")) {
        candidates.add("ex_ponte_glute_bridge")
        candidates.add("ex_ponte_de_gluteos")
    }

    // Costas, Bíceps, Peito, Ombros e Tríceps específicos
    if (normName.contains("barra fixa") && normName.contains("supinada")) {
        candidates.add("ex_barra_fixa_supinada")
    }
    if (normName.contains("puxada")) {
        when {
            normName.contains("nuca") || normName.contains("atras") || normName.contains("tras") ->
                candidates.add("ex_puxada_por_tras_da_nuca")
            normName.contains("triangulo") ->
                candidates.add("ex_puxada_alta_triangulo")
            normName.contains("unilateral") ->
                candidates.add("ex_puxada_unilateral")
        }
    }
    if (normName.contains("cavalinho") || normName.contains("t-bar") || normName.contains("barra t")) {
        candidates.add("ex_remada_cavalinho_barra_t")
        candidates.add("ex_remada_cavalinho")
    }
    if (normName.contains("remada") && normName.contains("apoio")) {
        candidates.add("ex_remada_apoio_peito")
        candidates.add("ex_remada_na_maquina")
    } else if (normName.contains("remada") && (normName.contains("maquina") || normName.contains("articulada"))) {
        candidates.add("ex_remada_na_maquina")
        candidates.add("ex_remada_maquina")
    }
    if (normName.contains("remada curvada") && normName.contains("halter")) {
        candidates.add("ex_remada_curvada_halteres")
    }
    if (normName.contains("remada alta")) {
        if (normName.contains("polia") || normName.contains("cabo") || normName.contains("cross")) {
            candidates.add("ex_remada_alta_polia")
        }
        candidates.add("ex_remada_alta_barra")
        candidates.add("ex_remada_alta")
    }
    if (normName.contains("encolhimento")) {
        when {
            normName.contains("smith") -> candidates.add("ex_encolhimento_smith")
            normName.contains("barra") -> candidates.add("ex_encolhimento_barra")
            else -> candidates.add("ex_encolhimento_ombros")
        }
    }
    if (normName.contains("supino")) {
        when {
            normName.contains("declinado") -> {
                candidates.add("ex_supino_declinado_barra")
                candidates.add("ex_supino_maquina")
            }
            normName.contains("inclinado") && normName.contains("barra") -> {
                candidates.add("ex_supino_inclinado_barra")
                candidates.add("ex_supino_inclinado")
            }
            normName.contains("inclinado") && normName.contains("halter") -> {
                candidates.add("ex_supino_inclinado_halteres")
                candidates.add("ex_supino_inclinado")
            }
            normName.contains("maquina") || normName.contains("articulado") -> {
                candidates.add("ex_supino_maquina")
            }
            normName.contains("halter") -> {
                candidates.add("ex_supino_reto_halteres")
                candidates.add("ex_supino_reto")
            }
        }
    }
    if (normName.contains("pullover")) {
        candidates.add("ex_pullover_halteres")
    }
    if (normName.contains("flexao") && normName.contains("diamante")) {
        candidates.add("ex_flexao_diamante")
    }
    if (normName.contains("desenvolvimento")) {
        when {
            normName.contains("arnold") -> candidates.add("ex_desenvolvimento_arnold")
            normName.contains("militar") || normName.contains("barra") -> candidates.add("ex_desenvolvimento_militar")
            normName.contains("maquina") || normName.contains("articulado") -> candidates.add("ex_desenvolvimento_maquina")
            else -> candidates.add("ex_desenvolvimento_ombros")
        }
    }
    if (normName.contains("elevacao lateral") || normName.contains("lateral raise")) {
        if (normName.contains("polia") || normName.contains("cabo") || normName.contains("cross")) {
            candidates.add("ex_elevacao_lateral_polia")
        } else {
            candidates.add("ex_elevacao_lateral")
        }
    }
    if (normName.contains("elevacao frontal") || normName.contains("front raise")) {
        when {
            normName.contains("barra") -> candidates.add("ex_elevacao_frontal_barra")
            normName.contains("cabo") || normName.contains("polia") || normName.contains("cross") -> candidates.add("ex_elevacao_frontal_cabo")
            else -> candidates.add("ex_elevacao_frontal")
        }
    }
    if (normName.contains("crucifixo invertido") || normName.contains("crucifixo inverso") || normName.contains("voador inverso")) {
        candidates.add("ex_crucifixo_invertido")
    } else if (normName.contains("crucifixo")) {
        if (normName.contains("inclinado")) {
            candidates.add("ex_crucifixo_inclinado")
        } else {
            candidates.add("ex_crucifixo_reto")
        }
    }
    if (normName.contains("rosca")) {
        when {
            normName.contains("concentrada") -> candidates.add("ex_rosca_concentrada")
            normName.contains("scott") && normName.contains("halter") -> {
                candidates.add("ex_rosca_scott_halteres")
                candidates.add("ex_rosca_scott")
            }
            normName.contains("scott") -> candidates.add("ex_rosca_scott")
            normName.contains("alternada") -> candidates.add("ex_rosca_alternada")
            normName.contains("martelo") || normName.contains("hammer") -> candidates.add("ex_rosca_martelo")
            normName.contains("inversa") || normName.contains("invertida") -> candidates.add("ex_rosca_inversa")
            normName.contains("cabo") || normName.contains("polia") || normName.contains("cross") || normName.contains("corda") -> candidates.add("ex_rosca_cabo")
        }
    }
    if (normName.contains("triceps") || normName.contains("kickback") || normName.contains("coice")) {
        when {
            normName.contains("testa") || normName.contains("skull") -> candidates.add("ex_triceps_testa")
            normName.contains("frances") -> candidates.add("ex_triceps_frances")
            normName.contains("banco") -> candidates.add("ex_triceps_banco")
            normName.contains("kickback") || normName.contains("coice") -> candidates.add("ex_triceps_coice_kickback")
            normName.contains("corda") -> {
                candidates.add("ex_triceps_corda_polia")
                candidates.add("ex_triceps_corda")
                candidates.add("ex_triceps_pulley")
            }
            normName.contains("pulley") || normName.contains("polia") || normName.contains("cross") -> {
                candidates.add("ex_triceps_pulley")
                candidates.add("ex_triceps_corda_polia")
            }
        }
    }

    // Core, Estabilização e Lombar específicos
    if ((normName.contains("infra") || (normName.contains("elevacao") && normName.contains("pernas"))) &&
        (normName.contains("solo") || normName.contains("chao") || normName.contains("colchonete"))
    ) {
        candidates.remove("ex_abdominal_infra")
        candidates.remove("ex_elevacao_pernas_suspenso")
        candidates.add("ex_dead_bug")
        candidates.add("ex_abdominal_supra")
    } else if ((normName.contains("elevacao") && normName.contains("pernas")) && (normName.contains("suspens") || normName.contains("barra") || normName.contains("paralela"))) {
        candidates.add("ex_elevacao_pernas_suspenso")
        candidates.add("ex_abdominal_infra")
    } else if (normName.contains("infra") && (normName.contains("paralela") || normName.contains("barra") || normName.contains("suspens"))) {
        candidates.add("ex_elevacao_pernas_suspenso")
        candidates.add("ex_abdominal_infra")
    }
    if (normName.contains("bird") || normName.contains("perdigueiro")) {
        candidates.add("ex_bird_dog")
    }
    if (normName.contains("dead bug") || normName.contains("deadbug")) {
        candidates.add("ex_dead_bug")
    }
    if (normName.contains("pallof")) {
        candidates.add("ex_pallof_press")
    }
    if (normName.contains("rollout") || normName.contains("ab wheel") || normName.contains("abdominal roda") || normName.contains("roda abdominal")) {
        candidates.add("ex_rollout_no_cabo")
        candidates.add("ex_abdominal_roda")
    }
    if (normName.contains("hiperextensao")) {
        candidates.add("ex_hiperextensao_lombar")
        candidates.add("ex_superman")
    } else if (normName.contains("superman") || normName.contains("bom dia") || normName.contains("good morning")) {
        candidates.add("ex_superman")
        candidates.add("ex_hiperextensao_lombar")
    }
    if (normName.contains("vacuo") || normName.contains("vacuum")) {
        candidates.add("ex_vacuo_abdominal")
    }

    // Alongamentos e Mobilidade específicos na pasta res/drawable/
    if (normName.contains("rotacao toracica")) {
        candidates.add("ex_rotacao_toracica")
        candidates.add("ex_alongamento_lombar")
    }
    if (normName.contains("90/90") || normName.contains("90_90") || normName.contains("90 graus") || normName.contains("quadril")) {
        candidates.add("ex_alongamento_90_graus_coxa")
        candidates.add("ex_alongamento_quadriceps")
    }
    if (normName.contains("alongamento") && normName.contains("posterior") && normName.contains("lombar")) {
        candidates.add("ex_alongamento_posterior_e_lombar")
        candidates.add("ex_alongamento_lombar")
    }
    if (normName.contains("descompressao") || normName.contains("cat-cow") || normName.contains("child") || (normName.contains("alongamento") && normName.contains("lombar"))) {
        candidates.add("ex_alongamento_lombar")
        candidates.add("ex_alongamento_posterior_e_lombar")
    }
    if ((normName.contains("alongamento") || normName.contains("mobilidade")) && (normName.contains("posterior") || normName.contains("isquiotibiais") || normName.contains("tornozelo"))) {
        candidates.add("ex_alongamento_posterior_coxa")
        candidates.add("ex_alongamento_posterior_e_lombar")
    }
    if (normName.contains("alongamento") && normName.contains("quadriceps")) {
        candidates.add("ex_alongamento_quadriceps")
        candidates.add("ex_alongamento_90_graus_coxa")
    }
    if (normName.contains("alongamento geral")) {
        candidates.add("ex_alongamento_lombar")
        candidates.add("ex_alongamento_posterior_e_lombar")
    }

    // Sessões de Corrida / Cardio específicas na pasta res/drawable/
    if (normName.contains("esteira") || normName.contains("treadmill") || normName.contains("incline walk")) {
        candidates.add("ex_treino_na_esteira")
    }
    if (normName.contains("longao") || normName.contains("10k") || normName.contains("endurance")) {
        candidates.add("ex_longao_run")
        candidates.add("ex_tempo_run")
    }
    if (normName.contains("tempo run") || normName.contains("5k") || normName.contains("30 minutos")) {
        candidates.add("ex_tempo_run")
        candidates.add("ex_corrida_ar_livre")
    }
    if (normName.contains("ar livre") || normName.contains("rodagem") || (normName.contains("corrida") && !normName.contains("esteira"))) {
        candidates.add("ex_corrida_ar_livre")
    }
    if (normName.contains("caminhada") || normName.contains("liss") || normName.contains("recovery") || normName.contains("descanso ativo")) {
        candidates.add("ex_caminhada")
        candidates.add("ex_active_recovery_walk")
    }

    // 2.5. Variações automáticas sem sufixo de equipamento (fallback antes do bloco genérico)
    val strippedSlug = compactSlug
        .replace(Regex("_(barra_w|barra_t|t_bar|barra|halteres|halter|polia_media|polia|cabo|maquina|smith|45_graus|45|solo|pronada|supinada|neutra|sentado|em_pe|tradicional|convencional|livre)$"), "")
    if (strippedSlug.isNotBlank() && strippedSlug != "ex_") {
        candidates.add(strippedSlug)
        candidates.add("${strippedSlug}_sentado")
        candidates.add("${strippedSlug}_tradicional")
        candidates.add("${strippedSlug}_maquina")
        candidates.add("${strippedSlug}_hack_machine")
        candidates.add("${strippedSlug}_barra_t")
        candidates.add("${strippedSlug}_livre")
    }

    // 3. Chave primária exata do bloco correspondente
    candidates.add(illustrationType.primaryDrawableKey)

    // 4. Aliases secundários de segurança
    candidates.addAll(illustrationType.drawableAliases)

    return candidates.toList()
}

/**
 * Procura nativamente no pacote Android (`app/src/main/res/drawable/`) o Resource ID do avatar biomecânico.
 * Retorna `0` caso o arquivo PNG/WebP correspondente ainda não esteja em `res/drawable/` (acionando o fallback vetorial 3D).
 */
fun resolveBiomechanicDrawableResId(
    context: Context,
    exerciseName: String,
    muscleGroup: String,
    illustrationType: ExerciseIllustrationType = resolveExerciseIllustrationType(exerciseName, muscleGroup),
    explicitIllustrationId: String? = null
): Int {
    val candidates = resolveBiomechanicDrawableCandidates(
        exerciseName = exerciseName,
        muscleGroup = muscleGroup,
        illustrationType = illustrationType,
        explicitIllustrationId = explicitIllustrationId
    )
    val pkg = context.packageName
    val res = context.resources
    for (key in candidates) {
        val id = try {
            res.getIdentifier(key, "drawable", pkg)
        } catch (_: Exception) {
            0
        }
        if (id != 0) return id
    }
    return 0
}

/**
 * Build #54 (v5.4) — Motor Unificado de Miniaturas Biomecânicas (32 Avatares PNG + Fallback Vetorial 3D)
 * com estética "Warm Obsidian & Copper Glass UI".
 *
 * - Carrega nativamente de `app/src/main/res/drawable/ex_<exercicio>.png` usando o dicionário dos 4 blocos (32 avatares).
 * - Caso o PNG ainda não esteja no diretório `res/drawable/`, renderiza a ilustração vetorial 3D em Canvas.
 */
@Composable
fun ExerciseIllustrationThumbnail(
    exerciseName: String,
    muscleGroup: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    showZoomBadge: Boolean = false,
    equipment: String = "",
    notes: String = "",
    illustrationId: String? = null,
    enableDetailModalOnTap: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val illustrationType = remember(exerciseName, muscleGroup) {
        resolveExerciseIllustrationType(exerciseName, muscleGroup)
    }

    val drawableResId = remember(exerciseName, muscleGroup, illustrationId) {
        resolveBiomechanicDrawableResId(
            context = context,
            exerciseName = exerciseName,
            muscleGroup = muscleGroup,
            illustrationType = illustrationType,
            explicitIllustrationId = illustrationId
        )
    }

    var showDetailModal by remember { mutableStateOf(false) }

    // Borda Especular (Rim Lighting): Cobre Iluminado (#FF8A3D) no topo esquerdo -> quase transparente na base direita
    val specularRimBorderBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                CopperRim.copy(alpha = 0.42f),
                WarmPillSurface.copy(alpha = 0.70f),
                CopperRim.copy(alpha = 0.08f)
            )
        )
    }

    val boxModifier = modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(WarmCardTop)
        .border(
            width = 1.dp,
            brush = specularRimBorderBrush,
            shape = RoundedCornerShape(cornerRadius)
        )
        .then(
            if (onClick != null || enableDetailModalOnTap) {
                Modifier.clickable {
                    com.example.utils.AudioSynth.playTickSound()
                    if (onClick != null) {
                        onClick()
                    } else {
                        showDetailModal = true
                    }
                }
            } else {
                Modifier
            }
        )

    Box(
        modifier = boxModifier.testTag("exercise_illustration_thumbnail"),
        contentAlignment = Alignment.Center
    ) {
        if (drawableResId != 0) {
            // Renderização nativa fluida do Avatar Biomecânico PNG recém-cortado (res/drawable/ex_*.png)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(WarmCardTop, WarmCardBottom, WarmObsidianDeep)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = drawableResId),
                    contentDescription = exerciseName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Vinheta sutil nas bordas para fusão perfeita com o card Warm Obsidian
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    WarmObsidianDeep.copy(alpha = 0.28f)
                                )
                            )
                        )
                )
            }
        } else {
            // Fallback Vetorial 3D em Canvas quando o PNG ainda não foi copiado para res/drawable/
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // 1. Fundo Warm Obsidian & Expresso Profundo (#1C130E -> #140D09 -> #110C09)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(WarmCardTop, WarmCardBottom, WarmObsidianDeep)
                    )
                )

                // 2. Ambient Copper Glow radial no topo e centro difundindo suavemente para o fundo escuro
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaxOrange.copy(alpha = 0.22f),
                            CopperRim.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.50f, h * 0.36f),
                        radius = w * 0.62f
                    ),
                    radius = w * 0.62f,
                    center = Offset(w * 0.50f, h * 0.36f)
                )

                // 3. Linha de piso 3D quente sutil
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, WarmSandSecondary.copy(alpha = 0.20f), Color.Transparent)
                    ),
                    start = Offset(w * 0.12f, h * 0.86f),
                    end = Offset(w * 0.88f, h * 0.86f),
                    strokeWidth = (w * 0.018f).coerceAtLeast(1.5f),
                    cap = StrokeCap.Round
                )

                // 4. Desenho específico dos 4 blocos de treino
                when (illustrationType) {
                    // Bloco 1: Pernas e Glúteos (8)
                    ExerciseIllustrationType.SQUAT -> drawSquatIllustration(w, h)
                    ExerciseIllustrationType.LEG_PRESS -> drawLegPressIllustration(w, h)
                    ExerciseIllustrationType.LEG_EXTENSION -> drawLegExtensionIllustration(w, h)
                    ExerciseIllustrationType.LEG_CURL -> drawLegCurlIllustration(w, h)
                    ExerciseIllustrationType.STIFF_RDL -> drawStiffIllustration(w, h)
                    ExerciseIllustrationType.LUNGE_BULGARIAN -> drawLungeIllustration(w, h)
                    ExerciseIllustrationType.HIP_THRUST -> drawHipThrustIllustration(w, h)
                    ExerciseIllustrationType.CALF_RAISE -> drawCalfRaiseIllustration(w, h)

                    // Bloco 2: Costas e Bíceps (8)
                    ExerciseIllustrationType.SEATED_CABLE_ROW -> drawSeatedCableRowIllustration(w, h)
                    ExerciseIllustrationType.LAT_PULLDOWN -> drawLatPulldownIllustration(w, h)
                    ExerciseIllustrationType.BENT_OVER_ROW -> drawBentOverRowIllustration(w, h)
                    ExerciseIllustrationType.ONE_ARM_DUMBBELL_ROW -> drawOneArmDumbbellRowIllustration(w, h)
                    ExerciseIllustrationType.PULL_UP_CHIN_UP -> drawPullUpChinUpIllustration(w, h)
                    ExerciseIllustrationType.BARBELL_CURL -> drawBarbellCurlIllustration(w, h)
                    ExerciseIllustrationType.DUMBBELL_HAMMER_CURL -> drawDumbbellHammerCurlIllustration(w, h)
                    ExerciseIllustrationType.PREACHER_CONCENTRATION_CURL -> drawPreacherCurlIllustration(w, h)

                    // Bloco 3: Peito, Tríceps e Ombros (8)
                    ExerciseIllustrationType.BENCH_PRESS,
                    ExerciseIllustrationType.INCLINE_BENCH_PRESS,
                    ExerciseIllustrationType.CHEST_FLYE_PECK_DECK,
                    ExerciseIllustrationType.CABLE_CROSSOVER,
                    ExerciseIllustrationType.PUSH_UP -> drawBenchPressIllustration(w, h)

                    ExerciseIllustrationType.TRICEPS_DIPS_EXTENSION -> drawTricepsIllustration(w, h)

                    ExerciseIllustrationType.SHOULDER_PRESS,
                    ExerciseIllustrationType.LATERAL_RAISE -> drawShoulderPressIllustration(w, h)

                    // Bloco 4: Ombros, Trapézio, Core, Descanso Ativo & Sprint (8)
                    ExerciseIllustrationType.FRONT_RAISE,
                    ExerciseIllustrationType.FACE_PULL_REVERSE_FLYE,
                    ExerciseIllustrationType.SHRUG_TRAPEZIUS -> drawShoulderPressIllustration(w, h)

                    ExerciseIllustrationType.ABDOMINAL_SUPRA,
                    ExerciseIllustrationType.ABDOMINAL_INFRA,
                    ExerciseIllustrationType.PLANK_ISOMETRIC,
                    ExerciseIllustrationType.ACTIVE_RECOVERY_WALK -> drawCoreAbsIllustration(w, h)

                    ExerciseIllustrationType.SPRINT_INTERVAL -> drawLungeIllustration(w, h)
                }
            }
        }

        // Ícone de lupa discreto quando showZoomBadge = true
        if (showZoomBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(WarmObsidianDeep.copy(alpha = 0.88f))
                    .border(0.8.dp, CopperRim.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Ampliar Ilustração",
                    tint = MaxOrange,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }

    if (showDetailModal) {
        ExerciseIllustrationDetailDialog(
            exerciseName = exerciseName,
            muscleGroup = muscleGroup,
            equipment = equipment,
            notes = notes,
            illustrationId = illustrationId,
            onDismiss = { showDetailModal = false }
        )
    }
}

/**
 * Modal/Dialog ampliado mostrando o Avatar Biomecânico (ou ilustração vetorial 3D) em alta definição (248.dp)
 * com suporte a Zoom Interativo (1.0x <-> 1.85x por toque duplo, botão ou pinça), chave mapeada do bloco
 * e dicas rápidas de execução biomecânica.
 */
@Composable
fun ExerciseIllustrationDetailDialog(
    exerciseName: String,
    muscleGroup: String,
    equipment: String = "",
    notes: String = "",
    illustrationId: String? = null,
    onDismiss: () -> Unit
) {
    val illustrationType = remember(exerciseName, muscleGroup) {
        resolveExerciseIllustrationType(exerciseName, muscleGroup)
    }

    var isZoomedIn by remember { mutableStateOf(false) }
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val targetScale = if (isZoomedIn) 1.75f else pinchScale
    val animatedScale by animateFloatAsState(
        targetValue = targetScale.coerceIn(1f, 2.4f),
        animationSpec = tween(durationMillis = 220),
        label = "avatar_zoom_scale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(22.dp),
            color = WarmCardTop,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        CopperRim.copy(alpha = 0.38f),
                        WarmPillSurface,
                        CopperRim.copy(alpha = 0.06f)
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(WarmCardTop, WarmCardBottom)
                        )
                    )
            ) {
                // Ambient Copper Glow no topo do modal
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaxOrange.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                radius = 420f
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exerciseName,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${illustrationType.title} • Bloco ${illustrationType.blockNumber}/4",
                                color = MaxOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(WarmPillSurface, CircleShape)
                                .border(1.dp, CopperRim.copy(alpha = 0.25f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = WarmSandSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Ilustração Ampliada em tamanho grande (248.dp) com suporte a Zoom Fluido (Pinça / Duplo Toque / Botão)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(248.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(WarmObsidianDeep)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        CopperRim.copy(alpha = 0.45f),
                                        WarmPillSurface,
                                        CopperRim.copy(alpha = 0.10f)
                                    )
                                ),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        isZoomedIn = !isZoomedIn
                                        if (!isZoomedIn) {
                                            pinchScale = 1f
                                            panOffset = Offset.Zero
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (pinchScale * zoom).coerceIn(1f, 2.4f)
                                    pinchScale = newScale
                                    isZoomedIn = newScale > 1.15f
                                    if (newScale > 1.05f) {
                                        panOffset = Offset(
                                            x = (panOffset.x + pan.x).coerceIn(-140f, 140f),
                                            y = (panOffset.y + pan.y).coerceIn(-140f, 140f)
                                        )
                                    } else {
                                        panOffset = Offset.Zero
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(248.dp)
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    translationX = if (animatedScale > 1.05f) panOffset.x else 0f
                                    translationY = if (animatedScale > 1.05f) panOffset.y else 0f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            ExerciseIllustrationThumbnail(
                                exerciseName = exerciseName,
                                muscleGroup = muscleGroup,
                                cornerRadius = 18.dp,
                                showZoomBadge = false,
                                illustrationId = illustrationId,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Botão de controle de Zoom no canto inferior direito do quadro ampliado
                        Surface(
                            color = WarmObsidianDeep.copy(alpha = 0.86f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CopperRim.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .clickable {
                                    com.example.utils.AudioSynth.playTickSound()
                                    isZoomedIn = !isZoomedIn
                                    if (!isZoomedIn) {
                                        pinchScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isZoomedIn) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                                    contentDescription = "Alternar Zoom",
                                    tint = MaxOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isZoomedIn) "1.0x" else "1.8x",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Chips de Grupo Muscular e Equipamento em Warm Copper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaxOrange.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CopperRim.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "GRUPO: ${muscleGroup.ifBlank { "GERAL" }.uppercase()}",
                                color = MaxOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            color = WarmPillSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CopperRim.copy(alpha = 0.20f))
                        ) {
                            Text(
                                text = "EQUIPAMENTO: ${equipment.ifBlank { "Padrão" }.uppercase()}",
                                color = WarmSandSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Card de Dicas Biomecânicas
                    Surface(
                        color = WarmPillSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    CopperRim.copy(alpha = 0.30f),
                                    CopperRim.copy(alpha = 0.06f)
                                )
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TipsAndUpdates,
                                    contentDescription = null,
                                    tint = MaxOrange,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "FOCO: ${illustrationType.primaryFocus.uppercase()}",
                                    color = MaxOrangeBright,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            val displayTip = if (notes.isNotBlank()) {
                                "${illustrationType.biomechanicalTip}\n\nNota da ficha: $notes"
                            } else {
                                illustrationType.biomechanicalTip
                            }

                            Text(
                                text = displayTip,
                                color = WarmSandSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // Botão FECHAR com volume (#FF8533 -> #E04F00) e borda iluminada no topo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(CopperVolumeTop, CopperVolumeBottom)
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF80D8FF).copy(alpha = 0.65f),
                                        CopperVolumeBottom.copy(alpha = 0.15f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "FECHAR",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// FUNÇÕES AUXILIARES DE DESENHO 3D / CLAY / WARM COPPER GLOW
// =========================================================================

private fun DrawScope.drawClayLimb(
    start: Offset,
    end: Offset,
    thickness: Float,
    isHighlightedMuscle: Boolean = false
) {
    // Sombra 3D inferior quente
    drawLine(
        color = Color.Black.copy(alpha = 0.42f),
        start = Offset(start.x + thickness * 0.1f, start.y + thickness * 0.15f),
        end = Offset(end.x + thickness * 0.1f, end.y + thickness * 0.15f),
        strokeWidth = thickness,
        cap = StrokeCap.Round
    )

    if (isHighlightedMuscle) {
        // Glow externo cobre/laranja
        drawLine(
            color = MaxOrange.copy(alpha = 0.36f),
            start = start,
            end = end,
            strokeWidth = thickness * 1.45f,
            cap = StrokeCap.Round
        )
        // Corpo muscular em gradiente Azul/Ciano Iluminado 3D (#29B6F6 -> #00B0FF -> #0288D1)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(MaxOrangeBright, MaxOrange, Color(0xFF0288D1)),
                start = start,
                end = end
            ),
            start = start,
            end = end,
            strokeWidth = thickness,
            cap = StrokeCap.Round
        )
        // Brilho especular central
        drawLine(
            color = Color.White.copy(alpha = 0.38f),
            start = Offset(start.x, start.y - thickness * 0.12f),
            end = Offset(end.x, end.y - thickness * 0.12f),
            strokeWidth = thickness * 0.28f,
            cap = StrokeCap.Round
        )
    } else {
        // Membro estilo 3D Clay Champagne/Areia Quente
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(ClayBodyLight, ClayBodyMid),
                start = start,
                end = end
            ),
            start = start,
            end = end,
            strokeWidth = thickness,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawClayHead(center: Offset, radius: Float) {
    drawCircle(
        color = Color.Black.copy(alpha = 0.40f),
        radius = radius,
        center = Offset(center.x + radius * 0.12f, center.y + radius * 0.15f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, ClayBodyLight, ClayBodyMid),
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
            radius = radius * 1.3f
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawGlowingPlate(center: Offset, width: Float, height: Float) {
    drawRoundRect(
        color = MaxOrange.copy(alpha = 0.32f),
        topLeft = Offset(center.x - width * 0.65f, center.y - height * 0.58f),
        size = Size(width * 1.3f, height * 1.16f),
        cornerRadius = CornerRadius(width * 0.45f, width * 0.45f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(CopperVolumeTop, MaxOrange, CopperVolumeBottom),
            startY = center.y - height * 0.5f,
            endY = center.y + height * 0.5f
        ),
        topLeft = Offset(center.x - width * 0.5f, center.y - height * 0.5f),
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.35f, width * 0.35f)
    )
}

// =========================================================================
// BLOCO 1 — 8 ILUSTRAÇÕES ESPECÍFICAS DE PERNAS
// =========================================================================

/**
 * 1. Agachamento Livre / Barra (Squat / Agachamento):
 * Boneco estilizado em cócoras com barra olímpica e anilhas nas costas.
 */
private fun DrawScope.drawSquatIllustration(w: Float, h: Float) {
    drawLine(
        color = EquipmentSteel.copy(alpha = 0.6f),
        start = Offset(w * 0.26f, h * 0.16f),
        end = Offset(w * 0.26f, h * 0.85f),
        strokeWidth = w * 0.035f,
        cap = StrokeCap.Round
    )

    val headPos = Offset(w * 0.56f, h * 0.24f)
    val shoulderPos = Offset(w * 0.50f, h * 0.35f)
    val hipPos = Offset(w * 0.34f, h * 0.57f)
    val kneePos = Offset(w * 0.62f, h * 0.60f)
    val anklePos = Offset(w * 0.50f, h * 0.83f)
    val footPos = Offset(w * 0.61f, h * 0.84f)

    drawClayLimb(
        start = Offset(hipPos.x + w * 0.04f, hipPos.y - h * 0.02f),
        end = Offset(kneePos.x + w * 0.05f, kneePos.y - h * 0.02f),
        thickness = w * 0.085f,
        isHighlightedMuscle = false
    )
    drawClayLimb(
        start = Offset(kneePos.x + w * 0.05f, kneePos.y - h * 0.02f),
        end = Offset(anklePos.x + w * 0.06f, anklePos.y - h * 0.01f),
        thickness = w * 0.065f,
        isHighlightedMuscle = false
    )

    drawClayLimb(kneePos, anklePos, w * 0.075f, isHighlightedMuscle = false)
    drawClayLimb(anklePos, footPos, w * 0.055f, isHighlightedMuscle = false)
    drawClayLimb(shoulderPos, hipPos, w * 0.11f, isHighlightedMuscle = false)

    drawCircle(color = MaxOrange, radius = w * 0.068f, center = hipPos)
    drawClayLimb(hipPos, kneePos, w * 0.105f, isHighlightedMuscle = true)
    drawClayHead(headPos, w * 0.075f)

    val elbowPos = Offset(w * 0.44f, h * 0.45f)
    val handPos = Offset(w * 0.39f, h * 0.35f)
    drawClayLimb(shoulderPos, elbowPos, w * 0.055f, isHighlightedMuscle = false)
    drawClayLimb(elbowPos, handPos, w * 0.05f, isHighlightedMuscle = false)

    val barLeft = Offset(w * 0.20f, h * 0.35f)
    val barRight = Offset(w * 0.78f, h * 0.35f)
    drawLine(
        color = ClayBodyLight,
        start = barLeft,
        end = barRight,
        strokeWidth = w * 0.032f,
        cap = StrokeCap.Round
    )
    drawGlowingPlate(Offset(w * 0.24f, h * 0.35f), w * 0.055f, h * 0.18f)
    drawGlowingPlate(Offset(w * 0.30f, h * 0.35f), w * 0.045f, h * 0.14f)
    drawGlowingPlate(Offset(w * 0.74f, h * 0.35f), w * 0.055f, h * 0.18f)
    drawGlowingPlate(Offset(w * 0.68f, h * 0.35f), w * 0.045f, h * 0.14f)
}

/**
 * 2. Leg Press 45° (Leg Press):
 * Estrutura inclinada 45° com plataforma e boneco empurrando com as pernas.
 */
private fun DrawScope.drawLegPressIllustration(w: Float, h: Float) {
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.16f, h * 0.24f),
        end = Offset(w * 0.64f, h * 0.76f),
        strokeWidth = w * 0.045f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = EquipmentHighlight,
        start = Offset(w * 0.56f, h * 0.78f),
        end = Offset(w * 0.74f, h * 0.78f),
        strokeWidth = w * 0.05f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = EquipmentHighlight,
        start = Offset(w * 0.72f, h * 0.78f),
        end = Offset(w * 0.86f, h * 0.58f),
        strokeWidth = w * 0.05f,
        cap = StrokeCap.Round
    )

    val platStart = Offset(w * 0.22f, h * 0.52f)
    val platEnd = Offset(w * 0.42f, h * 0.30f)
    drawLine(
        color = ClayBodyLight,
        start = platStart,
        end = platEnd,
        strokeWidth = w * 0.045f,
        cap = StrokeCap.Round
    )
    drawGlowingPlate(Offset(w * 0.26f, h * 0.32f), w * 0.06f, h * 0.17f)
    drawGlowingPlate(Offset(w * 0.33f, h * 0.26f), w * 0.05f, h * 0.14f)

    val hipPos = Offset(w * 0.64f, h * 0.72f)
    val shoulderPos = Offset(w * 0.78f, h * 0.56f)
    val headPos = Offset(w * 0.83f, h * 0.45f)
    val kneePos = Offset(w * 0.54f, h * 0.48f)
    val footPos = Offset(w * 0.34f, h * 0.42f)

    drawClayLimb(hipPos, shoulderPos, w * 0.10f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.068f)
    drawClayLimb(kneePos, footPos, w * 0.072f, isHighlightedMuscle = false)

    drawCircle(color = MaxOrange, radius = w * 0.06f, center = hipPos)
    drawClayLimb(hipPos, kneePos, w * 0.098f, isHighlightedMuscle = true)
}

/**
 * 3. Cadeira Extensora (Extensora / Leg Extension):
 * Boneco sentado no banco com rolo de espuma nas canelas estendendo os joelhos.
 */
private fun DrawScope.drawLegExtensionIllustration(w: Float, h: Float) {
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.26f, h * 0.30f),
        end = Offset(w * 0.28f, h * 0.62f),
        strokeWidth = w * 0.055f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.28f, h * 0.62f),
        end = Offset(w * 0.56f, h * 0.62f),
        strokeWidth = w * 0.055f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.36f, h * 0.62f),
        end = Offset(w * 0.36f, h * 0.85f),
        strokeWidth = w * 0.045f,
        cap = StrokeCap.Round
    )

    val headPos = Offset(w * 0.35f, h * 0.22f)
    val shoulderPos = Offset(w * 0.34f, h * 0.35f)
    val hipPos = Offset(w * 0.35f, h * 0.56f)
    val kneePos = Offset(w * 0.58f, h * 0.55f)
    val ankleExtended = Offset(w * 0.82f, h * 0.44f)
    val ankleBent = Offset(w * 0.62f, h * 0.80f)

    drawLine(
        color = ClayBodyMid.copy(alpha = 0.28f),
        start = kneePos,
        end = ankleBent,
        strokeWidth = w * 0.065f,
        cap = StrokeCap.Round
    )

    drawLine(
        color = EquipmentHighlight,
        start = kneePos,
        end = ankleExtended,
        strokeWidth = w * 0.03f,
        cap = StrokeCap.Round
    )

    drawClayLimb(shoulderPos, hipPos, w * 0.105f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.072f)
    drawClayLimb(kneePos, ankleExtended, w * 0.075f, isHighlightedMuscle = false)
    drawClayLimb(hipPos, kneePos, w * 0.108f, isHighlightedMuscle = true)

    drawCircle(color = EquipmentHighlight, radius = w * 0.038f, center = kneePos)
    drawCircle(color = MaxOrange, radius = w * 0.055f, center = Offset(w * 0.79f, h * 0.41f))
    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = w * 0.02f, center = Offset(w * 0.79f, h * 0.41f))
}

/**
 * 4. Mesa / Cadeira Flexora (Flexora / Leg Curl):
 * Boneco deitado de bruços na mesa flexionando os joelhos contra o rolo com destaque nos posteriores.
 */
private fun DrawScope.drawLegCurlIllustration(w: Float, h: Float) {
    val benchLeft = Offset(w * 0.15f, h * 0.62f)
    val benchMid = Offset(w * 0.52f, h * 0.56f)
    val benchRight = Offset(w * 0.84f, h * 0.64f)

    drawLine(color = EquipmentSteel, start = benchLeft, end = benchMid, strokeWidth = w * 0.055f, cap = StrokeCap.Round)
    drawLine(color = EquipmentSteel, start = benchMid, end = benchRight, strokeWidth = w * 0.055f, cap = StrokeCap.Round)
    drawLine(color = EquipmentSteel, start = Offset(w * 0.25f, h * 0.61f), end = Offset(w * 0.25f, h * 0.85f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
    drawLine(color = EquipmentSteel, start = Offset(w * 0.74f, h * 0.62f), end = Offset(w * 0.74f, h * 0.85f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)

    val headPos = Offset(w * 0.18f, h * 0.46f)
    val shoulderPos = Offset(w * 0.28f, h * 0.52f)
    val hipPos = Offset(w * 0.52f, h * 0.49f)
    val kneePos = Offset(w * 0.76f, h * 0.56f)
    val heelCurledPos = Offset(w * 0.66f, h * 0.26f)

    drawLine(
        color = ClayBodyMid.copy(alpha = 0.25f),
        start = kneePos,
        end = Offset(w * 0.90f, h * 0.60f),
        strokeWidth = w * 0.06f,
        cap = StrokeCap.Round
    )

    drawClayLimb(shoulderPos, hipPos, w * 0.10f, isHighlightedMuscle = false)
    drawClayLimb(shoulderPos, Offset(w * 0.16f, h * 0.60f), w * 0.05f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.068f)
    drawClayLimb(kneePos, heelCurledPos, w * 0.072f, isHighlightedMuscle = false)
    drawClayLimb(hipPos, kneePos, w * 0.102f, isHighlightedMuscle = true)

    drawLine(color = EquipmentHighlight, start = kneePos, end = heelCurledPos, strokeWidth = w * 0.025f, cap = StrokeCap.Round)
    drawCircle(color = MaxOrange, radius = w * 0.055f, center = Offset(w * 0.63f, h * 0.28f))
    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = w * 0.02f, center = Offset(w * 0.63f, h * 0.28f))
}

/**
 * 5. Stiff / Terra Romeno (Stiff / RDL / Levantamento Terra):
 * Boneco em hip hinge (tronco inclinado) segurando barra/halteres próximo às canelas com destaque em posteriores e glúteos.
 */
private fun DrawScope.drawStiffIllustration(w: Float, h: Float) {
    val headPos = Offset(w * 0.72f, h * 0.30f)
    val shoulderPos = Offset(w * 0.60f, h * 0.36f)
    val hipPos = Offset(w * 0.26f, h * 0.44f)
    val kneePos = Offset(w * 0.32f, h * 0.64f)
    val anklePos = Offset(w * 0.34f, h * 0.83f)
    val footPos = Offset(w * 0.44f, h * 0.84f)

    drawClayLimb(kneePos, anklePos, w * 0.072f, isHighlightedMuscle = false)
    drawClayLimb(anklePos, footPos, w * 0.055f, isHighlightedMuscle = false)
    drawClayLimb(hipPos, shoulderPos, w * 0.105f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.072f)

    drawCircle(color = MaxOrange, radius = w * 0.068f, center = hipPos)
    drawClayLimb(hipPos, kneePos, w * 0.10f, isHighlightedMuscle = true)

    val handPos = Offset(w * 0.56f, h * 0.66f)
    drawClayLimb(shoulderPos, handPos, w * 0.055f, isHighlightedMuscle = false)

    drawGlowingPlate(Offset(w * 0.56f, h * 0.68f), w * 0.16f, h * 0.16f)
    drawCircle(color = ClayBodyLight, radius = w * 0.032f, center = Offset(w * 0.56f, h * 0.68f))
}

/**
 * 6. Afundo / Passada / Agachamento Búlgaro (Lunge / Split Squat):
 * Boneco em passada unilateral (joelho da frente a 90° e perna de trás flexionada) com halteres.
 */
private fun DrawScope.drawLungeIllustration(w: Float, h: Float) {
    drawRoundRect(
        color = EquipmentSteel.copy(alpha = 0.65f),
        topLeft = Offset(w * 0.12f, h * 0.68f),
        size = Size(w * 0.16f, h * 0.17f),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
    )

    val headPos = Offset(w * 0.50f, h * 0.19f)
    val shoulderPos = Offset(w * 0.48f, h * 0.31f)
    val hipPos = Offset(w * 0.46f, h * 0.53f)

    val backKnee = Offset(w * 0.34f, h * 0.75f)
    val backFoot = Offset(w * 0.20f, h * 0.72f)
    drawClayLimb(hipPos, backKnee, w * 0.082f, isHighlightedMuscle = false)
    drawClayLimb(backKnee, backFoot, w * 0.068f, isHighlightedMuscle = false)

    val frontKnee = Offset(w * 0.72f, h * 0.56f)
    val frontAnkle = Offset(w * 0.72f, h * 0.83f)
    val frontFoot = Offset(w * 0.82f, h * 0.84f)
    drawClayLimb(frontKnee, frontAnkle, w * 0.075f, isHighlightedMuscle = false)
    drawClayLimb(frontAnkle, frontFoot, w * 0.055f, isHighlightedMuscle = false)

    drawClayLimb(shoulderPos, hipPos, w * 0.105f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.072f)

    drawCircle(color = MaxOrange, radius = w * 0.062f, center = hipPos)
    drawClayLimb(hipPos, frontKnee, w * 0.102f, isHighlightedMuscle = true)

    val handPos = Offset(w * 0.48f, h * 0.56f)
    drawClayLimb(shoulderPos, handPos, w * 0.052f, isHighlightedMuscle = false)

    drawGlowingPlate(Offset(w * 0.42f, h * 0.56f), w * 0.045f, h * 0.11f)
    drawGlowingPlate(Offset(w * 0.54f, h * 0.56f), w * 0.045f, h * 0.11f)
    drawLine(color = ClayBodyLight, start = Offset(w * 0.42f, h * 0.56f), end = Offset(w * 0.54f, h * 0.56f), strokeWidth = w * 0.025f)
}

/**
 * 7. Elevação Pélvica / Hip Thrust (Glúteos):
 * Boneco com escápulas apoiadas no banco elevando o quadril com barra e anilhas sobre a pelve.
 */
private fun DrawScope.drawHipThrustIllustration(w: Float, h: Float) {
    drawRoundRect(
        color = EquipmentSteel,
        topLeft = Offset(w * 0.12f, h * 0.52f),
        size = Size(w * 0.24f, h * 0.33f),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
    )

    val headPos = Offset(w * 0.20f, h * 0.36f)
    val shoulderPos = Offset(w * 0.28f, h * 0.48f)
    val hipPos = Offset(w * 0.54f, h * 0.48f)
    val kneePos = Offset(w * 0.78f, h * 0.49f)
    val anklePos = Offset(w * 0.78f, h * 0.83f)
    val footPos = Offset(w * 0.86f, h * 0.84f)

    drawClayLimb(kneePos, anklePos, w * 0.075f, isHighlightedMuscle = false)
    drawClayLimb(anklePos, footPos, w * 0.055f, isHighlightedMuscle = false)
    drawClayLimb(shoulderPos, hipPos, w * 0.105f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.068f)

    drawClayLimb(hipPos, kneePos, w * 0.102f, isHighlightedMuscle = true)
    drawCircle(color = MaxOrangeBright, radius = w * 0.072f, center = Offset(hipPos.x, hipPos.y + h * 0.02f))

    drawGlowingPlate(Offset(w * 0.54f, h * 0.36f), w * 0.15f, h * 0.15f)
    drawCircle(color = ClayBodyLight, radius = w * 0.03f, center = Offset(w * 0.54f, h * 0.36f))
}

/**
 * 8. Panturrilha em Pé / Sentado (Calf Raise):
 * Boneco na ponta dos pés sobre plataforma/degrau com destaque laranja iluminado na panturrilha.
 */
private fun DrawScope.drawCalfRaiseIllustration(w: Float, h: Float) {
    drawRoundRect(
        color = EquipmentSteel,
        topLeft = Offset(w * 0.34f, h * 0.76f),
        size = Size(w * 0.38f, h * 0.10f),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
    )

    val headPos = Offset(w * 0.50f, h * 0.16f)
    val shoulderPos = Offset(w * 0.50f, h * 0.27f)
    val hipPos = Offset(w * 0.48f, h * 0.47f)
    val kneePos = Offset(w * 0.50f, h * 0.61f)
    val heelHighPos = Offset(w * 0.44f, h * 0.71f)
    val toeStepPos = Offset(w * 0.58f, h * 0.76f)

    drawClayLimb(shoulderPos, hipPos, w * 0.095f, isHighlightedMuscle = false)
    drawClayLimb(hipPos, kneePos, w * 0.09f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.065f)

    drawGlowingPlate(Offset(w * 0.50f, h * 0.26f), w * 0.22f, h * 0.055f)
    drawClayLimb(heelHighPos, toeStepPos, w * 0.055f, isHighlightedMuscle = false)

    drawClayLimb(kneePos, heelHighPos, w * 0.095f, isHighlightedMuscle = true)
    drawCircle(color = MaxOrangeBright, radius = w * 0.058f, center = Offset(w * 0.45f, h * 0.64f))
}

// =========================================================================
// BLOCO 2 — 8 ILUSTRAÇÕES ESPECÍFICAS DE COSTAS E BÍCEPS (BUILD #54)
// =========================================================================

/**
 * 1. Remada Baixa na Polia / Triângulo (Seated Cable Row):
 * Boneco sentado tracionando o cabo horizontal na polia baixa com destaque nas dorsais.
 */
private fun DrawScope.drawSeatedCableRowIllustration(w: Float, h: Float) {
    // Coluna da Polia Baixa à esquerda + Plataforma de apoio dos pés
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.16f, h * 0.22f),
        end = Offset(w * 0.16f, h * 0.85f),
        strokeWidth = w * 0.048f,
        cap = StrokeCap.Round
    )
    // Banco longo horizontal
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.18f, h * 0.74f),
        end = Offset(w * 0.84f, h * 0.74f),
        strokeWidth = w * 0.048f,
        cap = StrokeCap.Round
    )
    // Plataforma vertical dos pés
    drawLine(
        color = EquipmentHighlight,
        start = Offset(w * 0.30f, h * 0.58f),
        end = Offset(w * 0.30f, h * 0.74f),
        strokeWidth = w * 0.04f,
        cap = StrokeCap.Round
    )

    // Cabo horizontal tracionado da polia até o abdômen
    val pulleyOrigin = Offset(w * 0.18f, h * 0.52f)
    val handlePos = Offset(w * 0.58f, h * 0.52f)
    drawLine(
        color = EquipmentHighlight,
        start = pulleyOrigin,
        end = handlePos,
        strokeWidth = w * 0.022f
    )
    // Roldana laranja na polia baixa
    drawCircle(color = MaxOrange, radius = w * 0.035f, center = pulleyOrigin)

    // Boneco sentado ereto tracionando o triângulo
    val headPos = Offset(w * 0.70f, h * 0.24f)
    val shoulderPos = Offset(w * 0.68f, h * 0.36f)
    val hipPos = Offset(w * 0.68f, h * 0.68f)
    val kneePos = Offset(w * 0.48f, h * 0.60f)
    val footPos = Offset(w * 0.32f, h * 0.66f)
    val elbowPos = Offset(w * 0.78f, h * 0.50f)

    // Pernas semiflexionadas apoiadas na plataforma
    drawClayLimb(hipPos, kneePos, w * 0.088f, isHighlightedMuscle = false)
    drawClayLimb(kneePos, footPos, w * 0.072f, isHighlightedMuscle = false)

    // Dorsal e Meio das Costas em DESTAQUE COBRE/LARANJA
    drawClayLimb(shoulderPos, hipPos, w * 0.118f, isHighlightedMuscle = true)
    drawClayHead(headPos, w * 0.068f)

    // Braço puxando o cotovelo para trás e segurando o triângulo
    drawClayLimb(shoulderPos, elbowPos, w * 0.065f, isHighlightedMuscle = true)
    drawClayLimb(elbowPos, handlePos, w * 0.055f, isHighlightedMuscle = false)

    // Puxador Triângulo iluminado
    drawGlowingPlate(handlePos, w * 0.07f, h * 0.09f)
}

/**
 * 2. Puxada Alta / Pulldown (Puxada / Pulldown):
 * Boneco sentado puxando a barra da polia alta em direção ao peito com destaque no grande dorsal.
 */
private fun DrawScope.drawLatPulldownIllustration(w: Float, h: Float) {
    // Torre de Polia Alta (Pulley)
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.22f, h * 0.14f),
        end = Offset(w * 0.22f, h * 0.85f),
        strokeWidth = w * 0.045f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.22f, h * 0.14f),
        end = Offset(w * 0.58f, h * 0.14f),
        strokeWidth = w * 0.042f,
        cap = StrokeCap.Round
    )
    // Cabo vertical descendo da polia alta
    drawLine(
        color = EquipmentHighlight,
        start = Offset(w * 0.54f, h * 0.14f),
        end = Offset(w * 0.54f, h * 0.33f),
        strokeWidth = w * 0.022f
    )
    // Barra larga curvada do Pulley em Cobre Iluminado
    drawLine(
        color = MaxOrangeBright,
        start = Offset(w * 0.34f, h * 0.33f),
        end = Offset(w * 0.74f, h * 0.33f),
        strokeWidth = w * 0.036f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = MaxOrange,
        start = Offset(w * 0.34f, h * 0.33f),
        end = Offset(w * 0.30f, h * 0.38f),
        strokeWidth = w * 0.034f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = MaxOrange,
        start = Offset(w * 0.74f, h * 0.33f),
        end = Offset(w * 0.78f, h * 0.38f),
        strokeWidth = w * 0.034f,
        cap = StrokeCap.Round
    )

    // Boneco sentado puxando a barra com destaque nas Dorsais em V
    val headPos = Offset(w * 0.55f, h * 0.42f)
    val shoulderLeft = Offset(w * 0.46f, h * 0.52f)
    val shoulderRight = Offset(w * 0.64f, h * 0.52f)
    val shoulderCenter = Offset(w * 0.55f, h * 0.52f)
    val hipPos = Offset(w * 0.55f, h * 0.75f)
    val kneePos = Offset(w * 0.36f, h * 0.74f)
    val footPos = Offset(w * 0.36f, h * 0.85f)

    // Pernas presas sob o rolo de apoio
    drawClayLimb(hipPos, kneePos, w * 0.08f, isHighlightedMuscle = false)
    drawClayLimb(kneePos, footPos, w * 0.065f, isHighlightedMuscle = false)
    drawCircle(color = EquipmentHighlight, radius = w * 0.038f, center = Offset(w * 0.39f, h * 0.68f))

    // Braços abertos puxando a barra até a altura do queixo/peito
    drawClayLimb(shoulderLeft, Offset(w * 0.34f, h * 0.35f), w * 0.055f, isHighlightedMuscle = false)
    drawClayLimb(shoulderRight, Offset(w * 0.74f, h * 0.35f), w * 0.055f, isHighlightedMuscle = false)

    // Grande Dorsal em V esculpido em Laranja/Cobre
    drawClayLimb(shoulderLeft, hipPos, w * 0.095f, isHighlightedMuscle = true)
    drawClayLimb(shoulderRight, hipPos, w * 0.095f, isHighlightedMuscle = true)
    drawClayLimb(shoulderCenter, hipPos, w * 0.12f, isHighlightedMuscle = true)
    drawClayHead(headPos, w * 0.065f)
}

/**
 * 3. Remada Curvada com Barra (Remada Curvada / Bent Over Row):
 * Tronco inclinado tracionando a barra livre em direção ao abdômen.
 */
private fun DrawScope.drawBentOverRowIllustration(w: Float, h: Float) {
    val headPos = Offset(w * 0.72f, h * 0.26f)
    val shoulderPos = Offset(w * 0.60f, h * 0.35f)
    val hipPos = Offset(w * 0.28f, h * 0.48f)
    val kneePos = Offset(w * 0.36f, h * 0.66f)
    val anklePos = Offset(w * 0.32f, h * 0.83f)
    val footPos = Offset(w * 0.44f, h * 0.84f)

    // Base sólida de pernas semiflexionadas
    drawClayLimb(hipPos, kneePos, w * 0.092f, isHighlightedMuscle = false)
    drawClayLimb(kneePos, anklePos, w * 0.075f, isHighlightedMuscle = false)
    drawClayLimb(anklePos, footPos, w * 0.055f, isHighlightedMuscle = false)

    // Tronco inclinado (~45°) com Dorsais e Romboides em DESTAQUE COBRE/LARANJA
    drawClayLimb(hipPos, shoulderPos, w * 0.118f, isHighlightedMuscle = true)
    drawClayHead(headPos, w * 0.07f)

    // Cotovelo puxado alto para trás + antebraço segurando a barra próxima ao abdômen
    val elbowPos = Offset(w * 0.44f, h * 0.28f)
    val handBarPos = Offset(w * 0.52f, h * 0.52f)
    drawClayLimb(shoulderPos, elbowPos, w * 0.068f, isHighlightedMuscle = true)
    drawClayLimb(elbowPos, handBarPos, w * 0.058f, isHighlightedMuscle = false)

    // Barra Olímpica com Anilhas Laranjas na linha do umbigo/quadril
    drawGlowingPlate(handBarPos, w * 0.16f, h * 0.16f)
    drawCircle(color = ClayBodyLight, radius = w * 0.032f, center = handBarPos)
}

/**
 * 4. Remada Unilateral / Serrote (Serrote / Unilateral / Dumbbell Row):
 * Apoio no banco horizontal puxando halter unilateralmente com destaque na dorsal.
 */
private fun DrawScope.drawOneArmDumbbellRowIllustration(w: Float, h: Float) {
    // Banco horizontal de apoio
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.24f, h * 0.62f),
        end = Offset(w * 0.78f, h * 0.62f),
        strokeWidth = w * 0.055f,
        cap = StrokeCap.Round
    )
    drawLine(color = EquipmentSteel, start = Offset(w * 0.32f, h * 0.62f), end = Offset(w * 0.32f, h * 0.85f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
    drawLine(color = EquipmentSteel, start = Offset(w * 0.70f, h * 0.62f), end = Offset(w * 0.70f, h * 0.85f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)

    val headPos = Offset(w * 0.74f, h * 0.34f)
    val shoulderPos = Offset(w * 0.62f, h * 0.38f)
    val hipPos = Offset(w * 0.30f, h * 0.42f)

    // Joelho de apoio sobre o banco + perna de base no chão
    val benchKnee = Offset(w * 0.42f, h * 0.58f)
    val floorFoot = Offset(w * 0.20f, h * 0.84f)
    drawClayLimb(hipPos, benchKnee, w * 0.075f, isHighlightedMuscle = false)
    drawClayLimb(hipPos, floorFoot, w * 0.078f, isHighlightedMuscle = false)

    // Braço de apoio estendido na ponta do banco
    drawClayLimb(shoulderPos, Offset(w * 0.68f, h * 0.59f), w * 0.052f, isHighlightedMuscle = false)

    // Tronco paralelo ao banco com Grande Dorsal iluminada em Laranja
    drawClayLimb(hipPos, shoulderPos, w * 0.115f, isHighlightedMuscle = true)
    drawClayHead(headPos, w * 0.068f)

    // Braço de trabalho puxando o halter até a linha do quadril (Serrote)
    val pullingElbow = Offset(w * 0.45f, h * 0.26f)
    val dumbbellHand = Offset(w * 0.48f, h * 0.48f)
    drawClayLimb(shoulderPos, pullingElbow, w * 0.068f, isHighlightedMuscle = true)
    drawClayLimb(pullingElbow, dumbbellHand, w * 0.055f, isHighlightedMuscle = false)

    // Halter 3D Laranja na mão
    drawGlowingPlate(Offset(w * 0.42f, h * 0.48f), w * 0.048f, h * 0.12f)
    drawGlowingPlate(Offset(w * 0.54f, h * 0.48f), w * 0.048f, h * 0.12f)
    drawLine(color = ClayBodyLight, start = Offset(w * 0.42f, h * 0.48f), end = Offset(w * 0.54f, h * 0.48f), strokeWidth = w * 0.025f)
}

/**
 * 5. Barra Fixa / Graviton (Barra Fixa / Pull-up / Chin-up):
 * Suspensão na barra fixa elevando o tronco com destaque nas dorsais e bíceps.
 */
private fun DrawScope.drawPullUpChinUpIllustration(w: Float, h: Float) {
    // Estrutura da Barra Fixa superior
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.18f, h * 0.16f),
        end = Offset(w * 0.18f, h * 0.85f),
        strokeWidth = w * 0.038f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.82f, h * 0.16f),
        end = Offset(w * 0.82f, h * 0.85f),
        strokeWidth = w * 0.038f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = ClayBodyLight,
        start = Offset(w * 0.16f, h * 0.20f),
        end = Offset(w * 0.84f, h * 0.20f),
        strokeWidth = w * 0.036f,
        cap = StrokeCap.Round
    )

    val headPos = Offset(w * 0.50f, h * 0.24f)
    val shoulderLeft = Offset(w * 0.39f, h * 0.35f)
    val shoulderRight = Offset(w * 0.61f, h * 0.35f)
    val hipPos = Offset(w * 0.50f, h * 0.60f)
    val kneePos = Offset(w * 0.50f, h * 0.76f)
    val footPos = Offset(w * 0.44f, h * 0.83f)

    // Pernas suspensas levemente cruzadas atrás
    drawClayLimb(hipPos, kneePos, w * 0.088f, isHighlightedMuscle = false)
    drawClayLimb(kneePos, footPos, w * 0.068f, isHighlightedMuscle = false)

    // Braços flexionados puxando na barra (Bíceps + Antebraços ativos)
    val gripLeft = Offset(w * 0.28f, h * 0.20f)
    val gripRight = Offset(w * 0.72f, h * 0.20f)
    val elbowLeft = Offset(w * 0.28f, h * 0.36f)
    val elbowRight = Offset(w * 0.72f, h * 0.36f)

    drawClayLimb(gripLeft, elbowLeft, w * 0.052f, isHighlightedMuscle = false)
    drawClayLimb(gripRight, elbowRight, w * 0.052f, isHighlightedMuscle = false)
    drawClayLimb(elbowLeft, shoulderLeft, w * 0.068f, isHighlightedMuscle = true)
    drawClayLimb(elbowRight, shoulderRight, w * 0.068f, isHighlightedMuscle = true)

    // Grande Dorsal em V iluminada em Laranja/Cobre
    drawClayLimb(shoulderLeft, hipPos, w * 0.098f, isHighlightedMuscle = true)
    drawClayLimb(shoulderRight, hipPos, w * 0.098f, isHighlightedMuscle = true)
    drawClayLimb(Offset(w * 0.50f, h * 0.35f), hipPos, w * 0.115f, isHighlightedMuscle = true)
    drawClayHead(headPos, w * 0.066f)
}

/**
 * 6. Rosca Direta com Barra / Barra W / Cabo (Barbell / EZ-Bar Curl):
 * Boneco em pé flexionando os cotovelos com barra e destaque intenso no bíceps.
 */
private fun DrawScope.drawBarbellCurlIllustration(w: Float, h: Float) {
    val headPos = Offset(w * 0.44f, h * 0.20f)
    val shoulderPos = Offset(w * 0.42f, h * 0.33f)
    val hipPos = Offset(w * 0.40f, h * 0.60f)
    val anklePos = Offset(w * 0.42f, h * 0.83f)
    val elbowPos = Offset(w * 0.44f, h * 0.52f)
    val handBarPos = Offset(w * 0.64f, h * 0.39f)

    // Tronco e pernas firmes em pé
    drawClayLimb(hipPos, anklePos, w * 0.088f, isHighlightedMuscle = false)
    drawClayLimb(shoulderPos, hipPos, w * 0.108f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.072f)

    // Posição estendida inicial em transparência (mostrando o arco da rosca)
    drawLine(
        color = ClayBodyMid.copy(alpha = 0.24f),
        start = elbowPos,
        end = Offset(w * 0.54f, h * 0.68f),
        strokeWidth = w * 0.055f,
        cap = StrokeCap.Round
    )

    // Antebraço subindo com a Barra W
    drawClayLimb(elbowPos, handBarPos, w * 0.065f, isHighlightedMuscle = false)

    // Bíceps Braquial contraído em DESTAQUE LARANJA/COBRE
    drawClayLimb(shoulderPos, elbowPos, w * 0.098f, isHighlightedMuscle = true)
    drawCircle(color = MaxOrangeBright, radius = w * 0.058f, center = Offset(w * 0.48f, h * 0.43f))

    // Barra W / Olímpica com anilhas laranjas nas mãos
    drawGlowingPlate(handBarPos, w * 0.14f, h * 0.14f)
    drawCircle(color = ClayBodyLight, radius = w * 0.03f, center = handBarPos)
}

/**
 * 7. Rosca Alternada / Martelo com Halteres (Alternating / Hammer Dumbbell Curl):
 * Boneco executando flexão alternada/martelo com par de halteres e destaque em bíceps e antebraço.
 */
private fun DrawScope.drawDumbbellHammerCurlIllustration(w: Float, h: Float) {
    val headPos = Offset(w * 0.50f, h * 0.20f)
    val shoulderLeft = Offset(w * 0.36f, h * 0.34f)
    val shoulderRight = Offset(w * 0.62f, h * 0.34f)
    val hipPos = Offset(w * 0.49f, h * 0.62f)
    val legLeft = Offset(w * 0.42f, h * 0.83f)
    val legRight = Offset(w * 0.56f, h * 0.83f)

    // Pernas e tronco
    drawClayLimb(hipPos, legLeft, w * 0.078f, isHighlightedMuscle = false)
    drawClayLimb(hipPos, legRight, w * 0.078f, isHighlightedMuscle = false)
    drawClayLimb(Offset(w * 0.49f, h * 0.34f), hipPos, w * 0.115f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.07f)

    // Braço esquerdo descido segurando halter (fase alternada inicial)
    val leftElbow = Offset(w * 0.32f, h * 0.50f)
    val leftHand = Offset(w * 0.28f, h * 0.64f)
    drawClayLimb(shoulderLeft, leftElbow, w * 0.075f, isHighlightedMuscle = false)
    drawClayLimb(leftElbow, leftHand, w * 0.062f, isHighlightedMuscle = false)
    drawGlowingPlate(leftHand, w * 0.10f, h * 0.055f)

    // Braço direito contraído no topo (Bíceps + Braquiorradial em DESTAQUE LARANJA)
    val rightElbow = Offset(w * 0.66f, h * 0.51f)
    val rightHand = Offset(w * 0.76f, h * 0.34f)
    drawClayLimb(shoulderRight, rightElbow, w * 0.092f, isHighlightedMuscle = true)
    drawClayLimb(rightElbow, rightHand, w * 0.072f, isHighlightedMuscle = true)
    drawCircle(color = MaxOrangeBright, radius = w * 0.052f, center = Offset(w * 0.65f, h * 0.42f))

    // Halter vertical (estilo Martelo / Alternada) no topo
    drawGlowingPlate(Offset(rightHand.x, rightHand.y - h * 0.055f), w * 0.095f, h * 0.048f)
    drawGlowingPlate(Offset(rightHand.x, rightHand.y + h * 0.055f), w * 0.095f, h * 0.048f)
    drawLine(
        color = ClayBodyLight,
        start = Offset(rightHand.x, rightHand.y - h * 0.055f),
        end = Offset(rightHand.x, rightHand.y + h * 0.055f),
        strokeWidth = w * 0.026f
    )
}

/**
 * 8. Rosca Scott / Concentrada (Preacher / Concentration Curl):
 * Braços apoiados no pupitre inclinado do Banco Scott isolando o pico do bíceps.
 */
private fun DrawScope.drawPreacherCurlIllustration(w: Float, h: Float) {
    // Estrutura do Banco Scott (Pupitre inclinado + base)
    val padTop = Offset(w * 0.48f, h * 0.38f)
    val padBottom = Offset(w * 0.66f, h * 0.62f)
    drawLine(
        color = EquipmentHighlight,
        start = padTop,
        end = padBottom,
        strokeWidth = w * 0.075f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.56f, h * 0.52f),
        end = Offset(w * 0.56f, h * 0.85f),
        strokeWidth = w * 0.045f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.24f, h * 0.66f),
        end = Offset(w * 0.56f, h * 0.66f),
        strokeWidth = w * 0.048f,
        cap = StrokeCap.Round
    )

    // Boneco sentado atrás do pupitre Scott
    val headPos = Offset(w * 0.32f, h * 0.22f)
    val shoulderPos = Offset(w * 0.34f, h * 0.36f)
    val hipPos = Offset(w * 0.30f, h * 0.62f)
    val elbowPos = Offset(w * 0.56f, h * 0.50f)
    val handBarPos = Offset(w * 0.74f, h * 0.30f)

    drawClayLimb(shoulderPos, hipPos, w * 0.105f, isHighlightedMuscle = false)
    drawClayLimb(hipPos, Offset(w * 0.34f, h * 0.83f), w * 0.082f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.07f)

    // Antebraço subindo a partir do apoio Scott
    drawClayLimb(elbowPos, handBarPos, w * 0.065f, isHighlightedMuscle = false)

    // Braço apoiado no pupitre com Pico do Bíceps em DESTAQUE LARANJA/COBRE
    drawClayLimb(shoulderPos, elbowPos, w * 0.096f, isHighlightedMuscle = true)
    drawCircle(color = MaxOrangeBright, radius = w * 0.056f, center = Offset(w * 0.47f, h * 0.41f))

    // Barra W / Halter na mão
    drawGlowingPlate(handBarPos, w * 0.14f, h * 0.14f)
    drawCircle(color = ClayBodyLight, radius = w * 0.03f, center = handBarPos)
}

// =========================================================================
// ILUSTRAÇÕES 3D PARA OS DEMAIS GRUPOS MUSCULARES
// =========================================================================

private fun DrawScope.drawBenchPressIllustration(w: Float, h: Float) {
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.16f, h * 0.64f),
        end = Offset(w * 0.76f, h * 0.64f),
        strokeWidth = w * 0.055f,
        cap = StrokeCap.Round
    )
    drawLine(color = EquipmentSteel, start = Offset(w * 0.24f, h * 0.64f), end = Offset(w * 0.24f, h * 0.85f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
    drawLine(color = EquipmentSteel, start = Offset(w * 0.66f, h * 0.64f), end = Offset(w * 0.66f, h * 0.85f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)

    val headPos = Offset(w * 0.22f, h * 0.53f)
    val shoulderPos = Offset(w * 0.35f, h * 0.54f)
    val hipPos = Offset(w * 0.64f, h * 0.55f)
    val kneePos = Offset(w * 0.78f, h * 0.56f)
    val footPos = Offset(w * 0.80f, h * 0.83f)

    drawClayLimb(hipPos, kneePos, w * 0.085f, isHighlightedMuscle = false)
    drawClayLimb(kneePos, footPos, w * 0.07f, isHighlightedMuscle = false)
    drawClayLimb(shoulderPos, hipPos, w * 0.105f, isHighlightedMuscle = true)
    drawClayHead(headPos, w * 0.065f)

    val handPos = Offset(w * 0.36f, h * 0.26f)
    drawClayLimb(shoulderPos, handPos, w * 0.065f, isHighlightedMuscle = true)

    drawGlowingPlate(Offset(w * 0.36f, h * 0.24f), w * 0.16f, h * 0.16f)
    drawCircle(color = ClayBodyLight, radius = w * 0.03f, center = Offset(w * 0.36f, h * 0.24f))
}

private fun DrawScope.drawShoulderPressIllustration(w: Float, h: Float) {
    val headPos = Offset(w * 0.50f, h * 0.38f)
    val shoulderLeft = Offset(w * 0.36f, h * 0.50f)
    val shoulderRight = Offset(w * 0.64f, h * 0.50f)
    val hipPos = Offset(w * 0.50f, h * 0.78f)

    drawClayLimb(Offset(w * 0.50f, h * 0.48f), hipPos, w * 0.12f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.075f)

    val handLeft = Offset(w * 0.26f, h * 0.24f)
    val handRight = Offset(w * 0.74f, h * 0.24f)
    drawClayLimb(shoulderLeft, handLeft, w * 0.06f, isHighlightedMuscle = false)
    drawClayLimb(shoulderRight, handRight, w * 0.06f, isHighlightedMuscle = false)

    drawCircle(color = MaxOrange, radius = w * 0.075f, center = shoulderLeft)
    drawCircle(color = MaxOrange, radius = w * 0.075f, center = shoulderRight)

    drawGlowingPlate(handLeft, w * 0.12f, h * 0.06f)
    drawGlowingPlate(handRight, w * 0.12f, h * 0.06f)
}

private fun DrawScope.drawTricepsIllustration(w: Float, h: Float) {
    drawLine(
        color = EquipmentSteel,
        start = Offset(w * 0.76f, h * 0.14f),
        end = Offset(w * 0.76f, h * 0.85f),
        strokeWidth = w * 0.04f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = EquipmentHighlight,
        start = Offset(w * 0.72f, h * 0.16f),
        end = Offset(w * 0.58f, h * 0.62f),
        strokeWidth = w * 0.02f
    )

    val headPos = Offset(w * 0.42f, h * 0.22f)
    val shoulderPos = Offset(w * 0.38f, h * 0.36f)
    val hipPos = Offset(w * 0.34f, h * 0.64f)
    val elbowPos = Offset(w * 0.42f, h * 0.50f)
    val handPos = Offset(w * 0.56f, h * 0.62f)

    drawClayLimb(shoulderPos, hipPos, w * 0.105f, isHighlightedMuscle = false)
    drawClayLimb(hipPos, Offset(w * 0.36f, h * 0.84f), w * 0.085f, isHighlightedMuscle = false)
    drawClayHead(headPos, w * 0.07f)

    drawClayLimb(shoulderPos, elbowPos, w * 0.09f, isHighlightedMuscle = true)
    drawClayLimb(elbowPos, handPos, w * 0.065f, isHighlightedMuscle = true)
    drawCircle(color = MaxOrangeBright, radius = w * 0.038f, center = handPos)
}

private fun DrawScope.drawCoreAbsIllustration(w: Float, h: Float) {
    val headPos = Offset(w * 0.22f, h * 0.48f)
    val shoulderPos = Offset(w * 0.34f, h * 0.54f)
    val hipPos = Offset(w * 0.56f, h * 0.72f)
    val kneePos = Offset(w * 0.72f, h * 0.52f)
    val footPos = Offset(w * 0.84f, h * 0.76f)

    drawClayLimb(hipPos, kneePos, w * 0.085f, isHighlightedMuscle = false)
    drawClayLimb(kneePos, footPos, w * 0.07f, isHighlightedMuscle = false)
    drawClayLimb(shoulderPos, hipPos, w * 0.11f, isHighlightedMuscle = true)
    drawClayHead(headPos, w * 0.068f)
}

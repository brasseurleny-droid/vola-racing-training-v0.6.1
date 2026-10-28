package fr.volaracing.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.volaracing.training.core.TrackPt
import fr.volaracing.training.data.Act
import fr.volaracing.training.data.Profile
import java.util.Locale

// ---------------------------------------------------------------- Accueil

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val sessions by vm.sessions.collectAsState()
    val all by vm.allPassages.collectAsState()
    val courses by vm.courses.collectAsState()

    val last = sessions.firstOrNull()
    val lastRuns = if (last == null) emptyList() else all.filter { it.sessionId == last.id }
    val bestRun = lastRuns.filter { it.kind == 0 }.minByOrNull { it.timeNs } ?: lastRuns.minByOrNull { it.timeNs }
    val courseName = { id: Long -> courses.firstOrNull { it.id == id }?.name ?: "ce parcours" }

    val phrase = bestRun?.let { b ->
        if (b.kind == 0 && b.courseId >= 0) {
            val prev = all.filter { it.courseId == b.courseId && it.sessionId != b.sessionId && it.kind == 0 }
                .minOfOrNull { it.timeNs }
            if (prev != null) {
                String.format(
                    Locale.FRANCE, "Meilleur run : %s, %+.2f s par rapport à votre record précédent sur %s.",
                    fmtT(b.timeNs), (b.timeNs - prev) / 1e9, courseName(b.courseId),
                )
            } else "Premier chrono sur ${courseName(b.courseId)} : ${fmtT(b.timeNs)}."
        } else "Meilleur run de la session : ${fmtT(b.timeNs)} (sans portes)."
    } ?: "Aucun run détecté dans cette session."

    // Progression par parcours : meilleur temps de chaque session (jamais tous parcours confondus).
    val progress = courses.mapNotNull { c ->
        val runs = all.filter { it.courseId == c.id && it.kind == 0 }
        if (runs.isEmpty()) null
        else {
            val perSession = runs.groupBy { it.sessionId }.toSortedMap().map { e -> e.value.minOf { it.timeNs } / 1e9 }
            Triple(c, perSession, runs.maxOf { it.id })
        }
    }.sortedByDescending { it.third }.take(3)

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeroBanner("Vola Racing Training", "Enregistrez. Analysez. Progressez.") }
            if (last != null) {
                item { SectionTitle("DERNIÈRE SESSION") }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = VSurface), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (bestRun != null) RunThumb(vm, bestRun, 72)
                                Column(Modifier.weight(1f)) {
                                    Text(sessionTitle(last), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                                    Text(
                                        "${lastRuns.size} run${if (lastRuns.size > 1) "s" else ""} - ${Act.label(last.activity)}",
                                        color = VGray,
                                    )
                                }
                                if (bestRun != null) TimeChip(fmtT(bestRun.timeNs))
                            }
                            Text(phrase, style = MaterialTheme.typography.bodyLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BigButton("Analyser", Modifier.weight(1f)) {
                                    if (bestRun != null) nav.navigate("lab/${bestRun.id}") else nav.navigate("session/${last.id}")
                                }
                                BigButton("Reprendre", Modifier.weight(1f), filled = false) {
                                    nav.navigate("run/${last.courseId}/${last.activity}")
                                }
                            }
                        }
                    }
                }
            }

            item { SectionTitle("QUE VOULEZ-VOUS FAIRE ?") }
            item {
                ActionCard("Enregistrer une session", "Avec un parcours (chrono automatique aux portes) ou en session libre.", "Enregistrer") {
                    nav.navigate("record")
                }
            }
            item {
                ActionCard("Analyser une session", "Courbes, curseur, comparaison de runs : comprenez où le temps se gagne ou se perd.", "Analyser") {
                    nav.navigate("analyze")
                }
            }
            item {
                ActionCard("Entre amis", "Partagez un parcours, échangez vos sessions et comparez vos runs avec ceux de votre groupe.", "Groupes") {
                    nav.navigate("groups")
                }
            }

            if (progress.isNotEmpty()) {
                item { SectionTitle("VOTRE PROGRESSION") }
                items(progress, key = { it.first.id }) { (c, values, _) ->
                    Card(colors = CardDefaults.cardColors(containerColor = VSurface), modifier = Modifier.fillMaxWidth().clickable { nav.navigate("course/${c.id}") }) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                                TimeChip(fmtT((values.min() * 1e9).toLong()))
                            }
                            Sparkline(values, color = VAccent)
                            val trend = if (values.size >= 2) {
                                String.format(Locale.FRANCE, "%+.2f s depuis la première session (%d sessions)", values.last() - values.first(), values.size)
                            } else "1 session pour l'instant"
                            Text(trend, color = if (values.size >= 2 && values.last() < values.first()) VGreen else VGray)
                        }
                    }
                }
                // "A battre" : le record du parcours le plus recemment roule, et l'ecart du dernier run.
                val (rc, rv, _) = progress.first()
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("À BATTRE - ${rc.name.uppercase()}", fmtT((rv.min() * 1e9).toLong()), VOr, Modifier.weight(1f))
                        Tile("DERNIER VS RECORD", String.format(Locale.FRANCE, "%+.2f s", rv.last() - rv.min()), VViolet, Modifier.weight(1f))
                    }
                }
            }

            if (sessions.isEmpty()) item { HowTo() }
        }
    }
}

@Composable
private fun ActionCard(title: String, text: String, button: String, enabled: Boolean = true, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = VSurface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, color = VGray, style = MaterialTheme.typography.bodyMedium)
            }
            BigButton(button, Modifier.size(width = 112.dp, height = 56.dp), enabled = enabled, onClick = onClick)
        }
    }
}

@Composable
private fun HowTo() {
    Card(colors = CardDefaults.cardColors(containerColor = VSurface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pour commencer", style = MaterialTheme.typography.titleLarge)
            Step(1, "Enregistrez une session", "Avec un parcours, ou librement : tout est enregistré.")
            Step(2, "Analysez vos runs", "Courbes, curseur, comparaison de deux tracés.")
            Step(3, "Progressez", "Suivez votre meilleur temps parcours par parcours.")
        }
    }
}

@Composable
private fun Step(n: Int, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Bib(n)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, color = VGray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Miniature d'un run, chargee a la demande. */
@Composable
fun RunThumb(vm: AppViewModel, p: fr.volaracing.training.data.Passage, sizeDp: Int = 64) {
    var pts by remember(p.id) { mutableStateOf<List<TrackPt>?>(null) }
    LaunchedEffect(p.id) { pts = vm.thumb(p) }
    Thumb(pts, Modifier.size(sizeDp.dp))
}

// ---------------------------------------------------------------- Enregistrer

@Composable
fun RecordScreen(vm: AppViewModel, nav: NavController) {
    val courses by vm.courses.collectAsState()
    var sel by remember { mutableStateOf(-1L) } // -1 = session libre
    var act by remember { mutableStateOf(vm.activity) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Enregistrer une session")
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle("ACTIVITÉ") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Act.all.forEach { a ->
                        BigButton(Act.label(a), Modifier.weight(1f), filled = act == a) { act = a; vm.selectActivity(a) }
                    }
                }
            }
            item { SectionTitle("PARCOURS") }
            item {
                Text(
                    "Avec un parcours, le chrono part et s'arrête tout seul aux portes. Sans parcours, la session " +
                        "est enregistrée en entier ; vous découpez et analysez vos runs ensuite.",
                    color = VGray,
                )
            }
            item { SelectRow("Session libre (sans parcours)", "Enregistrement complet, analyse après coup", sel == -1L) { sel = -1L } }
            items(courses, key = { it.id }) { c ->
                SelectRow(
                    c.name,
                    if (c.aLat == null || c.bLat == null) "Parcours incomplet : placez départ et arrivée" else Act.label(c.activity),
                    sel == c.id,
                ) { sel = c.id }
            }
            if (courses.isEmpty()) item { Text("Aucun parcours : créez-en un dans l'onglet Parcours.", color = VGray) }
        }
        Box(Modifier.fillMaxWidth().background(VBg).padding(16.dp)) {
            BigButton("Préparer l'enregistrement", Modifier.fillMaxWidth(), height = 64.dp) { nav.navigate("run/$sel/$act") }
        }
    }
}

@Composable
fun SelectRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().background(VSurface, shape)
            .then(if (selected) Modifier.border(3.dp, VAccent, shape) else Modifier)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(sub, color = VGray, fontSize = 13.sp)
        }
        if (selected) Box(Modifier.size(14.dp).background(VAccent, CircleShape))
    }
}

// ---------------------------------------------------------------- Premier lancement

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        Pair("Enregistrez vos sessions", "Posez un parcours avec départ et arrivée, ou roulez librement. GPS et capteurs sont enregistrés, écran verrouillé."),
        Pair("Analysez chaque run", "Superposez vos runs, choisissez vos courbes (vitesse, accélération, pente), placez un curseur et mesurez."),
        Pair("Progressez", "Comparez à votre meilleur run, voyez où le temps se gagne ou se perd, suivez votre progression parcours par parcours."),
    )
    var i by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { VolaLogo() }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${i + 1} / ${pages.size}", color = VGray)
            Text(pages[i].first, style = MaterialTheme.typography.displaySmall)
            Text(pages[i].second, style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Passer", Modifier.weight(1f), filled = false) { onDone() }
            BigButton(if (i == pages.lastIndex) "Commencer" else "Suivant", Modifier.weight(1f)) {
                if (i == pages.lastIndex) onDone() else i++
            }
        }
    }
}

// ---------------------------------------------------------------- Reglages

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    var p by remember { mutableStateOf(vm.prefs.profile()) }
    fun upd(n: Profile) { p = n; vm.prefs.saveProfile(n) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Réglages de détection", onBack = { nav.popBackStack() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle("PRÉRÉGLAGES (POINT DE DÉPART)")
            Text("Un préréglage remplit les valeurs ci-dessous, que vous pouvez ensuite modifier. Ce sont des hypothèses à valider sur le terrain.", color = VGray)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Act.all.forEach { a -> BigButton(Act.label(a), Modifier.weight(1f), filled = false) { upd(vm.prefs.preset(a)) } }
            }
            SectionTitle("RÉGLAGES")
            Stepper("Largeur de porte (nouveaux parcours)", p.gateWidth, "m", 5.0, 5.0, 100.0) { upd(p.copy(gateWidth = it)) }
            Stepper("Vitesse minimale de détection", p.minSpeedKmh, "km/h", 1.0, 0.0, 60.0) { upd(p.copy(minSpeedKmh = it)) }
            Stepper("Fenêtre de filtrage de la vitesse", p.windowS, "s", 0.5, 0.5, 5.0) { upd(p.copy(windowS = it)) }
            Stepper("Seuil de précision GPS (qualité faible au-delà)", p.accThreshold, "m", 5.0, 5.0, 100.0) { upd(p.copy(accThreshold = it)) }
            Stepper("Seuil de freinage (décélération)", p.brakeThreshold, "m/s²", 0.5, 0.5, 10.0) { upd(p.copy(brakeThreshold = it)) }
            BigButton("Télécharger une zone hors ligne (bientôt)", Modifier.fillMaxWidth(), filled = false, enabled = false) {}
            Text("Vola Racing Training 0.6.0 - mode solo. Toutes les données restent sur ce téléphone.", color = VGray)
            Spacer(Modifier.height(8.dp))
        }
    }
}

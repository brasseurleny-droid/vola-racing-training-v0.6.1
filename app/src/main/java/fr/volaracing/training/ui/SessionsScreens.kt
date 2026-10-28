package fr.volaracing.training.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.volaracing.training.data.Act
import fr.volaracing.training.data.Passage
import fr.volaracing.training.data.Session
import fr.volaracing.training.data.Waypoints
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun runTag(p: Passage): String? = listOfNotNull(
    if (p.kind == 1) "RUN LIBRE (SANS PORTES)" else null,
    if (p.kind == 2) "RUN AUTO (DESCENTE)" else null,
    if (p.lowQuality) "QUALITÉ FAIBLE" else null,
).joinToString(" - ").ifEmpty { null }

// ---------------------------------------------------------------- Analyser : liste des sessions

@Composable
fun AnalyzeScreen(vm: AppViewModel, nav: NavController) {
    val sessions by vm.sessions.collectAsState()
    val all by vm.allPassages.collectAsState()
    val courses by vm.courses.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") } // "" = toutes les activites

    val cname = { id: Long -> courses.firstOrNull { it.id == id }?.name ?: "" }
    val shown = sessions.filter { s ->
        (filter.isEmpty() || s.activity == filter) &&
            (query.isBlank() || sessionTitle(s).contains(query, true) || s.place.contains(query, true) ||
                cname(s.courseId).contains(query, true))
    }
    val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    val grouped = shown.groupBy { dayFmt.format(Date(it.startUtcMs)) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Analyser")
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Rechercher (nom, lieu, parcours)") }, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BigButton("Tous", Modifier.weight(1f), filled = filter.isEmpty(), height = 48.dp) { filter = "" }
                    Act.all.forEach { a ->
                        BigButton(Act.label(a), Modifier.weight(1f), filled = filter == a, height = 48.dp) { filter = a }
                    }
                }
            }
            if (sessions.isEmpty()) item {
                Text("Aucune session enregistrée. Lancez votre première session depuis l'onglet Enregistrer.", color = VGray)
            } else if (shown.isEmpty()) item { Text("Aucune session ne correspond.", color = VGray) }

            for ((_, list) in grouped) {
                item(key = "day-${list.first().startUtcMs / 86_400_000L}-${list.first().id}") { SectionTitle(dayLabel(list.first().startUtcMs)) }
                items(list, key = { it.id }) { s ->
                    val runs = all.filter { it.sessionId == s.id }
                    val best = runs.minByOrNull { it.timeNs }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VSurface),
                        modifier = Modifier.fillMaxWidth().clickable { nav.navigate("session/${s.id}") },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (best != null) RunThumb(vm, best) else Box(Modifier.padding(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sessionTitle(s), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                val course = cname(s.courseId)
                                Text(
                                    "${runs.size} run${if (runs.size > 1) "s" else ""} - ${Act.label(s.activity)}" +
                                        (if (course.isNotEmpty()) " - $course" else if (s.courseId < 0) " - libre" else ""),
                                    color = VGray, style = MaterialTheme.typography.bodyMedium,
                                )
                                if (s.place.isNotBlank()) Text(s.place, color = VGray, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (best != null) TimeChip(fmtT(best.timeNs))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Detail d'une session

@Composable
fun SessionDetailScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val runs by vm.passagesOfSession(id).collectAsState(emptyList())
    val courses by vm.courses.collectAsState()
    var session by remember { mutableStateOf<Session?>(null) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { session = vm.session(id) }

    val s = session
    val ranked = runs.sortedBy { it.timeNs }
    val number = runs.sortedBy { it.id }.mapIndexed { i, p -> p.id to i + 1 }.toMap()
    val courseName = s?.let { x -> courses.firstOrNull { it.id == x.courseId }?.name }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (s != null) sessionTitle(s) else "Session", onBack = { nav.popBackStack() })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (s != null) item {
                Text(
                    "${Act.label(s.activity)} - ${courseName ?: "session libre"}" +
                        (if (s.origin == 1) " - session importée de ${s.ownerName}" else "") +
                        (if (s.place.isNotBlank()) " - ${s.place}" else ""),
                    color = VGray,
                )
                if (s.notes.isNotBlank()) Text(s.notes)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("Analyser tout", Modifier.weight(1f), enabled = runs.isNotEmpty()) {
                        nav.navigate("lab/" + runs.sortedBy { it.id }.take(6).joinToString(",") { it.id.toString() })
                    }
                    BigButton("Renommer", Modifier.weight(1f), filled = false) { rename = true }
                }
            }
            if (s == null || s.origin == 0) item {
                BigButton("Découper en descentes", Modifier.fillMaxWidth(), filled = false) { nav.navigate("split/$id") }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("Exporter", Modifier.weight(1f), filled = false) {
                        scope.launch {
                            val msg = try { "${vm.exportSession(ctx, id)} fichiers dans Téléchargements/VolaRacing" }
                            catch (e: Exception) { "Export impossible : ${e.message}" }
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                    BigButton("Partager", Modifier.weight(1f), filled = false) {
                        scope.launch {
                            try {
                                val i = vm.shareIntent(ctx, id)
                                ctx.startActivity(Intent.createChooser(i, "Partager la session").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Partage impossible : ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    BigButton("Supprimer", Modifier.weight(1f), filled = false) { confirmDelete = true }
                }
            }
            item { SectionTitle("RUNS - TOUCHEZ UN RUN POUR L'ANALYSER") }
            if (runs.isEmpty()) item {
                Text("Aucun run détecté. Les fichiers GPS et capteurs restent exportables.", color = VGray)
            }
            items(ranked, key = { it.id }) { p ->
                RankRow(
                    rank = ranked.indexOf(p) + 1, bib = number[p.id] ?: 0,
                    title = "Run ${number[p.id] ?: ""}",
                    sub = String.format(Locale.US, "Vmax %.1f km/h  -  G max %.2f", p.vMax * 3.6, p.gMax),
                    timeNs = p.timeNs, gapNs = p.timeNs - ranked.first().timeNs,
                    tag = runTag(p),
                    thumb = { RunThumb(vm, p) },
                ) { nav.navigate("lab/${p.id}") }
            }
        }
    }

    if (rename && s != null) {
        var name by remember { mutableStateOf(s.name) }
        var place by remember { mutableStateOf(s.place) }
        var notes by remember { mutableStateOf(s.notes) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Renommer la session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nom") })
                    OutlinedTextField(place, { place = it }, singleLine = true, label = { Text("Lieu") })
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
                }
            },
            confirmButton = {
                BigButton("Enregistrer") {
                    val n = s.copy(name = name.trim(), place = place.trim(), notes = notes.trim())
                    scope.launch { vm.saveSession(n); session = n; rename = false }
                }
            },
            dismissButton = { BigButton("Annuler", filled = false) { rename = false } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette session ?") },
            text = { Text("Les runs et les fichiers GPS/capteurs de la session seront supprimés définitivement. Pensez à l'exporter avant.") },
            confirmButton = {
                BigButton("Supprimer") { scope.launch { vm.deleteSession(id); confirmDelete = false; nav.popBackStack() } }
            },
            dismissButton = { BigButton("Annuler", filled = false) { confirmDelete = false } },
        )
    }
}

// ---------------------------------------------------------------- Parcours

@Composable
fun CoursesScreen(vm: AppViewModel, nav: NavController) {
    val courses by vm.courses.collectAsState()
    val all by vm.allPassages.collectAsState()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Parcours")
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Un parcours est une piste avec départ, arrivée et étapes optionnelles. Il sert à chronométrer automatiquement.",
                    color = VGray,
                )
            }
            if (courses.isEmpty()) item { Text("Aucun parcours pour l'instant.", color = VGray) }
            items(courses, key = { it.id }) { c ->
                val runs = all.filter { it.courseId == c.id }
                val best = runs.filter { it.kind == 0 }.minByOrNull { it.timeNs }
                Card(
                    colors = CardDefaults.cardColors(containerColor = VSurface),
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("course/${c.id}") },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LatLonThumb(courseLine(c))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            Text("${Act.label(c.activity)} - ${runs.size} passage${if (runs.size > 1) "s" else ""}", color = VGray)
                            if (c.aLat == null || c.bLat == null) Text("Incomplet : placez départ et arrivée", color = VRed)
                        }
                        if (best != null) TimeChip(fmtT(best.timeNs))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(VBg).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Nouveau parcours", Modifier.weight(1f), height = 64.dp) { nav.navigate("edit/0") }
                BigButton("Réglages", Modifier.weight(1f), filled = false, height = 64.dp) { nav.navigate("settings") }
            }
        }
    }
}

private fun courseLine(c: fr.volaracing.training.data.Course): List<Pair<Double, Double>> {
    val out = ArrayList<Pair<Double, Double>>()
    if (c.aLat != null && c.aLon != null) out.add(Pair(c.aLat, c.aLon))
    out.addAll(Waypoints.decode(c.waypoints))
    if (c.bLat != null && c.bLon != null) out.add(Pair(c.bLat, c.bLon))
    return out
}

@Composable
fun CourseDetailScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val runs by vm.passagesOfCourse(id).collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var course by remember { mutableStateOf<fr.volaracing.training.data.Course?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { course = vm.course(id) }

    val gated = runs.filter { it.kind == 0 }
    val ranked = runs.sortedBy { it.timeNs }
    val number = runs.sortedBy { it.id }.mapIndexed { i, p -> p.id to i + 1 }.toMap()
    val perSession = gated.groupBy { it.sessionId }.toSortedMap().map { e -> e.value.minOf { it.timeNs } / 1e9 }
    val c = course

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(c?.name ?: "Parcours", onBack = { nav.popBackStack() })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("Démarrer", Modifier.weight(1f)) { nav.navigate("run/$id/${c?.activity ?: "VTT"}") }
                    BigButton("Modifier", Modifier.weight(1f), filled = false) { nav.navigate("edit/$id") }
                    BigButton("Supprimer", Modifier.weight(1f), filled = false) { confirmDelete = true }
                }
            }
            if (perSession.isNotEmpty()) {
                item { SectionTitle("PROGRESSION (MEILLEUR TEMPS PAR SESSION)") }
                item { Sparkline(perSession) }
            }
            item { SectionTitle("CLASSEMENT DES PASSAGES") }
            if (runs.isEmpty()) item { Text("Aucun passage sur ce parcours.", color = VGray) }
            items(ranked, key = { it.id }) { p ->
                RankRow(
                    rank = ranked.indexOf(p) + 1, bib = number[p.id] ?: 0,
                    title = "Passage ${number[p.id] ?: ""}",
                    sub = "Session ${p.sessionId} - " + String.format(Locale.US, "Vmax %.1f km/h", p.vMax * 3.6),
                    timeNs = p.timeNs, gapNs = p.timeNs - ranked.first().timeNs,
                    tag = runTag(p), thumb = { RunThumb(vm, p) },
                ) { nav.navigate("lab/${p.id}") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce parcours ?") },
            text = { Text("Le parcours est supprimé. Vos sessions et runs sont conservés (ils deviennent « sans parcours »).") },
            confirmButton = { BigButton("Supprimer") { scope.launch { vm.deleteCourse(id); confirmDelete = false; nav.popBackStack() } } },
            dismissButton = { BigButton("Annuler", filled = false) { confirmDelete = false } },
        )
    }
}

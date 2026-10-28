package fr.volaracing.training.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import fr.volaracing.training.data.Course
import fr.volaracing.training.data.Passage
import fr.volaracing.training.data.Session
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- liste des groupes

@Composable
fun GroupsScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups by vm.groups.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val courses by vm.courses.collectAsState()
    var newGroup by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(false) }
    var pseudo by remember { mutableStateOf(vm.prefs.pseudo) }
    var groupName by remember { mutableStateOf("") }
    val me = vm.prefs.athleteId

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { Toast.makeText(ctx, vm.importPackage(ctx, uri, ""), Toast.LENGTH_LONG).show() }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Entre amis", onBack = { nav.popBackStack() })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Roulez sur le même parcours, échangez vos sessions par fichier (messagerie, mail...) et comparez vos runs " +
                        "dans le laboratoire. Rien n'est envoyé sur Internet : vous choisissez ce que vous partagez, et avec qui.",
                    color = VGray,
                )
            }
            item { SectionTitle("MON PROFIL") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = VSurface), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Pseudo : $pseudo", style = MaterialTheme.typography.titleMedium)
                            Text("Ce nom accompagne vos runs partagés (pas de nom réel).", color = VGray, style = MaterialTheme.typography.bodyMedium)
                        }
                        BigButton("Modifier", filled = false, height = 48.dp) { profile = true }
                    }
                }
            }
            item { SectionTitle("MES GROUPES") }
            if (groups.isEmpty()) item { Text("Aucun groupe. Créez-en un pour votre équipe ou vos amis.", color = VGray) }
            items(groups, key = { it.id }) { g ->
                val gs = sessions.filter { it.groupId == g.id }
                val members = gs.map { it.ownerId.ifEmpty { me } }.toSet().size
                Card(
                    colors = CardDefaults.cardColors(containerColor = VSurface),
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("group/${g.id}") },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(g.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(
                            "$members membre${if (members > 1) "s" else ""} - ${courses.count { it.groupId == g.id }} parcours - ${gs.size} session${if (gs.size > 1) "s" else ""}",
                            color = VGray,
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("Nouveau groupe", Modifier.weight(1f), height = 56.dp) { groupName = ""; newGroup = true }
                    BigButton("Importer un paquet", Modifier.weight(1f), filled = false, height = 56.dp) { picker.launch(arrayOf("*/*")) }
                }
            }
        }
    }

    if (newGroup) {
        AlertDialog(
            onDismissRequest = { newGroup = false },
            title = { Text("Nouveau groupe") },
            text = { OutlinedTextField(groupName, { groupName = it }, singleLine = true, label = { Text("Nom du groupe") }) },
            confirmButton = {
                BigButton("Créer") {
                    scope.launch { val id = vm.createGroup(groupName); newGroup = false; nav.navigate("group/$id") }
                }
            },
            dismissButton = { BigButton("Annuler", filled = false) { newGroup = false } },
        )
    }
    if (profile) {
        AlertDialog(
            onDismissRequest = { profile = false },
            title = { Text("Mon pseudo") },
            text = { OutlinedTextField(pseudo, { pseudo = it }, singleLine = true, label = { Text("Pseudo") }) },
            confirmButton = { BigButton("OK") { vm.prefs.pseudo = pseudo; pseudo = vm.prefs.pseudo; profile = false } },
        )
    }
}

// ---------------------------------------------------------------- detail d'un groupe

@Composable
fun GroupDetailScreen(vm: AppViewModel, nav: NavController, gid: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups by vm.groups.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val courses by vm.courses.collectAsState()
    val all by vm.allPassages.collectAsState()
    val group = groups.firstOrNull { it.id == gid }
    val myName = vm.prefs.pseudo

    var addCourse by remember { mutableStateOf(false) }
    var addSession by remember { mutableStateOf(false) }
    var shareCourse by remember { mutableStateOf(false) }
    var shareSession by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf<Session?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val gSessions = sessions.filter { it.groupId == gid }
    val gCourses = courses.filter { it.groupId == gid }
    val sMap = gSessions.associateBy { it.id }
    val gRuns = all.filter { it.sessionId in sMap.keys }
    fun ownerOf(p: Passage): String = sMap[p.sessionId]?.let { s -> if (s.origin == 1) s.ownerName else myName } ?: myName
    val members = gSessions.map { if (it.origin == 1) it.ownerName else myName }.distinct()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { Toast.makeText(ctx, vm.importPackage(ctx, uri, gid), Toast.LENGTH_LONG).show() }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(group?.name ?: "Groupe", onBack = { nav.popBackStack() })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "1. Ajoutez un parcours au groupe et envoyez-le à vos amis. 2. Chacun roule et enregistre sa session. " +
                        "3. Échangez vos sessions, puis comparez-les ci-dessous ou dans le laboratoire.",
                    color = VGray, style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("Ajouter un parcours", Modifier.weight(1f), filled = false) { addCourse = true }
                    BigButton("Envoyer un parcours", Modifier.weight(1f), filled = false, enabled = gCourses.isNotEmpty()) { shareCourse = true }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("Ajouter ma session", Modifier.weight(1f), filled = false) { addSession = true }
                    BigButton("Envoyer ma session", Modifier.weight(1f), filled = false) { shareSession = true }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("Importer un paquet reçu", Modifier.weight(1f)) { picker.launch(arrayOf("*/*")) }
                    BigButton("Supprimer le groupe", Modifier.weight(1f), filled = false) { confirmDelete = true }
                }
            }

            item { SectionTitle("MEMBRES") }
            if (members.isEmpty()) item { Text("Aucune session dans ce groupe pour l'instant.", color = VGray) }
            items(members, key = { it }) { m ->
                val n = gRuns.count { ownerOf(it) == m }
                Text("$m - $n run${if (n > 1) "s" else ""}", style = MaterialTheme.typography.bodyLarge)
            }

            // classement par parcours : meilleur run chronometre de chaque membre
            for (c in gCourses) {
                item(key = "course-${c.id}") { SectionTitle("PARCOURS : ${c.name.uppercase()}") }
                val best = gRuns.filter { it.courseId == c.id && it.kind == 0 }
                    .groupBy { ownerOf(it) }
                    .map { e -> e.value.minByOrNull { it.timeNs }!! }
                    .sortedBy { it.timeNs }
                if (best.isEmpty()) item(key = "empty-${c.id}") { Text("Aucun passage chronométré.", color = VGray) }
                items(best, key = { "best-${it.id}" }) { p ->
                    val s = sMap[p.sessionId]
                    RankRow(
                        rank = best.indexOf(p) + 1, bib = best.indexOf(p) + 1, title = ownerOf(p),
                        sub = if (s != null) sessionTitle(s) else "", timeNs = p.timeNs, gapNs = p.timeNs - best.first().timeNs,
                    ) {
                        val ids = (listOf(p) + best.filter { it.id != p.id }).take(6).joinToString(",") { it.id.toString() }
                        nav.navigate("lab/$ids")
                    }
                }
                if (best.size >= 2) item(key = "cmp-${c.id}") {
                    BigButton("Comparer tout le groupe", Modifier.fillMaxWidth(), height = 52.dp) {
                        nav.navigate("lab/" + best.take(6).joinToString(",") { it.id.toString() })
                    }
                }
            }

            item { SectionTitle("SESSIONS DU GROUPE") }
            items(gSessions, key = { "s-${it.id}" }) { s ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = VSurface),
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("session/${s.id}") },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(sessionTitle(s), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(if (s.origin == 1) "Importée de ${s.ownerName}" else "Ma session", color = VGray)
                    }
                }
            }
        }
    }

    // ---- ajouter un de mes parcours au groupe
    if (addCourse) {
        PickDialog("Ajouter un parcours", "Aucun autre parcours.", courses.filter { it.groupId != gid }.map { it.id to it.name }, { addCourse = false }) { id ->
            courses.firstOrNull { it.id == id }?.let { c -> scope.launch { vm.setCourseGroup(c, gid) } }
            addCourse = false
        }
    }
    // ---- ajouter une de mes sessions au groupe
    if (addSession) {
        PickDialog(
            "Ajouter une session", "Aucune session à ajouter.",
            sessions.filter { it.origin == 0 && it.groupId != gid }.map { it.id to sessionTitle(it) }, { addSession = false },
        ) { id ->
            sessions.firstOrNull { it.id == id }?.let { s -> scope.launch { vm.setSessionGroup(s, gid) } }
            addSession = false
        }
    }
    // ---- envoyer un parcours du groupe
    if (shareCourse) {
        PickDialog("Envoyer un parcours", "Aucun parcours dans le groupe.", gCourses.map { it.id to it.name }, { shareCourse = false }) { id ->
            val c: Course? = gCourses.firstOrNull { it.id == id }
            shareCourse = false
            if (c != null) scope.launch {
                ctx.startActivity(Intent.createChooser(vm.courseShareIntent(ctx, c), "Envoyer le parcours").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    // ---- envoyer une session : choix puis consentement explicite
    if (shareSession) {
        PickDialog(
            "Envoyer une session", "Aucune session enregistrée.",
            sessions.filter { it.origin == 0 }.map { it.id to sessionTitle(it) }, { shareSession = false },
        ) { id ->
            consent = sessions.firstOrNull { it.id == id }
            shareSession = false
        }
    }
    val cs = consent
    if (cs != null) {
        AlertDialog(
            onDismissRequest = { consent = null },
            title = { Text("Partager cette session ?") },
            text = {
                Text(
                    "Le fichier contient la trace GPS complète de vos runs (départ, trajet, arrivée), vos temps et vos vitesses, " +
                        "avec votre pseudo. Envoyez-le uniquement à des personnes de confiance.",
                )
            },
            confirmButton = {
                BigButton("Partager") {
                    scope.launch {
                        val i = vm.sessionShareIntent(ctx, cs.id)
                        consent = null
                        if (i != null) ctx.startActivity(Intent.createChooser(i, "Envoyer la session").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            },
            dismissButton = { BigButton("Annuler", filled = false) { consent = null } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce groupe ?") },
            text = { Text("Le groupe est supprimé. Les sessions et parcours restent sur votre téléphone, hors groupe.") },
            confirmButton = { BigButton("Supprimer") { scope.launch { vm.deleteGroup(gid); confirmDelete = false; nav.popBackStack() } } },
            dismissButton = { BigButton("Annuler", filled = false) { confirmDelete = false } },
        )
    }
}

/** Boite de choix simple : liste de (id, libelle). */
@Composable
private fun PickDialog(title: String, empty: String, items: List<Pair<Long, String>>, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (items.isEmpty()) item { Text(empty, color = VGray) }
                items(items, key = { it.first }) { (id, label) ->
                    Box(
                        Modifier.fillMaxWidth().background(VBg, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable { onPick(id) }.padding(14.dp),
                    ) { Text(label, maxLines = 1) }
                }
            }
        },
        confirmButton = { BigButton("Fermer", filled = false) { onDismiss() } },
    )
}

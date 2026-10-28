package fr.volaracing.training

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.volaracing.training.ui.AnalyzeScreen
import fr.volaracing.training.ui.AppViewModel
import fr.volaracing.training.ui.BigButton
import fr.volaracing.training.ui.CourseDetailScreen
import fr.volaracing.training.ui.CoursesScreen
import fr.volaracing.training.ui.EditScreen
import fr.volaracing.training.ui.GroupDetailScreen
import fr.volaracing.training.ui.GroupsScreen
import fr.volaracing.training.ui.HomeScreen
import fr.volaracing.training.ui.LabScreen
import fr.volaracing.training.ui.OnboardingScreen
import fr.volaracing.training.ui.RecordScreen
import fr.volaracing.training.ui.RunScreen
import fr.volaracing.training.ui.SessionDetailScreen
import fr.volaracing.training.ui.SettingsScreen
import fr.volaracing.training.ui.SplitScreen
import fr.volaracing.training.ui.VAccent
import fr.volaracing.training.ui.VBg
import fr.volaracing.training.ui.VGray
import fr.volaracing.training.ui.VSurface
import fr.volaracing.training.ui.VolaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VolaTheme { AppRoot() } }
    }
}

private val TABS = listOf("home" to "Accueil", "record" to "Enregistrer", "analyze" to "Analyser", "courses" to "Parcours")

@Composable
fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()
    var onboard by remember { mutableStateOf(!vm.prefs.onboardingDone) }
    var warn by remember { mutableStateOf(!vm.prefs.warningAccepted) }
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route

    if (onboard) {
        Surface(Modifier.fillMaxSize(), color = VBg) {
            OnboardingScreen { vm.prefs.onboardingDone = true; onboard = false }
        }
        return
    }

    if (warn) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Avertissement") },
            text = {
                Text(
                    "Vola Racing Training est un outil d'entraînement. Ne manipulez pas le " +
                        "téléphone en mouvement. Cette application n'est pas un équipement de sécurité " +
                        "et ne remplace ni casque, ni protections, ni jugement. " +
                        "Toutes les données restent sur votre téléphone."
                )
            },
            confirmButton = { BigButton("J'ai compris") { vm.prefs.warningAccepted = true; warn = false } },
        )
    }

    Scaffold(
        containerColor = VBg,
        bottomBar = {
            if (TABS.any { it.first == route }) {
                NavigationBar(containerColor = VSurface) {
                    TABS.forEach { (r, label) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = { nav.navigate(r) { popUpTo("home"); launchSingleTop = true } },
                            icon = { Box(Modifier.size(10.dp).background(if (route == r) VAccent else VGray, CircleShape)) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(inner)) {
            composable("home") { HomeScreen(vm, nav) }
            composable("record") { RecordScreen(vm, nav) }
            composable("analyze") { AnalyzeScreen(vm, nav) }
            composable("courses") { CoursesScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("session/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                SessionDetailScreen(vm, nav, it.arguments!!.getLong("id"))
            }
            composable("groups") { GroupsScreen(vm, nav) }
            composable("group/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                GroupDetailScreen(vm, nav, it.arguments!!.getString("id") ?: "")
            }
            composable("split/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                SplitScreen(vm, nav, it.arguments!!.getLong("id"))
            }
            composable("course/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                CourseDetailScreen(vm, nav, it.arguments!!.getLong("id"))
            }
            composable("edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                EditScreen(vm, nav, it.arguments!!.getLong("id"))
            }
            composable(
                "run/{id}/{act}",
                listOf(navArgument("id") { type = NavType.LongType }, navArgument("act") { type = NavType.StringType }),
            ) { RunScreen(vm, nav, it.arguments!!.getLong("id"), it.arguments!!.getString("act") ?: "VTT") }
            composable("lab/{ids}", listOf(navArgument("ids") { type = NavType.StringType })) {
                val ids = (it.arguments!!.getString("ids") ?: "").split(',').mapNotNull { s -> s.toLongOrNull() }
                LabScreen(vm, nav, ids)
            }
        }
    }
}

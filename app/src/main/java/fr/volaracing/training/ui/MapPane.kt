package fr.volaracing.training.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import fr.volaracing.training.core.TrackPt
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// Fond OpenStreetMap, sans cle d'API.
// Attention : la politique d'usage de tile.openstreetmap.org interdit un usage intensif
// dans une application diffusee (voir README).
const val OSM_STYLE = """{
 "version": 8,
 "sources": {"osm": {"type": "raster", "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
   "tileSize": 256, "maxzoom": 19, "attribution": "© OpenStreetMap contributors"}},
 "layers": [{"id": "osm", "type": "raster", "source": "osm"}]
}"""

// Imagerie satellite Esri (sans cle). A verifier pour une diffusion publique (voir README).
const val SAT_STYLE = """{
 "version": 8,
 "sources": {"sat": {"type": "raster",
   "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
   "tileSize": 256, "maxzoom": 19, "attribution": "Esri, Maxar, Earthstar Geographics"}},
 "layers": [{"id": "sat", "type": "raster", "source": "sat"}]
}"""

@Composable
fun MapPane(
    modifier: Modifier = Modifier,
    satellite: Boolean = false,
    onClick: ((Double, Double) -> Unit)? = null,
    onReady: (MapLibreMap, Style) -> Unit,
) {
    val ctx = LocalContext.current
    val mapView = remember { MapLibre.getInstance(ctx.applicationContext); MapView(ctx) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    val ready by rememberUpdatedState(onReady)
    val click by rememberUpdatedState(onClick)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            runCatching { mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            mapView.also { mv ->
                mv.getMapAsync { map ->
                    map.addOnMapClickListener { ll -> click?.invoke(ll.latitude, ll.longitude); click != null }
                    mapRef = map
                }
            }
        },
    )
    // (Re)charge le fond a l'ouverture et a chaque bascule plan / satellite.
    LaunchedEffect(mapRef, satellite) {
        val m = mapRef ?: return@LaunchedEffect
        m.setStyle(Style.Builder().fromJson(if (satellite) SAT_STYLE else OSM_STYLE)) { style -> ready(m, style) }
    }
}

/** Derniere position connue (null si permission absente ou position inconnue). */
@SuppressLint("MissingPermission")
fun lastKnown(ctx: Context): Pair<Double, Double>? {
    val ok = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!ok) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) ?: return null
    return Pair(l.latitude, l.longitude)
}

fun lineFeature(pts: List<Pair<Double, Double>>): Feature =
    Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.second, it.first) }))

fun addLine(style: Style, id: String, pts: List<TrackPt>, color: Int, width: Float) {
    style.addSource(GeoJsonSource(id, lineFeature(pts.map { Pair(it.lat, it.lon) })))
    style.addLayer(LineLayer("$id-layer", id).withProperties(
        PropertyFactory.lineColor(color), PropertyFactory.lineWidth(width)))
}

fun hasFine(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/** Position en direct (fil principal). Retourne la fonction qui arrete les mises a jour. */
@SuppressLint("MissingPermission")
fun startLocationUpdates(ctx: Context, onFix: (Pair<Double, Double>) -> Unit): () -> Unit {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val l = object : LocationListener {
        override fun onLocationChanged(location: Location) { onFix(Pair(location.latitude, location.longitude)) }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    try {
        lastKnown(ctx)?.let(onFix)
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, l, Looper.getMainLooper())
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, l, Looper.getMainLooper())
        }
    } catch (_: Exception) { }
    return { try { lm.removeUpdates(l) } catch (_: Exception) { } }
}

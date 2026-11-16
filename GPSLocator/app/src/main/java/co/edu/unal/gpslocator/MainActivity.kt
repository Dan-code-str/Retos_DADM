package co.edu.unal.gpslocator

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var fusedClient: FusedLocationProviderClient

    private val executor = Executors.newCachedThreadPool()
    private val poiFolder = FolderOverlay()
    private var radiusOverlay: Polygon? = null
    private var centerMarker: Marker? = null

    private var center: GeoPoint? = null
    private var centerLabel = ""
    private var appliedSettings: String? = null
    @Volatile
    private var searchId = 0

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val kmFormat = DecimalFormat("#.##")

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) locateUser()
            else setStatus(getString(R.string.status_permission_denied))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid necesita su configuración (y un User-Agent propio) antes de crear el MapView.
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = packageName
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        statusText = findViewById(R.id.statusText)
        progress = findViewById(R.id.progress)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        setupMap()
        findViewById<FloatingActionButton>(R.id.fabMyLocation).setOnClickListener { requestUserLocation() }

        val savedLat = savedInstanceState?.getDouble(KEY_LAT, Double.NaN) ?: Double.NaN
        if (savedInstanceState != null && !savedLat.isNaN()) {
            setCenter(
                GeoPoint(savedLat, savedInstanceState.getDouble(KEY_LON)),
                savedInstanceState.getString(KEY_LABEL) ?: getString(R.string.my_location),
            )
        } else {
            requestUserLocation()
        }
    }

    private fun setupMap() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        map.controller.setZoom(14.0)
        map.controller.setCenter(DEFAULT_CENTER)

        // Capa 0: toques sobre el mapa (cerrar globos / mantener presionado para elegir un punto).
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                InfoWindow.closeAllInfoWindowsOn(map)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                setCenter(p, getString(R.string.selected_point))
                return true
            }
        }))
        map.overlays.add(poiFolder)
    }

    // ---------- Ubicación ----------

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestUserLocation() {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            locateUser()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun locateUser() {
        setStatus(getString(R.string.status_locating), loading = true)
        val priority = if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        fusedClient.getCurrentLocation(priority, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onUserLocation(location)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) onUserLocation(last)
                        else setStatus(getString(R.string.status_no_location))
                    }
                }
            }
            .addOnFailureListener { setStatus(getString(R.string.status_no_location)) }
    }

    private fun onUserLocation(location: Location) {
        setCenter(GeoPoint(location.latitude, location.longitude), getString(R.string.my_location))
    }

    @Suppress("DEPRECATION") // La versión síncrona funciona en todas las API y aquí corre en segundo plano.
    private fun geocode(query: String) {
        if (!Geocoder.isPresent()) {
            setStatus(getString(R.string.status_geocoder_unavailable))
            return
        }
        setStatus(getString(R.string.status_geocoding, query), loading = true)
        executor.execute {
            val address = runCatching {
                Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1)?.firstOrNull()
            }.getOrNull()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (address == null) {
                    setStatus(getString(R.string.status_address_not_found, query))
                } else {
                    setCenter(GeoPoint(address.latitude, address.longitude), address.getAddressLine(0) ?: query)
                }
            }
        }
    }

    // ---------- Mapa y puntos de interés ----------

    private fun setCenter(point: GeoPoint, label: String) {
        center = point
        centerLabel = label

        centerMarker?.let { map.overlays.remove(it) }
        val icon = MarkerIcons.labeled(resources, label, COLOR_CENTER, pinRadiusDp = 10f)
        centerMarker = Marker(map).apply {
            position = point
            this.icon = icon.drawable
            setAnchor(Marker.ANCHOR_CENTER, icon.anchorV)
            setInfoWindowAnchor(Marker.ANCHOR_CENTER, 0f)
            title = label
            snippet = String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude)
        }
        map.overlays.add(centerMarker)
        searchPois()
    }

    private fun searchPois() {
        val c = center ?: return
        val radiusKm = Prefs.radiusKm(prefs)
        val categories = PoiCategories.byIds(Prefs.categoryIds(prefs))
        appliedSettings = Prefs.fingerprint(prefs)

        drawRadius(c, radiusKm)
        InfoWindow.closeAllInfoWindowsOn(map)
        poiFolder.items.clear()
        map.invalidate()

        if (categories.isEmpty()) {
            setStatus(getString(R.string.status_no_categories))
            return
        }

        setStatus(getString(R.string.status_searching, formatKm(radiusKm)), loading = true)
        val id = ++searchId
        executor.execute {
            // Se cancela si el usuario ya cambió de punto o de radio.
            val isCancelled = { id != searchId || isDestroyed }
            val result = runCatching {
                PoiRepository.search(c, radiusKm * 1000.0, categories, isCancelled) { partial ->
                    runOnUiThread { if (!isCancelled()) showPois(partial, radiusKm, stillSearching = true) }
                }
            }
            runOnUiThread {
                if (isCancelled()) return@runOnUiThread
                result
                    .onSuccess { showPois(it, radiusKm, stillSearching = false) }
                    .onFailure { e ->
                        setStatus(getString(R.string.status_error) + "\n(" + (e.message ?: e.javaClass.simpleName) + ")")
                    }
            }
        }
    }

    private fun drawRadius(c: GeoPoint, radiusKm: Double) {
        val points = Polygon.pointsAsCircle(c, radiusKm * 1000.0)
        val circle = radiusOverlay ?: Polygon(map).also { p ->
            p.fillPaint.color = 0x221565C0
            p.outlinePaint.color = 0xAA1565C0.toInt()
            p.outlinePaint.strokeWidth = 2.5f * resources.displayMetrics.density
            p.infoWindow = null
            p.setOnClickListener { _, _, _ -> false } // deja pasar los toques al mapa
            map.overlays.add(1, p) // debajo de los marcadores
            radiusOverlay = p
        }
        circle.points = points

        val padding = (48 * resources.displayMetrics.density).roundToInt()
        map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, padding) }
    }

    private fun showPois(pois: List<Poi>, radiusKm: Double, stillSearching: Boolean) {
        poiFolder.items.clear()
        for (poi in pois) {
            val icon = MarkerIcons.labeled(resources, poi.name, poi.category.color)
            poiFolder.add(Marker(map).apply {
                position = poi.point
                this.icon = icon.drawable
                setAnchor(Marker.ANCHOR_CENTER, icon.anchorV)
                setInfoWindowAnchor(Marker.ANCHOR_CENTER, 0f)
                title = poi.name
                snippet = getString(
                    R.string.poi_snippet,
                    getString(poi.category.label),
                    formatDistance(poi.distanceMeters),
                )
            })
        }
        // El marcador de la posición queda encima de todo.
        centerMarker?.let { map.overlays.remove(it); map.overlays.add(it) }
        map.invalidate()

        when {
            stillSearching -> setStatus(
                resources.getQuantityString(R.plurals.status_partial, pois.size, pois.size),
                loading = true,
            )
            pois.isEmpty() -> setStatus(getString(R.string.status_none, formatKm(radiusKm)))
            else -> setStatus(
                resources.getQuantityString(R.plurals.status_found, pois.size, pois.size, formatKm(radiusKm))
            )
        }
    }

    // ---------- Utilidades ----------

    private fun setStatus(text: String, loading: Boolean = false) {
        statusText.text = text
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun formatKm(km: Double) = "${kmFormat.format(km)} km"

    private fun formatDistance(meters: Double) =
        if (meters < 1000) "${meters.roundToInt()} m" else "${DecimalFormat("#.#").format(meters / 1000)} km"

    // ---------- Menú ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        (searchItem.actionView as SearchView).apply {
            queryHint = getString(R.string.search_hint)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    val q = query.trim()
                    if (q.isNotEmpty()) geocode(q)
                    searchItem.collapseActionView()
                    return true
                }

                override fun onQueryTextChange(newText: String) = false
            })
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> {
            if (center == null) requestUserLocation() else searchPois()
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ---------- Ciclo de vida ----------

    override fun onResume() {
        super.onResume()
        map.onResume()
        // Al volver de Preferencias, repite la búsqueda si cambió el radio o las categorías.
        if (center != null && Prefs.fingerprint(prefs) != appliedSettings) searchPois()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        center?.let {
            outState.putDouble(KEY_LAT, it.latitude)
            outState.putDouble(KEY_LON, it.longitude)
            outState.putString(KEY_LABEL, centerLabel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    companion object {
        private const val KEY_LAT = "center_lat"
        private const val KEY_LON = "center_lon"
        private const val KEY_LABEL = "center_label"
        private const val COLOR_CENTER = 0xFF1565C0.toInt()
        // Universidad Nacional, Bogotá: se muestra mientras llega la ubicación.
        private val DEFAULT_CENTER = GeoPoint(4.6371, -74.0826)
    }
}

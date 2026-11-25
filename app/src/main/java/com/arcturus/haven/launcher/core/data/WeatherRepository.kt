package com.arcturus.haven.launcher.core.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.arcturus.haven.launcher.core.settings.launcherPrefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt

data class CurrentWeather(
    val temperatureFahrenheit: Int,
    val weatherCode: Int,
    val isDay: Boolean,
) {
    val condition: String
        get() = when (weatherCode) {
            0 -> "Clear"
            1, 2 -> "Mostly clear"
            3 -> "Cloudy"
            45, 48 -> "Fog"
            in 51..67 -> "Rain"
            in 71..77 -> "Snow"
            in 80..82 -> "Showers"
            in 85..86 -> "Snow showers"
            in 95..99 -> "Thunderstorms"
            else -> "Current weather"
        }
}

/** Reads approximate foreground location and fetches a small current-conditions response. */
class WeatherRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.launcherPrefs()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Teres-weather").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    fun load(onResult: (CurrentWeather?) -> Unit) {
        val cached = cachedWeather()
        val hasFreshCache = cached != null && System.currentTimeMillis() - preferences.getLong(KEY_UPDATED_AT, 0L) < REFRESH_INTERVAL_MS
        val hasPermission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasFreshCache || !hasPermission) {
            deliver(cached, onResult)
            return
        }

        val manager = appContext.getSystemService(LocationManager::class.java)
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
        val recentLocation = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull(Location::getTime)

        fun fetchFallback() = worker.execute { deliver(recentLocation?.let(::fetch) ?: cachedWeather(), onResult) }

        if (recentLocation != null && System.currentTimeMillis() - recentLocation.time < LOCATION_MAX_AGE_MS) {
            fetchFallback()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && provider != null) {
            runCatching {
                manager.getCurrentLocation(provider, null, worker) { location ->
                    deliver(location?.let(::fetch) ?: recentLocation?.let(::fetch) ?: cachedWeather(), onResult)
                }
            }.onFailure { fetchFallback() }
        } else {
            fetchFallback()
        }
    }

    private fun fetch(location: Location): CurrentWeather? {
        var connection: HttpURLConnection? = null
        return try {
            val uri = Uri.Builder()
                .scheme("https")
                .authority("api.open-meteo.com")
                .appendPath("v1")
                .appendPath("forecast")
                .appendQueryParameter("latitude", location.latitude.toString())
                .appendQueryParameter("longitude", location.longitude.toString())
                .appendQueryParameter("current", "temperature_2m,weather_code,is_day")
                .appendQueryParameter("temperature_unit", "fahrenheit")
                .appendQueryParameter("timezone", "auto")
                .build()
            connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) return null
            val current = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
            CurrentWeather(
                temperatureFahrenheit = current.getDouble("temperature_2m").roundToInt(),
                weatherCode = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1,
            ).also(::cache)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun cache(weather: CurrentWeather) {
        preferences.edit {
            putInt(KEY_TEMPERATURE, weather.temperatureFahrenheit)
            putInt(KEY_CODE, weather.weatherCode)
            putBoolean(KEY_IS_DAY, weather.isDay)
            putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        }
    }

    private fun cachedWeather(): CurrentWeather? = if (!preferences.contains(KEY_TEMPERATURE)) {
        null
    } else {
        CurrentWeather(
            temperatureFahrenheit = preferences.getInt(KEY_TEMPERATURE, 0),
            weatherCode = preferences.getInt(KEY_CODE, -1),
            isDay = preferences.getBoolean(KEY_IS_DAY, true),
        )
    }

    private fun deliver(weather: CurrentWeather?, callback: (CurrentWeather?) -> Unit) {
        main.post { callback(weather) }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
        const val LOCATION_MAX_AGE_MS = 30 * 60 * 1000L
        const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        const val KEY_TEMPERATURE = "weather_temperature_f"
        const val KEY_CODE = "weather_code"
        const val KEY_IS_DAY = "weather_is_day"
        const val KEY_UPDATED_AT = "weather_updated_at"
    }
}

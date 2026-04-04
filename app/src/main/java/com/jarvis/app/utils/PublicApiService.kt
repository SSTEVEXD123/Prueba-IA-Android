package com.jarvis.app.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * PublicApiService
 * Módulo para consultar APIs públicas externas y devolver
 * datos estructurados que Jarvis puede resumir con el LLM.
 *
 * APIs disponibles:
 *   - Open-Meteo    → Clima en tiempo real (sin API key)
 *   - ExchangeRate  → Tipos de cambio (sin API key)
 *   - OpenLibrary   → Búsqueda de libros (sin API key)
 *   - Wikipedia     → Extracto de artículos (sin API key)
 *   - IP Geolocation → Geolocalización aproximada (sin API key)
 */
object PublicApiService {

    private const val TAG = "PublicApiService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ─────────────────────────────────────────────
    // CLIMA — Open-Meteo (sin API key)
    // ─────────────────────────────────────────────

    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        cityName: String = "tu ubicación"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,wind_speed_10m," +
                "weather_code,apparent_temperature" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum" +
                "&timezone=auto&forecast_days=3"

            val json = getJson(url) ?: return@withContext Result.failure(
                Exception("No se pudo obtener el clima")
            )

            val current = json.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val feelsLike = current.getDouble("apparent_temperature")
            val humidity = current.getInt("relative_humidity_2m")
            val windSpeed = current.getDouble("wind_speed_10m")
            val weatherCode = current.getInt("weather_code")
            val condition = weatherCodeToSpanish(weatherCode)

            val daily = json.getJSONObject("daily")
            val maxTemps = daily.getJSONArray("temperature_2m_max")
            val minTemps = daily.getJSONArray("temperature_2m_min")
            val precipitation = daily.getJSONArray("precipitation_sum")

            val summary = buildString {
                appendLine("🌤️ **Clima en $cityName**")
                appendLine()
                appendLine("**Ahora mismo:**")
                appendLine("• Temperatura: ${temp}°C (sensación: ${feelsLike}°C)")
                appendLine("• Condición: $condition")
                appendLine("• Humedad: $humidity%")
                appendLine("• Viento: ${windSpeed} km/h")
                appendLine()
                appendLine("**Próximos 3 días:**")
                for (i in 0 until minOf(3, maxTemps.length())) {
                    val day = when (i) {
                        0 -> "Hoy"
                        1 -> "Mañana"
                        else -> "Pasado mañana"
                    }
                    val max = maxTemps.getDouble(i)
                    val min = minTemps.getDouble(i)
                    val precip = precipitation.getDouble(i)
                    appendLine("• $day: ${min}°C – ${max}°C" +
                        if (precip > 0) " | ${precip}mm de lluvia" else "")
                }
            }

            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Error clima: ${e.message}")
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────
    // TIPOS DE CAMBIO — Frankfurter (sin API key)
    // ─────────────────────────────────────────────

    suspend fun getExchangeRates(baseCurrency: String = "USD"): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.frankfurter.app/latest?from=$baseCurrency" +
                    "&to=EUR,GBP,JPY,MXN,COP,ARS,BRL,CLP"

                val json = getJson(url) ?: return@withContext Result.failure(
                    Exception("No se pudieron obtener los tipos de cambio")
                )

                val base = json.getString("base")
                val date = json.getString("date")
                val rates = json.getJSONObject("rates")

                val summary = buildString {
                    appendLine("💱 **Tipos de cambio — $base** (${date})")
                    appendLine()
                    val keys = rates.keys()
                    while (keys.hasNext()) {
                        val currency = keys.next()
                        val rate = rates.getDouble(currency)
                        appendLine("• 1 $base = ${String.format("%.4f", rate)} $currency")
                    }
                    appendLine()
                    appendLine("_Fuente: Frankfurter API (BCE)_")
                }

                Result.success(summary)
            } catch (e: Exception) {
                Log.e(TAG, "Error exchange: ${e.message}")
                Result.failure(e)
            }
        }

    // ─────────────────────────────────────────────
    // WIKIPEDIA — Extracto en español (sin API key)
    // ─────────────────────────────────────────────

    suspend fun getWikipediaExtract(topic: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(topic, "UTF-8")
                val url = "https://es.wikipedia.org/api/rest_v1/page/summary/$encoded"

                val json = getJson(url) ?: return@withContext Result.failure(
                    Exception("Artículo no encontrado en Wikipedia")
                )

                val title = json.optString("title", topic)
                val extract = json.optString("extract", "Sin descripción disponible.")
                val pageUrl = json.optJSONObject("content_urls")
                    ?.optJSONObject("mobile")?.optString("page", "") ?: ""

                val summary = buildString {
                    appendLine("📖 **$title** — Wikipedia")
                    appendLine()
                    appendLine(extract.take(800))
                    if (extract.length > 800) appendLine("…")
                    if (pageUrl.isNotEmpty()) {
                        appendLine()
                        appendLine("🔗 Leer más: $pageUrl")
                    }
                }

                Result.success(summary)
            } catch (e: Exception) {
                Log.e(TAG, "Error Wikipedia: ${e.message}")
                Result.failure(e)
            }
        }

    // ─────────────────────────────────────────────
    // LIBROS — Open Library (sin API key)
    // ─────────────────────────────────────────────

    suspend fun searchBooks(query: String, limit: Int = 5): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://openlibrary.org/search.json?q=$encoded&limit=$limit&language=spa"

                val json = getJson(url) ?: return@withContext Result.failure(
                    Exception("No se encontraron resultados")
                )

                val docs = json.getJSONArray("docs")
                val numFound = json.getInt("numFound")

                val summary = buildString {
                    appendLine("📚 **Resultados para \"$query\"** ($numFound encontrados)")
                    appendLine()
                    for (i in 0 until minOf(limit, docs.length())) {
                        val doc = docs.getJSONObject(i)
                        val title = doc.optString("title", "Sin título")
                        val authors = doc.optJSONArray("author_name")
                            ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                            ?.take(2)?.joinToString(", ") ?: "Autor desconocido"
                        val year = doc.optInt("first_publish_year", 0)
                        val yearStr = if (year > 0) " ($year)" else ""
                        appendLine("${i + 1}. **$title**$yearStr — $authors")
                    }
                    appendLine()
                    appendLine("_Fuente: Open Library_")
                }

                Result.success(summary)
            } catch (e: Exception) {
                Log.e(TAG, "Error books: ${e.message}")
                Result.failure(e)
            }
        }

    // ─────────────────────────────────────────────
    // IP GEOLOCATION — ip-api.com (sin API key)
    // ─────────────────────────────────────────────

    suspend fun getIpInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = getJson("http://ip-api.com/json/?lang=es&fields=country,regionName,city,isp,lat,lon")
                ?: return@withContext Result.failure(Exception("No se pudo obtener la IP"))

            val city = json.optString("city", "?")
            val region = json.optString("regionName", "?")
            val country = json.optString("country", "?")
            val isp = json.optString("isp", "?")
            val lat = json.optDouble("lat", 0.0)
            val lon = json.optDouble("lon", 0.0)

            Result.success(
                "🌍 **Ubicación aproximada por IP**\n" +
                "• Ciudad: $city, $region, $country\n" +
                "• Coordenadas: $lat, $lon\n" +
                "• Proveedor: $isp"
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────
    // Interno: petición HTTP genérica
    // ─────────────────────────────────────────────

    private fun getJson(url: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "JarvisApp/1.0 Android")
                .header("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            JSONObject(body)
        } catch (e: Exception) {
            Log.e(TAG, "HTTP error: ${e.message}")
            null
        }
    }

    private fun weatherCodeToSpanish(code: Int): String = when (code) {
        0 -> "☀️ Cielo despejado"
        1, 2, 3 -> "⛅ Parcialmente nublado"
        45, 48 -> "🌫️ Niebla"
        51, 53, 55 -> "🌦️ Llovizna"
        61, 63, 65 -> "🌧️ Lluvia"
        71, 73, 75 -> "❄️ Nevada"
        80, 81, 82 -> "🌧️ Chubascos"
        95 -> "⛈️ Tormenta"
        96, 99 -> "⛈️ Tormenta con granizo"
        else -> "🌤️ Variable"
    }
}

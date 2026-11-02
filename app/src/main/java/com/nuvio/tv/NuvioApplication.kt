package com.nuvio.tv

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.nuvio.tv.core.image.CustomPosterFallbackInterceptor
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.gif.GifDecoder
import coil3.gif.AnimatedImageDecoder
import coil3.svg.SvgDecoder
import coil3.request.crossfade
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.bitmapFactoryMaxParallelism

import okio.Path.Companion.toOkioPath
import com.nuvio.tv.core.diagnostics.SentryInitializer
import com.nuvio.tv.core.image.StaleWhileRevalidateCacheStrategy
import com.nuvio.tv.core.runtime.PluginRuntimeHooks
import com.nuvio.tv.core.sync.androidtv.AndroidTvChannelSyncService
import com.nuvio.tv.core.network.IPv4FirstDns
import com.nuvio.tv.data.local.ImagePerformancePreferences
import com.nuvio.tv.data.local.SentrySettingsDataStore
import com.nuvio.tv.data.simkl.SimklAnimeIdPreferenceHolder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var androidTvChannelSyncService: AndroidTvChannelSyncService
    @Inject lateinit var sentrySettingsDataStore: SentrySettingsDataStore
    @Inject lateinit var imagePerformancePreferences: ImagePerformancePreferences
    @Inject lateinit var simklAnimeIdPreferenceHolder: SimklAnimeIdPreferenceHolder
    @Inject lateinit var streamPrefetcher: dagger.Lazy<com.nuvio.tv.core.streams.StreamPrefetcher>
    @Inject lateinit var addonRepository: dagger.Lazy<com.nuvio.tv.domain.repository.AddonRepository>
    private val startupScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    companion object {
        /**
         * Shared cookie jar for CloudStream extension HTTP requests.
         * Accessible so the player's OkHttpClient can share cookies
         * obtained during scraping (e.g., session tokens needed for playback).
         */
        val extensionCookieJar: CookieJar = object : CookieJar {
            private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val hostCookies = store[url.host] ?: return emptyList()
                synchronized(hostCookies) {
                    return hostCookies.filter { cookie ->
                        cookie.expiresAt > System.currentTimeMillis()
                    }
                }
            }

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val hostCookies = store.getOrPut(url.host) { mutableListOf() }
                synchronized(hostCookies) {
                    cookies.forEach { newCookie ->
                        hostCookies.removeAll { it.name == newCookie.name }
                        hostCookies.add(newCookie)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        // Before anything else: the TMDB and Gemini keys are read synchronously from here on.
        com.nuvio.tv.core.apikeys.ApiKeys.init(this)
        super.onCreate()
        SentryInitializer.start(this, sentrySettingsDataStore)
        PluginRuntimeHooks.onApplicationCreate(this)
        androidTvChannelSyncService.start()
        // Off the main thread and after launch: building the stream stack (repositories, HTTP
        // clients, plugin manager) is not free on a TV CPU and used to run before the first frame.
        startupScope.launch {
            kotlinx.coroutines.delay(3_000)
            streamPrefetcher.get().warmUpAddonHosts()
        }
        installDefaultAddonsOnce()
        // Load locale synchronously so it's available before Activity.attachBaseContext.
        // SharedPreferences reads are fast (cached in memory after first access).
        val tag = getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            .getString("locale_tag", null)
        // Spanish (Latin America) until someone picks another language (or System) in Settings, which stores a value.
        LocaleCache.localeTag = tag ?: DEFAULT_APP_LOCALE_TAG
    }

    /** First launch only: puts the build's default addons in. Removing one later sticks, because the flag stays set. */
    private fun installDefaultAddonsOnce() {
        val urls = com.nuvio.tv.core.build.AppFeaturePolicy.defaultAddonUrls
        if (urls.isEmpty()) return
        val prefs = getSharedPreferences("default_addons", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return
        startupScope.launch {
            val repository = addonRepository.get()
            if (repository.getInstalledAddons().first().isNotEmpty()) {
                prefs.edit().putBoolean("seeded", true).apply()
                return@launch
            }
            urls.forEach { runCatching { repository.addAddon(it) } }
            // Stay unset when the manifest could not be fetched (offline), so the next launch tries again.
            if (repository.getInstalledAddons().first().isNotEmpty()) {
                prefs.edit().putBoolean("seeded", true).apply()
            }
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val imageOkHttpClient by lazy {
            val imageDispatcher = okhttp3.Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 16
            }
            OkHttpClient.Builder()
                .dispatcher(imageDispatcher)
                .dns(IPv4FirstDns())
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    try {
                        chain.proceed(chain.request())
                    } catch (e: java.net.SocketTimeoutException) {
                        chain.withConnectTimeout(3, TimeUnit.SECONDS)
                            .withReadTimeout(4, TimeUnit.SECONDS)
                            .proceed(chain.request())
                    }
                }
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        val imageLoaderRef: () -> ImageLoader = { SingletonImageLoader.get(this) }

        return ImageLoader.Builder(this)
            .components {
                add(CustomPosterFallbackInterceptor())
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
                add(
                    coil3.network.okhttp.OkHttpNetworkFetcherFactory(
                        callFactory = { imageOkHttpClient },
                        cacheStrategy = {
                            StaleWhileRevalidateCacheStrategy(
                                revalidationClient = { imageOkHttpClient },
                                imageLoaderProvider = imageLoaderRef,
                            )
                        },
                    )
                )
            }
            .memoryCache {
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
                // Low-RAM devices (≤2GB): use 0.15 — larger cache reduces GC pressure
                // from rapid bitmap eviction during scrolling.
                // Mid-range devices (≤3GB): use 0.20 for decent image caching.
                // Normal devices (>3GB): use 0.25 for snappy image loading.
                // - hardware bitmaps (one copy, in graphics memory) are a Beta speed-ups switch, off by default
                val cachePercent = when {
                    totalRamMb <= 2048 -> 0.15
                    totalRamMb <= 3072 -> 0.20
                    else -> 0.25
                }
                MemoryCache.Builder()
                    .maxSizePercent(context, cachePercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .precision(coil3.size.Precision.INEXACT)
            .allowHardware(imagePerformancePreferences.hardwareBitmapsEnabled)
            .allowRgb565(imagePerformancePreferences.rgb565Enabled)
            .bitmapFactoryMaxParallelism(4)
            .build()
    }
}

/** The app language on a device where none has been chosen yet. */
internal val DEFAULT_APP_LOCALE_TAG: String get() = com.nuvio.tv.core.build.AppFeaturePolicy.defaultAppLocaleTag

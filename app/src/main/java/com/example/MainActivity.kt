package com.example

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.databinding.ActivityMainBinding
import com.example.databinding.ItemAppCardBinding
import com.example.databinding.DialogUpdateBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // App classification categories
    private val appsList = mutableListOf<AppItem>()
    private val gamesList = mutableListOf<AppItem>()
    private val streamingList = mutableListOf<AppItem>()
    private val toolsList = mutableListOf<AppItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start clock routine
        startClock()

        // Load and classify installed applications
        loadInstalledApps()

        // Configure left category tabs listeners and focus events
        setupCategoryTabs()

        // Configure setting options and theme customizations
        setupSettingsControls()

        // Trigger automatic update check
        checkForUpdates(forceShowDialog = false)
    }

    private fun startClock() {
        lifecycleScope.launch {
            while (isActive) {
                val calendar = Calendar.getInstance()
                val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))

                binding.clockText.text = clockFormat.format(calendar.time)
                val formattedDate = dateFormat.format(calendar.time)
                binding.dateText.text = formattedDate.replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
                }

                delay(1000)
            }
        }
    }

    private fun loadInstalledApps() {
        appsList.clear()
        gamesList.clear()
        streamingList.clear()
        toolsList.clear()

        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        // Filter out our own launcher app from the list
        val filteredList = resolveInfos.filter { 
            it.activityInfo.packageName != packageName 
        }

        for (info in filteredList) {
            val appLabel = info.loadLabel(pm).toString()
            val appIcon = info.loadIcon(pm)
            val launchIntent = pm.getLaunchIntentForPackage(info.activityInfo.packageName)
            
            val appItem = AppItem(
                name = appLabel,
                packageName = info.activityInfo.packageName,
                icon = appIcon,
                launchIntent = launchIntent
            )

            // Classify app using intelligent heuristics
            when (classifyApp(info, pm)) {
                "Juegos" -> gamesList.add(appItem)
                "Streaming" -> streamingList.add(appItem)
                "Herramientas" -> toolsList.add(appItem)
                else -> appsList.add(appItem)
            }
        }

        // Sort all lists alphabetically
        appsList.sortBy { it.name.lowercase() }
        gamesList.sortBy { it.name.lowercase() }
        streamingList.sortBy { it.name.lowercase() }
        toolsList.sortBy { it.name.lowercase() }
    }

    private fun classifyApp(resolveInfo: ResolveInfo, pm: PackageManager): String {
        val packageName = resolveInfo.activityInfo.packageName.lowercase()
        val appLabel = resolveInfo.loadLabel(pm).toString().lowercase()

        // Match against explicit Android package category hints (API 26+)
        try {
            val appInfo = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                    return "Juegos"
                }
                if (appInfo.category == ApplicationInfo.CATEGORY_VIDEO || 
                    appInfo.category == ApplicationInfo.CATEGORY_AUDIO) {
                    return "Streaming"
                }
            }
        } catch (e: Exception) {
            // Ignored, proceed to keyword heuristics
        }

        // Keyword matches for Games
        val gameKeywords = listOf("game", "juego", "play", "arcade", "retroarch", "emulator", "nintendo", "steam", "xbox", "pubg")
        if (gameKeywords.any { packageName.contains(it) || appLabel.contains(it) }) {
            return "Juegos"
        }

        // Keyword matches for Streaming
        val streamingKeywords = listOf(
            "youtube", "netflix", "primevideo", "hulu", "disney", "twitch", "plex", "kodi", "vlc", "hbo", "max", 
            "spotify", "rtve", "movistar", "crunchyroll", "paramount", "player", "tv", "music", "video", "cinema", "stream"
        )
        if (streamingKeywords.any { packageName.contains(it) || appLabel.contains(it) }) {
            return "Streaming"
        }

        // Keyword matches for Tools
        val toolKeywords = listOf(
            "setting", "config", "file", "explor", "tool", "clean", "download", "system", "terminal", "browser", 
            "chrome", "firefox", "opera", "manager", "installer", "backup", "security", "antivirus", "store", "playstore"
        )
        if (toolKeywords.any { packageName.contains(it) || appLabel.contains(it) }) {
            return "Herramientas"
        }

        return "Aplicaciones"
    }

    private fun setupCategoryTabs() {
        val tabs = listOf(
            Triple(binding.tabApps, "Aplicaciones", appsList),
            Triple(binding.tabGames, "Juegos", gamesList),
            Triple(binding.tabStreaming, "Streaming", streamingList),
            Triple(binding.tabTools, "Herramientas", toolsList),
            Triple(binding.tabSettings, "Ajustes", emptyList<AppItem>())
        )

        tabs.forEach { (tabLayout, categoryName, appList) ->
            tabLayout.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
                    view.isSelected = true
                    
                    // Show corresponding category details immediately
                    showCategory(categoryName, appList)
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    view.isSelected = false
                }
            }

            tabLayout.setOnClickListener {
                showCategory(categoryName, appList)
            }
        }

        // Default initial category selection
        binding.tabApps.requestFocus()
        showCategory("Aplicaciones", appsList)
    }

    private fun showCategory(categoryName: String, list: List<AppItem>) {
        binding.categoryTitle.text = categoryName

        if (categoryName == "Ajustes") {
            binding.appsGrid.visibility = View.GONE
            binding.emptyStateView.visibility = View.GONE
            binding.settingsScrollContainer.visibility = View.VISIBLE
        } else {
            binding.settingsScrollContainer.visibility = View.GONE
            binding.appsGrid.visibility = View.VISIBLE
            
            if (list.isEmpty()) {
                binding.appsGrid.visibility = View.GONE
                binding.emptyStateView.visibility = View.VISIBLE
            } else {
                binding.emptyStateView.visibility = View.GONE
                binding.appsGrid.adapter = AppsGridAdapter(list)
            }
        }
    }

    private fun setupSettingsControls() {
        val focusableSettings = listOf(
            binding.cardAutoUpdate,
            binding.cardCheckNow,
            binding.bgBtnPremium,
            binding.bgBtnNeon,
            binding.bgBtnGold
        )

        focusableSettings.forEach { card ->
            card.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }
        }

        // Auto update settings persistence
        val prefs = getSharedPreferences("TV_LAUNCHER_PREFS", MODE_PRIVATE)
        var isAutoUpdateEnabled = prefs.getBoolean("AUTO_UPDATE_ENABLED", true)
        binding.chkAutoUpdate.isChecked = isAutoUpdateEnabled
        binding.txtAutoUpdateStatus.text = if (isAutoUpdateEnabled) "Estado: Activado" else "Estado: Desactivado"

        binding.cardAutoUpdate.setOnClickListener {
            isAutoUpdateEnabled = !isAutoUpdateEnabled
            binding.chkAutoUpdate.isChecked = isAutoUpdateEnabled
            binding.txtAutoUpdateStatus.text = if (isAutoUpdateEnabled) "Estado: Activado" else "Estado: Desactivado"
            prefs.edit().putBoolean("AUTO_UPDATE_ENABLED", isAutoUpdateEnabled).apply()
            Toast.makeText(this, "Actualización automática: " + (if (isAutoUpdateEnabled) "Activada" else "Desactivada"), Toast.LENGTH_SHORT).show()
        }

        binding.cardCheckNow.setOnClickListener {
            checkForUpdates(forceShowDialog = true)
        }

        // Custom preset themes click actions
        binding.bgBtnPremium.setOnClickListener {
            saveBackgroundStyle("premium")
        }
        binding.bgBtnNeon.setOnClickListener {
            saveBackgroundStyle("neon")
        }
        binding.bgBtnGold.setOnClickListener {
            saveBackgroundStyle("gold")
        }

        // Load initial style
        applyBackgroundStyle(prefs.getString("BG_STYLE", "premium") ?: "premium")
    }

    private fun applyBackgroundStyle(style: String) {
        val overlay = binding.gradientOverlay
        when (style) {
            "premium" -> {
                overlay.setBackgroundColor(Color.parseColor("#F50F1115")) // Bento Slate Black background
                binding.brandTitle.setTextColor(Color.parseColor("#3B82F6")) // Bento Blue
                binding.clockText.setTextColor(Color.parseColor("#FFFFFF")) // Pure white
            }
            "neon" -> {
                overlay.setBackgroundColor(Color.parseColor("#F50A0F1F")) // Bento Deep Navy/Neon background
                binding.brandTitle.setTextColor(Color.parseColor("#00E5FF")) // Neon Cyan
                binding.clockText.setTextColor(Color.parseColor("#00E5FF"))
            }
            "gold" -> {
                overlay.setBackgroundColor(Color.parseColor("#F51C1510")) // Bento Amber/Gold background
                binding.brandTitle.setTextColor(Color.parseColor("#F97316")) // Bento Orange-500
                binding.clockText.setTextColor(Color.parseColor("#FFD700"))
            }
        }
    }

    private fun saveBackgroundStyle(style: String) {
        getSharedPreferences("TV_LAUNCHER_PREFS", MODE_PRIVATE)
            .edit().putString("BG_STYLE", style).apply()
        applyBackgroundStyle(style)
        Toast.makeText(this, "Fondo aplicado con éxito", Toast.LENGTH_SHORT).show()
    }

    private fun checkForUpdates(forceShowDialog: Boolean) {
        val prefs = getSharedPreferences("TV_LAUNCHER_PREFS", MODE_PRIVATE)
        val isAutoCheckEnabled = prefs.getBoolean("AUTO_UPDATE_ENABLED", true)

        if (!forceShowDialog && !isAutoCheckEnabled) return

        if (forceShowDialog) {
            Toast.makeText(this, "Buscando actualizaciones...", Toast.LENGTH_SHORT).show()
        }

        // Background online check using OkHttp
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // A reliable, online mock test URL for our update.json metadata
                val url = "https://raw.githubusercontent.com/kb0528256/tv-premium-launcher/main/update.json"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val onlineVersionCode = json.optInt("versionCode", 1)
                        val onlineVersionName = json.optString("versionName", "1.0")
                        val apkUrl = json.optString("apkUrl", "")
                        val changelog = json.optString("changelog", "Mejoras de rendimiento y diseño premium.")

                        val currentVersionCode = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                            } else {
                                @Suppress("DEPRECATION")
                                packageManager.getPackageInfo(packageName, 0).versionCode
                            }
                        } catch (e: Exception) {
                            1
                        }

                        if (onlineVersionCode > currentVersionCode) {
                            withContext(Dispatchers.Main) {
                                binding.updateBadge.visibility = View.VISIBLE
                                showUpdateDialog(onlineVersionName, onlineVersionCode, apkUrl, changelog)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                binding.updateBadge.visibility = View.GONE
                                if (forceShowDialog) {
                                    Toast.makeText(this@MainActivity, "Estás en la última versión disponible.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (forceShowDialog) {
                                Toast.makeText(this@MainActivity, "Servidor no disponible (Código ${response.code})", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (forceShowDialog) {
                        Toast.makeText(this@MainActivity, "No se pudo conectar al servidor de actualización.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(versionName: String, versionCode: Int, apkUrl: String, changelog: String) {
        val dialogBinding = DialogUpdateBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        builder.setView(dialogBinding.root)
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(true)

        dialogBinding.updateVersionTitle.text = "Versión: $versionName (Código $versionCode)"
        dialogBinding.updateChangelog.text = changelog

        // Focus scale events on action buttons
        val buttons = listOf(dialogBinding.btnCancelUpdate, dialogBinding.btnConfirmUpdate)
        buttons.forEach { btn ->
            btn.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }
        }

        dialogBinding.btnCancelUpdate.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmUpdate.setOnClickListener {
            dialog.dismiss()
            startApkDownload(apkUrl)
        }

        dialog.show()
        dialogBinding.btnConfirmUpdate.requestFocus()
    }

    private fun startApkDownload(apkUrl: String) {
        Toast.makeText(this, "Descargando APK de actualización...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(apkUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val apkFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "tv_premium_launcher_update.apk")
                        if (apkFile.exists()) {
                            apkFile.delete()
                        }

                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(apkFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Descarga completada. Instalando...", Toast.LENGTH_LONG).show()
                            installApk(apkFile)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Fallo en descarga: HTTP ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error de red al descargar el APK.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun installApk(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val authority = "com.aistudio.tvpremiumlauncher.qrxmzw.fileprovider"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val apkUri = FileProvider.getUriForFile(this, authority, file)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            // Verify and prompt Unknown Unknown app install source configuration on API 26+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(this, "Permite orígenes desconocidos para este launcher.", Toast.LENGTH_LONG).show()
                    val requestIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(requestIntent)
                    return
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Instalador no soportado: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Grid adapter class for launcher items
    inner class AppsGridAdapter(private val apps: List<AppItem>) : BaseAdapter() {
        override fun getCount(): Int = apps.size
        override fun getItem(position: Int): Any = apps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: ViewHolder

            if (convertView == null) {
                val itemBinding = ItemAppCardBinding.inflate(layoutInflater, parent, false)
                view = itemBinding.root
                holder = ViewHolder(itemBinding)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }

            val app = apps[position]
            holder.binding.appName.text = app.name
            holder.binding.appIcon.setImageDrawable(app.icon)

            // Setup focus change animations
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }

            view.setOnClickListener {
                if (app.launchIntent != null) {
                    try {
                        startActivity(app.launchIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "No se pudo abrir: ${app.name}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "La aplicación no posee launcher intent", Toast.LENGTH_SHORT).show()
                }
            }

            return view
        }

        private inner class ViewHolder(val binding: ItemAppCardBinding)
    }

    // Individual Application model
    data class AppItem(
        val name: String,
        val packageName: String,
        val icon: Drawable,
        val launchIntent: Intent?
    )
}

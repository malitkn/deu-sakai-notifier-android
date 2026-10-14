package tr.edu.deu.sakainotifier.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tr.edu.deu.sakainotifier.data.api.AuthResult
import tr.edu.deu.sakainotifier.data.api.DeuWebmailClient
import tr.edu.deu.sakainotifier.data.api.WebmailTestResult
import tr.edu.deu.sakainotifier.data.api.SakaiApiClient
import tr.edu.deu.sakainotifier.data.api.SakaiAuthClient
import tr.edu.deu.sakainotifier.data.api.SakaiUsernameUtils
import tr.edu.deu.sakainotifier.data.model.SakaiAccount
import tr.edu.deu.sakainotifier.data.model.SakaiNotification
import tr.edu.deu.sakainotifier.data.storage.NotificationStateManager
import tr.edu.deu.sakainotifier.data.storage.SecurePreferencesManager
import tr.edu.deu.sakainotifier.data.updater.AppUpdateInfo
import tr.edu.deu.sakainotifier.data.updater.UpdateChecker
import tr.edu.deu.sakainotifier.notification.NotificationHelper
import tr.edu.deu.sakainotifier.ui.theme.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import tr.edu.deu.sakainotifier.worker.AlarmScheduler
import tr.edu.deu.sakainotifier.worker.WorkScheduler

enum class NotificationTypeFilter(val label: String) {
    ALL("Tümü"),
    SAKAI("🎓 Sakai"),
    MAIL("✉️ E-posta")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    securePrefs: SecurePreferencesManager,
    stateManager: NotificationStateManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authClient = remember { SakaiAuthClient() }
    val apiClient = remember { SakaiApiClient(authClient) }
    val webmailClient = remember { DeuWebmailClient() }

    var registeredAccounts by remember { mutableStateOf(securePrefs.getAccounts()) }
    var editingAccountUsername by remember { mutableStateOf<String?>(null) }
    var inputUsername by remember { mutableStateOf("") }
    var inputPassword by remember { mutableStateOf("") }
    var inputEmail by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isMonitoringActive by remember { mutableStateOf(securePrefs.isMonitoringActive) }
    var isMailMonitoringActive by remember { mutableStateOf(securePrefs.isMailMonitoringActive) }
    var selectedInterval by remember { mutableStateOf(securePrefs.pollIntervalMinutes) }

    var isAddingAccount by remember { mutableStateOf(false) }
    var accountFeedbackText by remember { mutableStateOf<String?>(null) }
    var accountFeedbackSuccess by remember { mutableStateOf(false) }
    var isFeedbackWarning by remember { mutableStateOf(false) }
    var pendingBypassUser by remember { mutableStateOf<String?>(null) }
    var pendingBypassPassword by remember { mutableStateOf<String?>(null) }
    var pendingBypassEmail by remember { mutableStateOf<String?>(null) }

    var isManualChecking by remember { mutableStateOf(false) }
    var manualCheckResultText by remember { mutableStateOf<String?>(null) }
    var manualCheckSuccess by remember { mutableStateOf(false) }
    var recentNotifications by remember {
        mutableStateOf(stateManager.getAllRecentNotifications(securePrefs.getAccounts().map { it.username }))
    }

    // Filter & Clear state
    var selectedAccountFilter by remember { mutableStateOf<String?>(null) }
    var selectedTypeFilter by remember { mutableStateOf(NotificationTypeFilter.ALL) }
    var showClearDialog by remember { mutableStateOf(false) }

    // In-App Update state
    var appUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateBannerDismissed by remember { mutableStateOf(false) }
    var isCheckingUpdateManual by remember { mutableStateOf(false) }

    val currentAppVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.2.4"
        } catch (_: Exception) {
            "1.2.4"
        }
    }

    // Battery Optimization State (Xiaomi, Samsung background reliability)
    var isBatteryOptimizationIgnored by remember {
        mutableStateOf(
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            } catch (_: Exception) {
                true
            }
        )
    }
    var isBatteryGuideDismissed by remember { mutableStateOf(false) }

    // App Hibernation / Unused App Restrictions State (Android 11+ / API 30+)
    var isHibernationWhitelisted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    context.packageManager.isAutoRevokeWhitelisted
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        )
    }
    var isHibernationGuideDismissed by remember { mutableStateOf(false) }

    // Re-check system background & hibernation permissions when returning from Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                isBatteryOptimizationIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        isHibernationWhitelisted = context.packageManager.isAutoRevokeWhitelisted
                    } catch (_: Exception) {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Check for app updates on launch
    LaunchedEffect(Unit) {
        val update = UpdateChecker.checkForUpdates(currentAppVersion)
        if (update != null && update.hasUpdate) {
            appUpdateInfo = update
        }
    }

    // Auto-refresh notifications list when background worker updates SharedPreferences
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key.startsWith(NotificationStateManager.KEY_RECENT_NOTIFICATIONS)) {
                recentNotifications = stateManager.getAllRecentNotifications(securePrefs.getAccounts().map { it.username })
            }
        }
        stateManager.registerChangeListener(listener)
        onDispose {
            stateManager.unregisterChangeListener(listener)
        }
    }

    val displayedNotifications = remember(recentNotifications, selectedAccountFilter, selectedTypeFilter) {
        recentNotifications.filter { notif ->
            val matchesAccount = selectedAccountFilter == null ||
                notif.accountUsername?.substringBefore("@").equals(selectedAccountFilter, ignoreCase = true)
            val matchesType = when (selectedTypeFilter) {
                NotificationTypeFilter.ALL -> true
                NotificationTypeFilter.SAKAI -> !notif.isEmail
                NotificationTypeFilter.MAIL -> notif.isEmail
            }
            matchesAccount && matchesType
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DEU Sakai Notifier",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Dokuz Eylül Üniversitesi LMS & Webmail v$currentAppVersion",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeuNavy
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. In-App Update Alert Banner
            if (appUpdateInfo != null && !updateBannerDismissed) {
                item {
                    val update = appUpdateInfo!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        ),
                        border = BorderStroke(1.2.dp, DeuSuccess.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🚀", fontSize = 16.sp)
                                    Text(
                                        text = "Yeni Sürüm Mevcut (${update.latestVersion})",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20),
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = "Yeni özellikler ve iyileştirmeler için güncelleyin.",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DeuSuccess),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("İndir 📥", fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = { updateBannerDismissed = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text("✕", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            // 1. Service Status Card
            item {
                val isAnyActive = isMonitoringActive || isMailMonitoringActive
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isAnyActive) DeuSuccess.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isAnyActive) "Arka Plan Takibi Aktif" else "Arka Plan Takibi Durduruldu",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isAnyActive)
                                        "Kayıtlı hesaplar her $selectedInterval dakikada bir taranıyor."
                                    else
                                        "Bildirimleri almak için aşağıdaki servisleri başlatın.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAnyActive) DeuSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isAnyActive) DeuSuccess.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = if (isAnyActive) "● ÇALIŞIYOR" else "○ DURDURULDU",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isAnyActive) DeuSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Sakai Announcement Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🎓 Sakai Duyuru Takibi",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Duyuru ve ödevler için bildirim gönderir.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isMonitoringActive,
                                onCheckedChange = { active ->
                                    if (active && registeredAccounts.isEmpty()) {
                                        Toast.makeText(context, "Lütfen önce en az bir öğrenci hesabı ekleyin.", Toast.LENGTH_SHORT).show()
                                        return@Switch
                                    }
                                    isMonitoringActive = active
                                    securePrefs.isMonitoringActive = active
                                    if (active || isMailMonitoringActive) {
                                        AlarmScheduler.scheduleNext(context, selectedInterval)
                                        WorkScheduler.schedule(context, selectedInterval, forceUpdate = true)
                                    } else {
                                        AlarmScheduler.cancel(context)
                                        WorkScheduler.cancel(context)
                                    }
                                    Toast.makeText(context, if (active) "Sakai takibi başlatıldı." else "Sakai takibi durduruldu.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // Student Webmail Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "✉️ Öğrenci E-postası Takibi",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Gelen yeni öğrenci mailleri için bildirim gönderir.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isMailMonitoringActive,
                                onCheckedChange = { active ->
                                    if (active && registeredAccounts.none { !it.emailAddress.isNullOrBlank() }) {
                                        Toast.makeText(
                                            context,
                                            "Öğrenci e-postası takibi için önce hesabınıza e-posta adresi ekleyin (✏️ Düzenle).",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@Switch
                                    }
                                    isMailMonitoringActive = active
                                    securePrefs.isMailMonitoringActive = active
                                    if (active || isMonitoringActive) {
                                        AlarmScheduler.scheduleNext(context, selectedInterval)
                                        WorkScheduler.schedule(context, selectedInterval, forceUpdate = true)
                                    } else {
                                        AlarmScheduler.cancel(context)
                                        WorkScheduler.cancel(context)
                                    }
                                    Toast.makeText(context, if (active) "Öğrenci e-postası takibi başlatıldı." else "Öğrenci e-postası takibi durduruldu.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            // Battery Optimization Warning Card (For Xiaomi, Samsung, HyperOS background reliability)
            if (!isBatteryOptimizationIgnored && !isBatteryGuideDismissed) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF8E1)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🔋", fontSize = 16.sp)
                                    Text(
                                        text = "Kesintisiz Arka Plan Uyarısı",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFFB78103)
                                    )
                                }
                                IconButton(
                                    onClick = { isBatteryGuideDismissed = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("✕", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            Text(
                                text = "Xiaomi, Samsung vb. cihazlarda ekran kapalıyken bildirimlerin durmaması için:\n• Pil Tasarrufu: 'Kısıtlama Yok' yapın.\n• Xiaomi / HyperOS: 'Otomatik Başlatma' iznini açın.\n• Son uygulamalar ekranında DEU Sakai'yi kilit 🔒 simgesiyle sabitleyin.",
                                fontSize = 12.sp,
                                color = Color.DarkGray,
                                lineHeight = 16.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (Build.MANUFACTURER.contains("xiaomi", ignoreCase = true) ||
                                    Build.MANUFACTURER.contains("redmi", ignoreCase = true) ||
                                    Build.BRAND.contains("xiaomi", ignoreCase = true) ||
                                    Build.BRAND.contains("poco", ignoreCase = true)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent().apply {
                                                    component = android.content.ComponentName(
                                                        "com.miui.securitycenter",
                                                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                                    )
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                try {
                                                    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    }
                                                    context.startActivity(fallbackIntent)
                                                } catch (_: Exception) {
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Otomatik Başlatma 🚀", fontSize = 11.sp)
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(fallbackIntent)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Pil Ayarı ⚙️", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // App Hibernation / Unused App Restrictions Warning Card (Android 11+)
            if (!isHibernationWhitelisted && !isHibernationGuideDismissed) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFEFF6FF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("⏳", fontSize = 16.sp)
                                    Text(
                                        text = "Uygulama Etkinliği Duraklatma Uyarısı",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                                IconButton(
                                    onClick = { isHibernationGuideDismissed = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("✕", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            Text(
                                text = "Android sistemi, uzun süre açılmayan uygulamaların bildirim izinlerini ve arka plan kontrollerini otomatik durdurur. Bildirimlerin kesilmemesi için uygulama ayarlarındaki 'Kullanılmıyorsa etkinliği duraklat' (veya 'İzinleri kaldır') seçeneğini kapatmanız önerilir.",
                                fontSize = 12.sp,
                                color = Color.DarkGray,
                                lineHeight = 16.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Ayarlar açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Uygulama Ayarlarını Aç ⚙️", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Multi-Account Management Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Kayıtlı Öğrenci Hesapları (${registeredAccounts.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Eklenen tüm hesaplar arka planda birlikte taranır. Bildirimler öğrenci numarası ile etiketlenir.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        // Registered accounts list
                        if (registeredAccounts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                registeredAccounts.forEach { acc ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    color = DeuNavy.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "👤",
                                                        fontSize = 14.sp,
                                                        modifier = Modifier.padding(6.dp)
                                                    )
                                                }
                                                Column {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = acc.shortUsername,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (!acc.emailAddress.isNullOrBlank()) {
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "✉️ Mail Bağlı",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = acc.username,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (!acc.emailAddress.isNullOrBlank()) {
                                                        Text(
                                                            text = "Mail: ${acc.emailAddress}",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        editingAccountUsername = acc.username
                                                        inputUsername = acc.username
                                                        inputPassword = acc.password
                                                        inputEmail = acc.emailAddress.orEmpty()
                                                        accountFeedbackText = null
                                                    }
                                                ) {
                                                    Text("✏️", fontSize = 16.sp)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (editingAccountUsername == acc.username) {
                                                            editingAccountUsername = null
                                                            inputUsername = ""
                                                            inputPassword = ""
                                                            inputEmail = ""
                                                        }
                                                        securePrefs.removeAccount(acc.username)
                                                        registeredAccounts = securePrefs.getAccounts()
                                                        recentNotifications = stateManager.getAllRecentNotifications(registeredAccounts.map { it.username })
                                                        Toast.makeText(context, "${acc.shortUsername} hesabı silindi.", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Text("🗑️", fontSize = 16.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        // Add / Update Account Form Header
                        if (editingAccountUsername != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Hesabı Düzenle (✏️ ${editingAccountUsername?.substringBefore("@")})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = DeuNavy
                                )
                                TextButton(
                                    onClick = {
                                        editingAccountUsername = null
                                        inputUsername = ""
                                        inputPassword = ""
                                        inputEmail = ""
                                        accountFeedbackText = null
                                    }
                                ) {
                                    Text("İptal Et", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Text(
                                text = if (registeredAccounts.isEmpty()) "Hesap Ekle" else "Yeni Hesap Ekle",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        OutlinedTextField(
                            value = inputUsername,
                            onValueChange = {
                                inputUsername = it
                                pendingBypassUser = null
                                pendingBypassPassword = null
                                pendingBypassEmail = null
                            },
                            label = { Text("Öğrenci Numarası veya E-posta") },
                            placeholder = { Text("Örn: 2024123456 veya 2024123456@ogr.deu.edu.tr") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = inputPassword,
                            onValueChange = {
                                inputPassword = it
                                pendingBypassUser = null
                                pendingBypassPassword = null
                                pendingBypassEmail = null
                            },
                            label = { Text("Şifre") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Text(if (passwordVisible) "Gizle" else "Göster", fontSize = 12.sp)
                                }
                            }
                        )

                        OutlinedTextField(
                            value = inputEmail,
                            onValueChange = {
                                inputEmail = it
                                pendingBypassUser = null
                                pendingBypassPassword = null
                                pendingBypassEmail = null
                            },
                            label = { Text("Öğrenci E-postası (İsteğe Bağlı)") },
                            placeholder = { Text("Örn: ad.soyad@ogr.deu.edu.tr") },
                            supportingText = {
                                Text(
                                    text = "Boş bırakılırsa e-posta takibi yapılmaz. Ayrı şifre gerekmez (şifre aynıdır).",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        fun saveAccountAndComplete(acc: SakaiAccount, feedbackMsg: String, isWarning: Boolean = false) {
                            if (editingAccountUsername != null && !editingAccountUsername.equals(acc.username, ignoreCase = true)) {
                                securePrefs.removeAccount(editingAccountUsername!!)
                            }

                            securePrefs.saveAccount(acc)
                            registeredAccounts = securePrefs.getAccounts()
                            stateManager.checkAccountSwitch(acc.username)

                            editingAccountUsername = null
                            inputUsername = ""
                            inputPassword = ""
                            inputEmail = ""
                            pendingBypassUser = null
                            pendingBypassPassword = null
                            pendingBypassEmail = null

                            accountFeedbackText = feedbackMsg
                            accountFeedbackSuccess = !isWarning
                            isFeedbackWarning = isWarning

                            coroutineScope.launch {
                                // Fetch and populate initial recent notifications for this account
                                val initFetch = apiClient.fetchNotifications(acc.username, acc.password)
                                if (initFetch.isSuccess) {
                                    val list = initFetch.getOrDefault(emptyList())
                                    val tagged = list.map { it.copy(accountUsername = acc.username) }
                                    stateManager.filterNewNotifications(tagged)
                                    stateManager.markProcessed(tagged.take(15))
                                }

                                // If email configured, fetch initial unread emails
                                if (!acc.emailAddress.isNullOrBlank()) {
                                    val initMail = webmailClient.fetchUnreadEmails(acc.emailAddress, acc.password, acc.username)
                                    if (initMail.isSuccess) {
                                        val mailList = initMail.getOrDefault(emptyList())
                                        stateManager.filterNewEmailNotifications(mailList)
                                        stateManager.markProcessed(mailList.take(5))
                                    }
                                }

                                recentNotifications = stateManager.getAllRecentNotifications(securePrefs.getAccounts().map { it.username })
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    val normalizedUser = SakaiUsernameUtils.normalize(inputUsername)
                                    if (normalizedUser.isBlank() || inputPassword.isBlank()) {
                                        Toast.makeText(context, "Lütfen öğrenci no ve şifre girin.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val normalizedEmail = if (inputEmail.isNotBlank()) DeuWebmailClient.normalizeEmail(inputEmail) else null

                                    isAddingAccount = true
                                    accountFeedbackText = null
                                    isFeedbackWarning = false
                                    pendingBypassUser = null
                                    pendingBypassPassword = null
                                    pendingBypassEmail = null

                                    coroutineScope.launch {
                                        when (val res = authClient.authenticate(normalizedUser, inputPassword)) {
                                            is AuthResult.Success -> {
                                                val shortName = SakaiUsernameUtils.shortUsername(normalizedUser)
                                                // If student email is entered, ALSO test Webmail login
                                                if (!normalizedEmail.isNullOrBlank()) {
                                                    when (val webmailRes = webmailClient.testLogin(normalizedEmail, inputPassword)) {
                                                        is WebmailTestResult.Success -> {
                                                            saveAccountAndComplete(
                                                                SakaiAccount(normalizedUser, inputPassword, normalizedEmail),
                                                                "✅ Giriş doğrulandı! $shortName için Sakai ve Öğrenci E-postası bağlandı."
                                                            )
                                                        }
                                                        is WebmailTestResult.ServerUnavailable -> {
                                                            // University mail server is down or unresponsive.
                                                            // Allow saving anyway with an informative warning so Sakai works immediately.
                                                            saveAccountAndComplete(
                                                                SakaiAccount(normalizedUser, inputPassword, normalizedEmail),
                                                                "⚠️ Sakai girişi doğrulandı ve kaydedildi! DEU E-posta sunucusuna şu an ulaşılamıyor (sunucu kapalı veya bakımda olabilir). Sunucu açıldığında mailleriniz arka planda otomatik taranacaktır.",
                                                                isWarning = true
                                                            )
                                                        }
                                                        is WebmailTestResult.InvalidCredentials -> {
                                                            // Rejected credentials on student mail server
                                                            accountFeedbackText = "❌ Sakai girişi başarılı fakat Öğrenci E-postası girişi başarısız: ${webmailRes.message}\nE-posta adresinizi kontrol edebilir veya aşağıdaki seçeneklerle devam edebilirsiniz."
                                                            accountFeedbackSuccess = false
                                                            isFeedbackWarning = false
                                                            pendingBypassUser = normalizedUser
                                                            pendingBypassPassword = inputPassword
                                                            pendingBypassEmail = normalizedEmail
                                                        }
                                                    }
                                                } else {
                                                    saveAccountAndComplete(
                                                        SakaiAccount(normalizedUser, inputPassword, null),
                                                        "✅ Giriş doğrulandı! $shortName Sakai hesabı başarıyla kaydedildi."
                                                    )
                                                }
                                            }
                                            is AuthResult.InvalidCredentials -> {
                                                accountFeedbackText = "❌ Sakai Hatası: Kullanıcı adı ya da şifreniz hatalı."
                                                accountFeedbackSuccess = false
                                                isFeedbackWarning = false
                                            }
                                            is AuthResult.Error -> {
                                                accountFeedbackText = "❌ Sakai Bağlantı hatası: ${res.message}"
                                                accountFeedbackSuccess = false
                                                isFeedbackWarning = false
                                            }
                                        }
                                        isAddingAccount = false
                                    }
                                },
                                enabled = !isAddingAccount
                            ) {
                                if (isAddingAccount) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Doğrulanıyor...")
                                } else {
                                    Text(if (editingAccountUsername != null) "Hesabı Güncelle ve Doğrula" else "Hesabı Doğrula ve Kaydet")
                                }
                            }
                        }

                        accountFeedbackText?.let { text ->
                            val feedbackColor = when {
                                accountFeedbackSuccess -> DeuSuccess
                                isFeedbackWarning -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.error
                            }
                            Text(
                                text = text,
                                color = feedbackColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Bypass action options when Webmail credentials were rejected
                        if (pendingBypassUser != null && pendingBypassPassword != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Nasıl devam etmek istersiniz?",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val pUser = pendingBypassUser!!
                                                val pPass = pendingBypassPassword!!
                                                val shortName = SakaiUsernameUtils.shortUsername(pUser)
                                                saveAccountAndComplete(
                                                    SakaiAccount(pUser, pPass, null),
                                                    "✅ $shortName yalnızca Sakai hesabı olarak kaydedildi."
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text("Yalnızca Sakai Olarak Kaydet", fontSize = 11.sp, maxLines = 1)
                                        }

                                        Button(
                                            onClick = {
                                                val pUser = pendingBypassUser!!
                                                val pPass = pendingBypassPassword!!
                                                val pEmail = pendingBypassEmail
                                                val shortName = SakaiUsernameUtils.shortUsername(pUser)
                                                saveAccountAndComplete(
                                                    SakaiAccount(pUser, pPass, pEmail),
                                                    "⚠️ $shortName kaydedildi. E-posta sunucusu girişi reddettiğinden şifrenizi veya adresinizi daha sonra kontrol ediniz.",
                                                    isWarning = true
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text("Yine de Bu E-posta İle Kaydet", fontSize = 11.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Polling Interval Selector (Min 20 mins enforced)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Kontrol Sıklığı (Aralık)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Üniversite sunucusunu korumak için en düşük sınır 20 dakikadır.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        val intervals = listOf(
                            20 to "20 dk",
                            30 to "30 dk",
                            45 to "45 dk",
                            60 to "1 saat",
                            120 to "2 saat"
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            intervals.forEach { (minutes, label) ->
                                val isSelected = selectedInterval == minutes
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedInterval = minutes
                                        securePrefs.pollIntervalMinutes = minutes
                                        if (isMonitoringActive || isMailMonitoringActive) {
                                            AlarmScheduler.scheduleNext(context, minutes)
                                            WorkScheduler.schedule(context, minutes, forceUpdate = true)
                                        }
                                    },
                                    label = {
                                        Text(label)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Recent Notifications Header, Filters & Actions
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Son Bildirimler",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (displayedNotifications.isNotEmpty())
                                    "${displayedNotifications.size} bildirim listeleniyor"
                                else if (recentNotifications.isNotEmpty())
                                    "Filtreye uygun bildirim yok"
                                else
                                    "Henüz bildirim yok",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (recentNotifications.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showClearDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Temizle 🗑️", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val currentAccs = registeredAccounts
                                    if (currentAccs.isEmpty()) {
                                        Toast.makeText(context, "Lütfen önce en az bir hesap kaydedin.", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }

                                    isManualChecking = true
                                    manualCheckResultText = null
                                    coroutineScope.launch {
                                        try {
                                            val summaries = mutableListOf<String>()
                                            var totalNew = 0
                                            var anySakaiFail = false
                                            var anyMailFail = false

                                            for (acc in currentAccs) {
                                                stateManager.checkAccountSwitch(acc.username)
                                                val parts = mutableListOf<String>()

                                                // 1. Check Sakai Announcements
                                                val fetchRes = apiClient.fetchNotifications(acc.username, acc.password)
                                                if (fetchRes.isSuccess) {
                                                    val allList = fetchRes.getOrDefault(emptyList())
                                                    val tagged = allList.map { it.copy(accountUsername = acc.username) }
                                                    val newOnes = stateManager.filterNewNotifications(tagged)
                                                    if (newOnes.isNotEmpty()) {
                                                        for (notif in newOnes) {
                                                            NotificationHelper.showNotification(context, notif, acc.username)
                                                        }
                                                        stateManager.markProcessed(newOnes)
                                                        totalNew += newOnes.size
                                                        parts.add("${newOnes.size} yeni duyuru")
                                                    } else {
                                                        if (stateManager.getRecentNotifications().isEmpty() && tagged.isNotEmpty()) {
                                                            stateManager.markProcessed(tagged.take(15))
                                                        }
                                                        parts.add("duyurular güncel")
                                                    }
                                                } else {
                                                    anySakaiFail = true
                                                    val err = fetchRes.exceptionOrNull()?.message?.take(30) ?: "Bağlantı hatası"
                                                    parts.add("Sakai: ❌ $err")
                                                }

                                                // 2. Check Student Webmail if configured
                                                if (!acc.emailAddress.isNullOrBlank()) {
                                                    val mailRes = webmailClient.fetchUnreadEmails(
                                                        email = acc.emailAddress,
                                                        pass = acc.password,
                                                        studentUsername = acc.username
                                                    )
                                                    if (mailRes.isSuccess) {
                                                        val mailList = mailRes.getOrDefault(emptyList())
                                                        val newMails = stateManager.filterNewEmailNotifications(mailList)
                                                        if (newMails.isNotEmpty()) {
                                                            NotificationHelper.showEmailNotifications(context, newMails, acc.username)
                                                            stateManager.markProcessed(newMails)
                                                            totalNew += newMails.size
                                                            parts.add("${newMails.size} yeni mail")
                                                        } else {
                                                            parts.add("mail güncel")
                                                        }
                                                    } else {
                                                        anyMailFail = true
                                                        val mErr = mailRes.exceptionOrNull()?.message?.take(30) ?: "sunucuya ulaşılamadı"
                                                        parts.add("Mail: ⚠️ $mErr")
                                                    }
                                                }

                                                summaries.add("${acc.shortUsername}: " + parts.joinToString(", "))
                                            }

                                            recentNotifications = stateManager.getAllRecentNotifications(currentAccs.map { it.username })

                                            if (totalNew > 0) {
                                                manualCheckResultText = "✅ " + summaries.joinToString(" | ")
                                                manualCheckSuccess = true
                                            } else if (!anySakaiFail && !anyMailFail) {
                                                manualCheckResultText = "✅ Tüm hesaplar kontrol edildi (${summaries.joinToString(" | ")})."
                                                manualCheckSuccess = true
                                            } else if (!anySakaiFail && anyMailFail) {
                                                manualCheckResultText = "ℹ️ Sakai güncel (Mail sunucusuna ulaşılamadı): " + summaries.joinToString(" | ")
                                                manualCheckSuccess = true
                                            } else {
                                                manualCheckResultText = "⚠️ Sonuç: " + summaries.joinToString(" | ")
                                                manualCheckSuccess = false
                                            }
                                        } finally {
                                            isManualChecking = false
                                        }
                                    }
                                },
                                enabled = !isManualChecking,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (isManualChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Kontrol ediliyor...", fontSize = 12.sp)
                                } else {
                                    Text("Şimdi Kontrol Et 🔄", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Account & Type Filter Chips
                    if (recentNotifications.isNotEmpty()) {
                        val accountFiltered = remember(recentNotifications, selectedAccountFilter) {
                            if (selectedAccountFilter == null) {
                                recentNotifications
                            } else {
                                recentNotifications.filter {
                                    it.accountUsername?.substringBefore("@").equals(selectedAccountFilter, ignoreCase = true)
                                }
                            }
                        }
                        val sakaiCount = remember(accountFiltered) { accountFiltered.count { !it.isEmail } }
                        val mailCount = remember(accountFiltered) { accountFiltered.count { it.isEmail } }

                        // 1. Account Filter Chips (if multiple accounts registered)
                        if (registeredAccounts.size > 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Hesap Filtresi:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedAccountFilter == null,
                                        onClick = { selectedAccountFilter = null },
                                        label = { Text("Tüm Hesaplar (${recentNotifications.size})", fontSize = 12.sp) }
                                    )
                                    registeredAccounts.forEach { acc ->
                                        val count = recentNotifications.count {
                                            it.accountUsername?.substringBefore("@").equals(acc.shortUsername, ignoreCase = true)
                                        }
                                        FilterChip(
                                            selected = selectedAccountFilter == acc.shortUsername,
                                            onClick = {
                                                selectedAccountFilter = if (selectedAccountFilter == acc.shortUsername) null else acc.shortUsername
                                            },
                                            label = { Text("🎓 ${acc.shortUsername} ($count)", fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Notification Type Filter Chips (Tümü / 🎓 Sakai / ✉️ E-posta)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (registeredAccounts.size > 1) {
                                Text(
                                    text = "Tür Filtresi:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = selectedTypeFilter == NotificationTypeFilter.ALL,
                                    onClick = { selectedTypeFilter = NotificationTypeFilter.ALL },
                                    label = { Text("Tümü (${accountFiltered.size})", fontSize = 12.sp) }
                                )
                                FilterChip(
                                    selected = selectedTypeFilter == NotificationTypeFilter.SAKAI,
                                    onClick = {
                                        selectedTypeFilter = if (selectedTypeFilter == NotificationTypeFilter.SAKAI) {
                                            NotificationTypeFilter.ALL
                                        } else {
                                            NotificationTypeFilter.SAKAI
                                        }
                                    },
                                    label = { Text("🎓 Sakai Duyuruları ($sakaiCount)", fontSize = 12.sp) }
                                )
                                FilterChip(
                                    selected = selectedTypeFilter == NotificationTypeFilter.MAIL,
                                    onClick = {
                                        selectedTypeFilter = if (selectedTypeFilter == NotificationTypeFilter.MAIL) {
                                            NotificationTypeFilter.ALL
                                        } else {
                                            NotificationTypeFilter.MAIL
                                        }
                                    },
                                    label = { Text("✉️ Öğrenci Mailleri ($mailCount)", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // Banner displaying manual check result
            manualCheckResultText?.let { bannerText ->
                item {
                    Surface(
                        color = if (manualCheckSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (manualCheckSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bannerText,
                            color = if (manualCheckSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // 5. Recent Notifications List
            if (displayedNotifications.isEmpty()) {
                item {
                    val emptyMsg = when {
                        recentNotifications.isEmpty() ->
                            "Henüz yakalanan bildirim yok. Sistem arka planda yeni duyuruları ve mailleri bekliyor."
                        selectedTypeFilter == NotificationTypeFilter.MAIL ->
                            "Seçilen kriterlere uygun öğrenci e-postası bulunmuyor."
                        selectedTypeFilter == NotificationTypeFilter.SAKAI ->
                            "Seçilen kriterlere uygun Sakai duyurusu bulunmuyor."
                        else ->
                            "Bu filtre için gösterilecek bildirim bulunmuyor."
                    }
                    Text(
                        text = emptyMsg,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(displayedNotifications) { notif ->
                    NotificationItemCard(
                        notif = notif,
                        registeredAccounts = registeredAccounts
                    ) { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                }
            }

            // 6. App Info & Manual Update Checker Footer
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DEU Sakai Notifier",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sürüm: v$currentAppVersion",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Ayarlar açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Ayarlar ⚙️", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (!isCheckingUpdateManual) {
                                        isCheckingUpdateManual = true
                                        coroutineScope.launch {
                                            try {
                                                val update = UpdateChecker.checkForUpdates(currentAppVersion)
                                                if (update != null && update.hasUpdate) {
                                                    appUpdateInfo = update
                                                    updateBannerDismissed = false
                                                    Toast.makeText(
                                                        context,
                                                        "🚀 Yeni sürüm mevcut: ${update.latestVersion}!",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else if (update != null) {
                                                    Toast.makeText(
                                                        context,
                                                        "✅ Uygulamanız en güncel sürümde (v$currentAppVersion).",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "⚠️ Güncelleme sunucusuna ulaşılamadı. İnternet bağlantınızı kontrol edin.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "Hata: ${e.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } finally {
                                                isCheckingUpdateManual = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isCheckingUpdateManual,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (isCheckingUpdateManual) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Denetleniyor...", fontSize = 12.sp)
                                } else {
                                    Text("Güncellemeleri Denetle 🔄", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Bildirim Geçmişini Temizle") },
            text = { Text("Listelenen tüm bildirim geçmişi silinecektir. Yeni gelecek duyurular bundan etkilenmez. Onaylıyor musunuz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        stateManager.clearAllRecentNotifications(registeredAccounts.map { it.username })
                        recentNotifications = emptyList()
                        showClearDialog = false
                        Toast.makeText(context, "Bildirim geçmişi temizlendi.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Evet, Temizle", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

private data class AccountBadgeTheme(
    val badgeBg: Color,
    val badgeText: Color,
    val cardBorder: Color
)

@Composable
fun NotificationItemCard(
    notif: SakaiNotification,
    registeredAccounts: List<SakaiAccount>,
    onOpenUrl: (String) -> Unit
) {
    val rawUsername = notif.accountUsername.orEmpty()
    val shortUser = if (rawUsername.isNotBlank()) {
        rawUsername.substringBefore("@").trim()
    } else {
        registeredAccounts.firstOrNull()?.shortUsername ?: "Öğrenci"
    }

    val accountIndex = registeredAccounts.indexOfFirst {
        it.shortUsername.equals(shortUser, ignoreCase = true) ||
        it.username.equals(rawUsername, ignoreCase = true)
    }.let { if (it >= 0) it else 0 }

    val themes = listOf(
        // Account 1: Blue / Navy theme
        AccountBadgeTheme(
            badgeBg = Color(0xFFDBEAFE),
            badgeText = Color(0xFF1E40AF),
            cardBorder = Color(0xFF3B82F6)
        ),
        // Account 2: Purple / Violet theme
        AccountBadgeTheme(
            badgeBg = Color(0xFFF3E8FF),
            badgeText = Color(0xFF6B21A8),
            cardBorder = Color(0xFFA855F7)
        ),
        // Account 3: Teal / Emerald theme
        AccountBadgeTheme(
            badgeBg = Color(0xFFD1FAE5),
            badgeText = Color(0xFF065F46),
            cardBorder = Color(0xFF10B981)
        ),
        // Account 4: Amber / Warm theme
        AccountBadgeTheme(
            badgeBg = Color(0xFFFEF3C7),
            badgeText = Color(0xFF92400E),
            cardBorder = Color(0xFFF59E0B)
        )
    )

    val currentTheme = themes[accountIndex % themes.size]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.2.dp, currentTheme.cardBorder.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Prominent Account Badge, Mail Badge & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = currentTheme.badgeBg,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, currentTheme.cardBorder.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("🎓", fontSize = 12.sp)
                            Text(
                                text = "Öğrenci: $shortUser",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentTheme.badgeText
                            )
                        }
                    }

                    if (notif.isEmail) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("✉️", fontSize = 11.sp)
                                Text(
                                    text = "Öğrenci Maili",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }
                }

                notif.publishedAt?.let { dateStr ->
                    Text(
                        text = "📅 ${SakaiNotification.formatPublishedDate(dateStr) ?: dateStr}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // Subtitle: Sender (for email) or Course Name (for Sakai)
            if (notif.isEmail) {
                notif.siteTitle?.let { senderName ->
                    Surface(
                        color = Color(0xFFE0F2FE),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "👤 Gönderen: $senderName",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            } else {
                notif.siteTitle?.let { site ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "📚 $site",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Title
            Text(
                text = notif.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Body
            notif.body?.let { bodyText ->
                Text(
                    text = bodyText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    maxLines = 4
                )
            }

            // Link Action
            notif.url?.let { link ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = if (notif.isEmail) "Webmail'e Git ↗" else "Duyuruyu Aç ↗",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onOpenUrl(link) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

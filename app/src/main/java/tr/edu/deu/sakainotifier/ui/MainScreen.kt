package tr.edu.deu.sakainotifier.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import tr.edu.deu.sakainotifier.data.api.SakaiApiClient
import tr.edu.deu.sakainotifier.data.api.SakaiAuthClient
import tr.edu.deu.sakainotifier.data.api.SakaiUsernameUtils
import tr.edu.deu.sakainotifier.data.model.SakaiAccount
import tr.edu.deu.sakainotifier.data.model.SakaiNotification
import tr.edu.deu.sakainotifier.data.storage.NotificationStateManager
import tr.edu.deu.sakainotifier.data.storage.SecurePreferencesManager
import tr.edu.deu.sakainotifier.notification.NotificationHelper
import tr.edu.deu.sakainotifier.ui.theme.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import tr.edu.deu.sakainotifier.worker.WorkScheduler

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

    var registeredAccounts by remember { mutableStateOf(securePrefs.getAccounts()) }
    var inputUsername by remember { mutableStateOf("") }
    var inputPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isMonitoringActive by remember { mutableStateOf(securePrefs.isMonitoringActive) }
    var selectedInterval by remember { mutableStateOf(securePrefs.pollIntervalMinutes) }

    var isAddingAccount by remember { mutableStateOf(false) }
    var accountFeedbackText by remember { mutableStateOf<String?>(null) }
    var accountFeedbackSuccess by remember { mutableStateOf(false) }

    var isManualChecking by remember { mutableStateOf(false) }
    var manualCheckResultText by remember { mutableStateOf<String?>(null) }
    var manualCheckSuccess by remember { mutableStateOf(false) }
    var recentNotifications by remember {
        mutableStateOf(stateManager.getAllRecentNotifications(securePrefs.getAccounts().map { it.username }))
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
                            text = "Dokuz Eylül Üniversitesi LMS",
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
            // 1. Service Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMonitoringActive) Color(0xFFE8F5E9) else Color(0xFFECEFF1)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMonitoringActive) "Takip Aktif (Çalışıyor)" else "Takip Durduruldu",
                                fontWeight = FontWeight.Bold,
                                color = if (isMonitoringActive) DeuSuccess else Color.DarkGray,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isMonitoringActive)
                                    "Kayıtlı tüm hesaplar her $selectedInterval dakikada bir kontrol ediliyor."
                                else
                                    "Yeni duyurular için takibi başlatın.",
                                fontSize = 13.sp,
                                color = Color.Gray
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
                                if (active) {
                                    WorkScheduler.schedule(context, selectedInterval, forceUpdate = true)
                                    Toast.makeText(context, "Sakai takibi başlatıldı.", Toast.LENGTH_SHORT).show()
                                } else {
                                    WorkScheduler.cancel(context)
                                    Toast.makeText(context, "Sakai takibi durduruldu.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
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
                                                    Text(
                                                        text = acc.shortUsername,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = acc.username,
                                                        fontSize = 11.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
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
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        // Add / Update Account Form
                        Text(
                            text = if (registeredAccounts.isEmpty()) "Hesap Ekle" else "Yeni Hesap Ekle / Güncelle",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        OutlinedTextField(
                            value = inputUsername,
                            onValueChange = { inputUsername = it },
                            label = { Text("Öğrenci Numarası veya E-posta") },
                            placeholder = { Text("Örn: 2024123456 veya 2024123456@ogr.deu.edu.tr") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = inputPassword,
                            onValueChange = { inputPassword = it },
                            label = { Text("Sakai Şifresi") },
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

                                    isAddingAccount = true
                                    accountFeedbackText = null
                                    coroutineScope.launch {
                                        when (val res = authClient.authenticate(normalizedUser, inputPassword)) {
                                            is AuthResult.Success -> {
                                                val acc = SakaiAccount(normalizedUser, inputPassword)
                                                securePrefs.saveAccount(acc)
                                                registeredAccounts = securePrefs.getAccounts()
                                                stateManager.checkAccountSwitch(normalizedUser)
                                                inputUsername = ""
                                                inputPassword = ""
                                                accountFeedbackText = "✅ Giriş doğrulandı! ${acc.shortUsername} hesabı başarıyla kaydedildi."
                                                accountFeedbackSuccess = true

                                                // Fetch and populate initial recent notifications for this account
                                                val initFetch = apiClient.fetchNotifications(normalizedUser, inputPassword)
                                                if (initFetch.isSuccess) {
                                                    val list = initFetch.getOrDefault(emptyList())
                                                    val tagged = list.map { it.copy(accountUsername = normalizedUser) }
                                                    stateManager.filterNewNotifications(tagged)
                                                    stateManager.markProcessed(tagged.take(15))
                                                }

                                                recentNotifications = stateManager.getAllRecentNotifications(registeredAccounts.map { it.username })
                                            }
                                            is AuthResult.InvalidCredentials -> {
                                                accountFeedbackText = "❌ Hata: Kullanıcı adı ya da şifreniz hatalı."
                                                accountFeedbackSuccess = false
                                            }
                                            is AuthResult.Error -> {
                                                accountFeedbackText = "❌ Bağlantı hatası: ${res.message}"
                                                accountFeedbackSuccess = false
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
                                    Text("Hesabı Doğrula ve Kaydet")
                                }
                            }
                        }

                        accountFeedbackText?.let { text ->
                            Text(
                                text = text,
                                color = if (accountFeedbackSuccess) DeuSuccess else MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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
                                        if (isMonitoringActive) {
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

            // 4. Recent Notifications Header & Manual Check
            item {
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
                            text = if (recentNotifications.isNotEmpty()) "${recentNotifications.size} bildirim listeleniyor" else "Henüz bildirim yok",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
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
                                    var anyFail = false

                                    for (acc in currentAccs) {
                                        stateManager.checkAccountSwitch(acc.username)
                                        val fetchRes = apiClient.fetchNotifications(acc.username, acc.password)
                                        if (fetchRes.isSuccess) {
                                            val allList = fetchRes.getOrDefault(emptyList())
                                            val tagged = allList.map { it.copy(accountUsername = acc.username) }
                                            val wasFirstBoot = stateManager.isFirstBoot
                                            val newOnes = stateManager.filterNewNotifications(tagged)
                                            if (newOnes.isNotEmpty()) {
                                                for (notif in newOnes) {
                                                    NotificationHelper.showNotification(context, notif, acc.username)
                                                }
                                                stateManager.markProcessed(newOnes)
                                                totalNew += newOnes.size
                                                summaries.add("${acc.shortUsername}: ${newOnes.size} yeni duyuru")
                                            } else {
                                                if (stateManager.getRecentNotifications().isEmpty() && tagged.isNotEmpty()) {
                                                    stateManager.markProcessed(tagged.take(15))
                                                }
                                                summaries.add("${acc.shortUsername}: güncel")
                                            }
                                        } else {
                                            anyFail = true
                                            val err = fetchRes.exceptionOrNull()?.message?.take(50) ?: "Bağlantı hatası"
                                            summaries.add("${acc.shortUsername}: ❌ $err")
                                        }
                                    }

                                    recentNotifications = stateManager.getAllRecentNotifications(currentAccs.map { it.username })

                                    if (totalNew > 0) {
                                        manualCheckResultText = "✅ " + summaries.joinToString(" | ")
                                        manualCheckSuccess = true
                                    } else if (!anyFail) {
                                        manualCheckResultText = "✅ Tüm hesaplar kontrol edildi. Yeni duyuru yok (${summaries.joinToString(", ")})."
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
                        enabled = !isManualChecking
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
            if (recentNotifications.isEmpty()) {
                item {
                    Text(
                        text = "Henüz yakalanan bildirim yok. Sistem arka planda yeni duyuruları bekliyor.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(recentNotifications) { notif ->
                    NotificationItemCard(
                        notif = notif,
                        registeredAccounts = registeredAccounts
                    ) { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                }
            }
        }
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
            // Header Row: Prominent Account Badge & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

                notif.publishedAt?.let { dateStr ->
                    Text(
                        text = "📅 ${SakaiNotification.formatPublishedDate(dateStr) ?: dateStr}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // Course Name
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
                        text = "Duyuruyu Aç ↗",
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

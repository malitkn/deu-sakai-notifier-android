package tr.edu.deu.sakainotifier.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import tr.edu.deu.sakainotifier.data.model.SakaiNotification
import tr.edu.deu.sakainotifier.data.storage.NotificationStateManager
import tr.edu.deu.sakainotifier.data.storage.SecurePreferencesManager
import tr.edu.deu.sakainotifier.notification.NotificationHelper
import tr.edu.deu.sakainotifier.ui.theme.DeuNavy
import tr.edu.deu.sakainotifier.ui.theme.DeuSuccess
import tr.edu.deu.sakainotifier.worker.WorkScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    securePrefs: SecurePreferencesManager,
    stateManager: NotificationStateManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authClient = remember { SakaiAuthClient() }
    val apiClient = remember { SakaiApiClient(authClient) }

    var username by remember { mutableStateOf(securePrefs.username) }
    var password by remember { mutableStateOf(securePrefs.password) }
    var passwordVisible by remember { mutableStateOf(false) }

    var isMonitoringActive by remember { mutableStateOf(securePrefs.isMonitoringActive) }
    var selectedInterval by remember { mutableStateOf(securePrefs.pollIntervalMinutes) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testResultSuccess by remember { mutableStateOf(false) }

    var isManualChecking by remember { mutableStateOf(false) }
    var recentNotifications by remember { mutableStateOf(stateManager.getRecentNotifications()) }

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
                                    "Arka planda her $selectedInterval dakikada bir kontrol ediliyor."
                                else
                                    "Yeni duyurular için takibi başlatın.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = isMonitoringActive,
                            onCheckedChange = { active ->
                                if (active && !securePrefs.hasCredentials()) {
                                    Toast.makeText(context, "Lütfen önce giriş bilgilerinizi kaydedin.", Toast.LENGTH_SHORT).show()
                                    return@Switch
                                }
                                isMonitoringActive = active
                                securePrefs.isMonitoringActive = active
                                if (active) {
                                    WorkScheduler.schedule(context, selectedInterval)
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

            // 2. Credentials Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Öğrenci Giriş Bilgileri",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Bilgileriniz sadece cihazınızda şifrelenerek saklanır, harici sunucuya gitmez.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                val normalized = SakaiUsernameUtils.normalize(it)
                                securePrefs.username = normalized
                            },
                            label = { Text("Öğrenci Numarası veya E-posta") },
                            placeholder = { Text("Örn: 2024123456 veya 2024123456@ogr.deu.edu.tr") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                securePrefs.password = it
                            },
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
                                    val normalizedUser = SakaiUsernameUtils.normalize(username)
                                    if (normalizedUser.isBlank() || password.isBlank()) {
                                        Toast.makeText(context, "Lütfen öğrenci no ve şifre girin.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (username != normalizedUser) {
                                        username = normalizedUser
                                    }
                                    securePrefs.username = normalizedUser
                                    securePrefs.password = password

                                    val switched = stateManager.checkAccountSwitch(normalizedUser)
                                    if (switched) {
                                        apiClient.invalidateSession()
                                        recentNotifications = emptyList()
                                    }

                                    isTestingConnection = true
                                    testResultText = null
                                    coroutineScope.launch {
                                        when (val res = authClient.authenticate(normalizedUser, password)) {
                                            is AuthResult.Success -> {
                                                testResultText = "✅ Giriş başarılı! Sakai oturumu doğrulandı ($normalizedUser)."
                                                testResultSuccess = true
                                            }
                                            is AuthResult.InvalidCredentials -> {
                                                testResultText = "❌ Hata: Kullanıcı adı yada şifreniz hatalı."
                                                testResultSuccess = false
                                            }
                                            is AuthResult.Error -> {
                                                testResultText = "❌ Bağlantı hatası: ${res.message}"
                                                testResultSuccess = false
                                            }
                                        }
                                        isTestingConnection = false
                                    }
                                },
                                enabled = !isTestingConnection
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Kontrol ediliyor...")
                                } else {
                                    Text("Girişi Test Et")
                                }
                            }
                        }

                        testResultText?.let { text ->
                            Text(
                                text = text,
                                color = if (testResultSuccess) DeuSuccess else MaterialTheme.colorScheme.error,
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

                        val intervals = listOf(20, 30, 45, 60, 120)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            intervals.forEach { minutes ->
                                val isSelected = selectedInterval == minutes
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedInterval = minutes
                                        securePrefs.pollIntervalMinutes = minutes
                                        if (isMonitoringActive) {
                                            WorkScheduler.schedule(context, minutes)
                                        }
                                    },
                                    label = {
                                        Text(if (minutes >= 60) "${minutes / 60} sa" else "$minutes dk")
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Recent Notifications Header
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
                            val normalizedUser = SakaiUsernameUtils.normalize(username)
                            if (normalizedUser.isBlank() || password.isBlank()) {
                                Toast.makeText(context, "Lütfen önce giriş bilgilerinizi girin.", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            if (username != normalizedUser) {
                                username = normalizedUser
                                securePrefs.username = normalizedUser
                            }
                            val switched = stateManager.checkAccountSwitch(normalizedUser)
                            if (switched) {
                                apiClient.invalidateSession()
                                recentNotifications = emptyList()
                            }

                            isManualChecking = true
                            coroutineScope.launch {
                                try {
                                    val fetchRes = apiClient.fetchNotifications(normalizedUser, password)
                                    if (fetchRes.isSuccess) {
                                        val allList = fetchRes.getOrDefault(emptyList())
                                        val newOnes = stateManager.filterNewNotifications(allList)
                                        if (newOnes.isNotEmpty()) {
                                            for (notif in newOnes) {
                                                NotificationHelper.showNotification(context, notif)
                                            }
                                            stateManager.markProcessed(newOnes)
                                            Toast.makeText(context, "${newOnes.size} yeni duyuru bulundu!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Yeni duyuru yok. Tüm duyurular güncel.", Toast.LENGTH_SHORT).show()
                                        }
                                        recentNotifications = stateManager.getRecentNotifications()
                                    } else {
                                        Toast.makeText(context, "Sakai bağlantı hatası: ${fetchRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
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
                    NotificationItemCard(notif = notif) { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItemCard(
    notif: SakaiNotification,
    onOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            notif.siteTitle?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeuNavy
                )
            }
            Text(
                text = notif.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            notif.body?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    maxLines = 3
                )
            }
            notif.publishedAt?.let {
                Text(
                    text = "Tarih: $it",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
            notif.url?.let { link ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Duyuruyu Tarayıcıda Aç ↗",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onOpenUrl(link) }
                )
            }
        }
    }
}

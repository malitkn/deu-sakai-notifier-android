package tr.edu.deu.sakainotifier.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tr.edu.deu.sakainotifier.data.api.DebisClient
import tr.edu.deu.sakainotifier.data.model.DebisCaptchaSession

/**
 * Native Jetpack Compose Dialog that displays the DEBİS CAPTCHA image directly to the student.
 * Guarantees that the human user visually resolves and types the challenge, preserving 100%
 * security boundary compliance and session cookie continuity.
 */
@Composable
fun DebisLoginDialog(
    initialStudentNumber: String = "",
    initialPassword: String = "",
    debisClient: DebisClient = remember { DebisClient() },
    onDismiss: () -> Unit,
    onSuccess: (sessionCookie: String, studentNo: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var studentNo by remember { mutableStateOf(initialStudentNumber.substringBefore("@").trim()) }
    var password by remember { mutableStateOf(initialPassword) }
    var captchaInput by remember { mutableStateOf("") }

    var captchaSession by remember { mutableStateOf<DebisCaptchaSession?>(null) }
    var isLoadingCaptcha by remember { mutableStateOf(true) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadCaptcha() {
        isLoadingCaptcha = true
        errorMessage = null
        captchaInput = ""
        coroutineScope.launch {
            val result = debisClient.fetchCaptcha()
            if (result.isSuccess) {
                captchaSession = result.getOrNull()
            } else {
                errorMessage = "Güvenlik kodu yüklenemedi: ${result.exceptionOrNull()?.message}"
            }
            isLoadingCaptcha = false
        }
    }

    LaunchedEffect(Unit) {
        loadCaptcha()
    }

    AlertDialog(
        onDismissRequest = { if (!isLoggingIn) onDismiss() },
        title = {
            Column {
                Text(
                    text = "DEBİS Sınav Notları Takibi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Yeni vize, final ve harf notlarını anlık almak için giriş yapın.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = studentNo,
                    onValueChange = { studentNo = it.trim() },
                    label = { Text("Öğrenci Numarası") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("DEBİS Şifresi") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                // CAPTCHA Image Container & Human Input
                Text(
                    text = "Güvenlik Doğrulaması",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingCaptcha) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else if (captchaSession != null) {
                            val bitmap = remember(captchaSession) {
                                val bytes = captchaSession!!.captchaImageBytes
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Güvenlik Kodu Görseli",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("Görsel çözülemedi", fontSize = 12.sp)
                            }
                        } else {
                            Text("Görsel yok", fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = { loadCaptcha() },
                        enabled = !isLoadingCaptcha && !isLoggingIn
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Güvenlik Kodunu Yenile"
                        )
                    }
                }

                OutlinedTextField(
                    value = captchaInput,
                    onValueChange = { captchaInput = it.trim() },
                    label = { Text("Resimdeki Kodu Giriniz") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val currentSession = captchaSession
                    if (currentSession == null) {
                        errorMessage = "Lütfen güvenlik kodunun yüklenmesini bekleyin."
                        return@Button
                    }
                    if (studentNo.isBlank() || password.isBlank() || captchaInput.isBlank()) {
                        errorMessage = "Lütfen tüm alanları doldurun."
                        return@Button
                    }

                    isLoggingIn = true
                    errorMessage = null

                    coroutineScope.launch {
                        val loginResult = debisClient.login(
                            username = studentNo,
                            password = password,
                            captchaCode = captchaInput,
                            sessionCookie = currentSession.sessionId
                        )

                        if (loginResult.isSuccess) {
                            val activeCookie = loginResult.getOrThrow()
                            onSuccess(activeCookie, studentNo)
                        } else {
                            errorMessage = loginResult.exceptionOrNull()?.message ?: "Giriş başarısız oldu."
                            // Refresh captcha on failure
                            loadCaptcha()
                        }
                        isLoggingIn = false
                    }
                },
                enabled = !isLoggingIn && !isLoadingCaptcha
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Giriş Yap & Notları Bağla")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoggingIn
            ) {
                Text("Vazgeç")
            }
        }
    )
}

@file:OptIn(ExperimentalMaterial3Api::class)
package com.app.fluenscene

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.android.billingclient.api.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// --------------------------------------------------------------------
// UI COMPONENTS (FONTS, BRANDING & GLASS ENGINE)
// --------------------------------------------------------------------
val Cormorant = FontFamily(Font(R.font.cormorant))
val Cinzel = FontFamily(Font(R.font.cinzelb))
val Jakarta = FontFamily(Font(R.font.jakartan))
val Comfortaa = FontFamily(Font(R.font.comfortaa))
val Agrandir = FontFamily(Font(R.font.agrandirwl))


val FluenSceneGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF42F1B8), Color(0xFF2ADF8E), Color(0xFF00C93A)),
    start = Offset(0f, 0f),
    end = Offset(1000f, 1000f)
)
val FluenSceneGreenSolid = Color(0xFF2ADF8E)

enum class GlassShapeType { SPHERE, CUBE, CAPSULE }
data class GlassParticle(
    val type: GlassShapeType,
    val size: Float,
    val centerX: Float,
    val centerY: Float,
    val orbitPhaseX: Float,
    val orbitPhaseY: Float,
    val speedX: Float,
    val speedY: Float,
    val orbitRadiusX: Float,
    val orbitRadiusY: Float,
    val rotationSpeed: Float
)

// GLOBAL CONFIG & STATE
var isVideoPlayingGlobally = false
val auth by lazy { FirebaseAuth.getInstance() }
val db by lazy { FirebaseFirestore.getInstance() }

// SECURE FINGERPRINT
fun getUserIdFingerprint(): String {
    val email = auth.currentUser?.email ?: auth.currentUser?.uid ?: return ""
    val bytes = MessageDigest.getInstance("SHA-256").digest(email.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        var intentUri: Uri? = null
        if (intent?.action == Intent.ACTION_VIEW) {
            intentUri = intent.data
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0F0D))) {
                    FluenSceneApp(intentUri)
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isVideoPlayingGlobally && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

data class LocalVideo(val uri: Uri, val name: String, val duration: String, val size: String, val folder: String, val id: Long)

val globalRegionsList = listOf(
    "Andhra Pradesh, India", "Arunachal Pradesh, India", "Assam, India", "Bihar, India",
    "Chhattisgarh, India", "Goa, India", "Gujarat, India", "Haryana, India",
    "Himachal Pradesh, India", "Jharkhand, India", "Karnataka, India", "Kerala, India",
    "Madhya Pradesh, India", "Maharashtra, India", "Manipur, India", "Meghalaya, India",
    "Mizoram, India", "Nagaland, India", "Odisha, India", "Punjab, India",
    "Rajasthan, India", "Sikkim, India", "Tamil Nadu, India", "Telangana, India",
    "Tripura, India", "Uttar Pradesh, India", "Uttarakhand, India", "West Bengal, India",
    "Delhi, India", "California, USA", "New York, USA", "London, UK", "Global / Neutral"
)

val dynamicVoicePrompts = listOf(
    "Describe a challenge you recently faced and how you overcame it.",
    "If you could travel anywhere in the world right now, where would it be and why?",
    "Explain a hobby or topic you are deeply passionate about.",
    "What is your favorite movie or book, and why does it resonate with you?",
    "How do you think artificial intelligence will change daily life in the next ten years?"
)

@Composable
fun AmbientGreenGlow() {
    val infiniteTransition = rememberInfiniteTransition(label = "ambientGlow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.03f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )
    Box(modifier = Modifier.fillMaxSize().background(
        Brush.radialGradient(
            colors = listOf(FluenSceneGreenSolid.copy(alpha = alpha), Color.Transparent),
            radius = 1200f
        )
    ))
}

@Composable
fun FluenSceneApp(intentUri: Uri? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember {
        mutableStateOf(if (auth.currentUser == null) "login" else "check_setup")
    }

    var activePlaylist by remember { mutableStateOf<List<Uri>?>(null) }
    var activeStartIndex by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var pendingIntentUri by remember { mutableStateOf(intentUri) }

    BackHandler(enabled = activePlaylist != null || selectedFolder != null) {
        if (activePlaylist != null) {
            activePlaylist = null
        } else if (selectedFolder != null) {
            selectedFolder = null
        }
    }

    when (currentScreen) {
        "login" -> GoogleSignInScreen(onSignInSuccess = { currentScreen = "check_setup" })
        "check_setup" -> {
            LaunchedEffect(Unit) {
                val userId = getUserIdFingerprint()
                if (userId.isEmpty()) {
                    currentScreen = "login"
                    return@LaunchedEffect
                }
                try {
                    val doc = db.collection("users").document(userId).get().await()
                    if (doc.exists()) {
                        val status = doc.getString("accountStatus")
                        if (status == "deleted") {
                            currentScreen = "setup_region"
                        } else {
                            currentScreen = "home"
                        }
                    } else {
                        currentScreen = "setup_region"
                    }
                } catch (e: Exception) {
                    currentScreen = "home"
                }
            }

            val infiniteTransition = rememberInfiniteTransition(label = "infiniteGlow")
            val glowRadius by infiniteTransition.animateFloat(
                initialValue = 200f, targetValue = 400f,
                animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = "glowRadius"
            )
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF030305)), contentAlignment = Alignment.Center) {
                AmbientGreenGlow()
                Box(modifier = Modifier.size(300.dp).background(
                    brush = Brush.radialGradient(colors = listOf(FluenSceneGreenSolid.copy(alpha = 0.2f), Color.Transparent), radius = glowRadius),
                    shape = CircleShape
                ))
                CircularProgressIndicator(color = FluenSceneGreenSolid)
            }
        }
        "setup_region" -> SetupRegionScreen(onRegionSaved = { currentScreen = "quiz" })
        "quiz" -> AIQuizScreen(onQuizFinished = { calculatedFluency ->
            val userId = getUserIdFingerprint()
            coroutineScope.launch {
                try {
                    val docRef = db.collection("users").document(userId)
                    val snapshot = docRef.get().await()

                    if (snapshot.exists() && snapshot.contains("apiCallsUsed")) {
                        docRef.update(
                            mapOf(
                                "email" to auth.currentUser?.email,
                                "fluencyLevel" to calculatedFluency,
                                "accountStatus" to "active"
                            )
                        ).await()
                    } else {
                        val userData = mapOf(
                            "email" to auth.currentUser?.email,
                            "fluencyLevel" to calculatedFluency,
                            "apiCallsUsed" to 0,
                            "isPremium" to false,
                            "totalTranslationsRequested" to 0,
                            "accountStatus" to "active"
                        )
                        docRef.set(userData, SetOptions.merge()).await()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                currentScreen = "home"
            }
        })
        "settings" -> SettingsScreen(onNavigateBack = { currentScreen = "home" })
        "home" -> {
            LaunchedEffect(pendingIntentUri) {
                if (pendingIntentUri != null) {
                    activePlaylist = listOf(pendingIntentUri!!)
                    activeStartIndex = 0
                    pendingIntentUri = null
                }
            }

            val videoPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    activePlaylist = listOf(uri)
                    activeStartIndex = 0
                }
            }

            if (activePlaylist == null) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF04140C))) {
                    LocalVideoLibrary(
                        selectedFolder = selectedFolder,
                        onFolderSelected = { selectedFolder = it },
                        onVideoSelected = { playlist, index ->
                            activePlaylist = playlist
                            activeStartIndex = index
                        },
                        onSettingsClicked = { currentScreen = "settings" }
                    )

                    Button(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .systemBarsPadding()
                            .background(FluenSceneGradient, RoundedCornerShape(32.dp))
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("Browse Files Manually", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = Jakarta)
                        }
                    }
                }
            } else {
                VideoPlayerScreen(
                    playlist = activePlaylist!!,
                    startIndex = activeStartIndex,
                    onDismissPlayer = { activePlaylist = null },
                    userId = getUserIdFingerprint()
                )
            }
        }
    }
}

@Composable
fun GoogleSignInScreen(onSignInSuccess: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(Exception::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { onSignInSuccess() }
        } catch (e: Exception) { e.printStackTrace() }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "LoginTransition")
    val lineAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "LineAlpha"
    )
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(animation = tween(100000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "Time"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.05f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "GlowPulse"
    )

    // Pre-calculate stars to maintain their offsets
    val stars = remember {
        List(50) {
            floatArrayOf(
                Random.nextFloat(), // x ratio
                Random.nextFloat(), // start y ratio
                Random.nextFloat() * 3f + 1f, // radius
                Random.nextFloat() * 0.5f + 0.2f, // base alpha
                Random.nextFloat() * 0.5f + 0.1f, // speed Y
                Random.nextFloat() * (2 * Math.PI).toFloat() // phase for pulse
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF060D0A))) {

        // Background Curves and Stars
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top Right Waves
            val topPath1 = Path().apply {
                moveTo(w * 0.4f, 0f)
                quadraticBezierTo(w * 0.8f, h * 0.15f, w, h * 0.25f)
            }
            val topPath2 = Path().apply {
                moveTo(w * 0.6f, 0f)
                quadraticBezierTo(w * 0.9f, h * 0.1f, w, h * 0.15f)
            }
            drawPath(topPath1, Brush.linearGradient(listOf(Color.Transparent, FluenSceneGreenSolid.copy(alpha = lineAlpha))), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            drawPath(topPath2, Brush.linearGradient(listOf(Color.Transparent, FluenSceneGreenSolid.copy(alpha = lineAlpha * 0.6f))), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))

            // Bottom Left Waves
            val bottomPath1 = Path().apply {
                moveTo(0f, h * 0.8f)
                quadraticBezierTo(w * 0.2f, h * 0.9f, w * 0.4f, h)
            }
            drawPath(bottomPath1, Brush.linearGradient(listOf(FluenSceneGreenSolid.copy(alpha = lineAlpha), Color.Transparent)), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            // Moving Stars
            stars.forEach { star ->
                val startX = star[0] * w
                val startY = star[1] * h
                val r = star[2]
                val baseAlpha = star[3]
                val speedY = star[4]
                val phase = star[5]

                // Slowly move upwards
                val currentY = (startY - (time * speedY * 15f)) % h
                val actualY = if (currentY < 0) currentY + h else currentY

                // Breathing alpha effect using explicit kotlin.math.sin to fix Float/Double mismatch
                val alphaPulse = (sin(time * 0.05f + phase) * 0.4f + 0.6f)
                val finalAlpha = (baseAlpha * alphaPulse).coerceIn(0.1f, 1f)

                drawCircle(color = FluenSceneGreenSolid.copy(alpha = finalAlpha), radius = r, center = Offset(startX, actualY))
            }
        }

        // Glowing center background
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(500.dp)
                .background(Brush.radialGradient(listOf(FluenSceneGreenSolid.copy(alpha = glowPulse), Color.Transparent)), CircleShape)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {

            // Large Centered Title
            Text("FluenScene", style = TextStyle(brush = FluenSceneGradient), fontSize = 50.sp, fontWeight = FontWeight.Black, fontFamily = Agrandir, letterSpacing = 2.sp)

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle
            Text("Master English through Cinema", color = Color.Gray, fontFamily = Jakarta, fontSize = 18.sp)

            Spacer(modifier = Modifier.height(64.dp))

            // Features Row (Wrapped in IntrinsicSize.Max to force equal heights)
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard(modifier = Modifier.weight(1f).fillMaxHeight(), icon = Icons.Filled.Movie, title = "Learn Naturally", desc = "Real conversations from real movies")
                FeatureCard(modifier = Modifier.weight(1f).fillMaxHeight(), icon = Icons.Filled.ChatBubble, title = "Build Fluency", desc = "Improve listening, speaking & more")
                FeatureCard(modifier = Modifier.weight(1f).fillMaxHeight(), icon = Icons.Filled.Star, title = "Track Progress", desc = "Personalized insights that motivate")
            }

            Spacer(modifier = Modifier.height(56.dp))

            // Google Sign In Button
            Button(
                onClick = {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("232965060388-ltj3mp9q0pgqjc1lhq7j88rfl3d64f6e.apps.googleusercontent.com")
                        .requestEmail()
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                    launcher.launch(googleSignInClient.signInIntent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                modifier = Modifier.fillMaxWidth(0.9f).height(60.dp).background(FluenSceneGradient, RoundedCornerShape(30.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.size(28.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Text("G", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = Jakarta)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Sign in with Google", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = Jakarta, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Footer Privacy text linked to github
            val privacyUrl = "https://sanjaygireesh.github.io/fluenscene-privacy/"
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = "Privacy Shield", tint = Color.Gray, modifier = Modifier.size(18.dp).offset(y = 2.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("We respect your privacy.", color = Color.Gray, fontSize = 13.sp, fontFamily = Jakarta)
                    Spacer(modifier = Modifier.height(4.dp))
                    val annotatedStr = buildAnnotatedString {
                        append("By continuing, you agree to our ")
                        withStyle(style = SpanStyle(color = FluenSceneGreenSolid)) {
                            append("Terms & Privacy Policy.")
                        }
                    }
                    Text(annotatedStr, color = Color.Gray, fontSize = 13.sp, fontFamily = Jakarta)
                }
            }
        }
    }
}

@Composable
fun FeatureCard(modifier: Modifier, icon: ImageVector, title: String, desc: String) {
    Column(
        modifier = modifier
            .background(Color(0xFF0B1410), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF162A20), RoundedCornerShape(16.dp))
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Icon(icon, contentDescription = title, tint = FluenSceneGreenSolid, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Jakarta, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text(desc, color = Color.Gray, fontSize = 11.sp, fontFamily = Jakarta, textAlign = TextAlign.Center, lineHeight = 16.sp)
    }
}

@Composable
fun SetupRegionScreen(onRegionSaved: () -> Unit) {
    val context = LocalContext.current
    val userId = remember { getUserIdFingerprint() }
    var regionText by remember(userId) { mutableStateOf("") }
    var expanded by remember(userId) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        AmbientGreenGlow()
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp).systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to FluenScene",
                style = TextStyle(brush = FluenSceneGradient),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Comfortaa,
                lineHeight = 38.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Select your region to personalize your AI tutor.", color = Color.Gray, textAlign = TextAlign.Center, fontFamily = Jakarta)
            Spacer(modifier = Modifier.height(32.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { isExpanded -> expanded = isExpanded }
            ) {
                OutlinedTextField(
                    value = regionText,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Search your State / Country") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = Color.White,
                        disabledBorderColor = FluenSceneGreenSolid,
                        disabledLabelColor = Color.Gray,
                        disabledTrailingIconColor = Color.White
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth().clickable { expanded = true }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF1E2924))
                ) {
                    globalRegionsList.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = Color.White) },
                            onClick = { regionText = option; expanded = false }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val finalRegion = if (regionText.isNotBlank()) regionText else "Global / Neutral"
                    UserPreferences.saveRegion(context, userId, finalRegion)
                    onRegionSaved()
                },
                enabled = regionText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(if (regionText.isNotBlank()) FluenSceneGradient else Brush.linearGradient(listOf(Color(0xFF121212), Color(0xFF121212)))),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Text("Continue to Placement Test", color = if(regionText.isNotBlank()) Color.Black else Color.DarkGray, fontWeight = FontWeight.Bold, fontFamily = Jakarta, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
fun AIQuizScreen(onQuizFinished: (Int) -> Unit) {
    val context = LocalContext.current
    var questions by remember { mutableStateOf<JSONArray?>(null) }
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }

    var isProcessingQuiz by remember { mutableStateOf(false) }
    var isServerWaking by remember { mutableStateOf(false) }
    var isVoiceStage by remember { mutableStateOf(false) }
    var mcqScore by remember { mutableStateOf(5) }

    var showResultDialog by remember { mutableStateOf(false) }
    var finalCalculatedScore by remember { mutableIntStateOf(5) }
    val coroutineScope = rememberCoroutineScope()

    val selectedVoicePrompt by remember { mutableStateOf(dynamicVoicePrompts.random()) }

    LaunchedEffect(isProcessingQuiz) {
        if (isProcessingQuiz) {
            delay(4000)
            isServerWaking = true
        } else {
            isServerWaking = false
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            isProcessingQuiz = true

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val prompt = "The user was asked: \"$selectedVoicePrompt\". They verbally responded: \"$spokenText\". Grade their English conversational fluency (focusing on natural flow, vocabulary usage in context, and enunciation. Ignore theoretical grammar flaws if it sounds like native slang or conversational flow) strictly from 1 to 10. Respond ONLY with a single digit or number."
                    val evaluationResponse = generateTextFromGroq(prompt)

                    val match = Regex("\\b([1-9]|10)\\b").find(evaluationResponse)
                    val voiceScore = match?.value?.toInt() ?: mcqScore

                    finalCalculatedScore = ((mcqScore + voiceScore) / 2.0).roundToInt().coerceIn(1, 10)
                } catch (e: Exception) {
                    finalCalculatedScore = mcqScore
                }
                withContext(Dispatchers.Main) {
                    isProcessingQuiz = false
                    showResultDialog = true
                }
            }
        } else {
            finalCalculatedScore = mcqScore
            showResultDialog = true
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Please speak now...")
            }
            speechLauncher.launch(intent)
        } else {
            finalCalculatedScore = mcqScore
            showResultDialog = true
        }
    }

    LaunchedEffect(Unit) {
        val staticQuizJson = """
        [
          {"q": "Which word is closest in meaning to Happy?", "options": ["Angry", "Joyful", "Tired", "Lazy"], "ans": 1},
          {"q": "What is the opposite of Ancient?", "options": ["Old", "Historic", "Modern", "Traditional"], "ans": 2},
          {"q": "Which word is closest in meaning to Reluctant?", "options": ["Eager", "Hesitant", "Excited", "Brave"], "ans": 1},
          {"q": "What is the opposite of Scarce?", "options": ["Rare", "Few", "Abundant", "Limited"], "ans": 2},
          {"q": "Which word is closest in meaning to Meticulous?", "options": ["Careless", "Precise", "Fast", "Ordinary"], "ans": 1},
          {"q": "What does \"Break the ice\" mean?", "options": ["Destroy something cold", "Start a conversation comfortably", "Get angry", "Leave suddenly"], "ans": 1},
          {"q": "What does \"Hit the nail on the head\" mean?", "options": ["Miss the point", "Hurt someone", "Be exactly correct", "Work hard"], "ans": 2},
          {"q": "What does \"Once in a blue moon\" mean?", "options": ["Every day", "Very rarely", "At night", "During winter"], "ans": 1},
          {"q": "What does \"Bite off more than you can chew\" mean?", "options": ["Eat too much food", "Take on more responsibility than you can handle", "Waste time", "Complain a lot"], "ans": 1},
          {"q": "What does \"Throw in the towel\" mean?", "options": ["Clean a room", "Start a fight", "Give up or admit defeat", "Celebrate success"], "ans": 2},
          {"q": "Which sentence is correct?", "options": ["He don't like coffee.", "He doesn't likes coffee.", "He doesn't like coffee.", "He not like coffee."], "ans": 2},
          {"q": "Choose the best word: \"The scientist's explanation was so ______ that even children could understand it.\"", "options": ["Complex", "Obscure", "Clear", "Ambiguous"], "ans": 2},
          {"q": "If someone says: \"I expected the movie to be amazing, but it turned out to be just average.\" What is the speaker's feeling?", "options": ["Excited", "Disappointed", "Confused", "Terrified"], "ans": 1},
          {"q": "Which sentence sounds most natural?", "options": ["Despite of the rain, we went outside.", "In spite the rain, we went outside.", "Despite the rain, we went outside.", "Although of the rain, we went outside."], "ans": 2},
          {"q": "What is the difference between 'You must finish the report.' and 'You should finish the report.'?", "options": ["'Must' is advice, 'Should' is obligation.", "'Must' is obligation, 'Should' is advice.", "Both mean the exact same thing.", "'Must' is optional, 'Should' is required."], "ans": 1}
        ]
        """.trimIndent()

        questions = JSONArray(staticQuizJson)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)), contentAlignment = Alignment.Center) {
        AmbientGreenGlow()

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).systemBarsPadding(), verticalArrangement = Arrangement.Center) {
            if (questions == null) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = FluenSceneGreenSolid)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading your strict fluency test...", color = Color.White, fontFamily = Jakarta)
                }
            } else if (!isVoiceStage && questions != null && currentQuestionIndex < questions!!.length()) {
                val qObj = questions!!.getJSONObject(currentQuestionIndex)
                val options = qObj.getJSONArray("options")
                val correctIdx = qObj.getInt("ans")

                val phaseTitle = when (currentQuestionIndex) {
                    in 0..4 -> "Part 1: Synonyms & Antonyms"
                    in 5..9 -> "Part 2: Idioms & Expressions"
                    else -> "Part 3: Fluency & Comprehension"
                }

                Text(phaseTitle, style = TextStyle(brush = FluenSceneGradient), fontWeight = FontWeight.Bold, fontFamily = Comfortaa)
                Text("Question ${currentQuestionIndex + 1} / 15", color = Color.Gray, fontFamily = Jakarta)
                Spacer(modifier = Modifier.height(16.dp))
                Text(qObj.getString("q"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Jakarta)
                Spacer(modifier = Modifier.height(32.dp))

                for (i in 0 until options.length()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            .background(Color(0xFF121212), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1E2924), RoundedCornerShape(12.dp))
                            .clickable(!isProcessingQuiz) {
                                if (i == correctIdx) correctAnswers++
                                if (currentQuestionIndex < questions!!.length() - 1) {
                                    currentQuestionIndex++
                                } else {
                                    isProcessingQuiz = true
                                    mcqScore = ((correctAnswers.toFloat() / 15f) * 9f + 1f).roundToInt()
                                    isProcessingQuiz = false
                                    isVoiceStage = true
                                }
                            }.padding(20.dp)
                    ) {
                        Text(options.getString(i), color = Color.White, fontFamily = Jakarta)
                    }
                }
            } else if (isVoiceStage) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Final Phase: Spontaneous Speaking", style = TextStyle(brush = FluenSceneGradient), fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = Comfortaa)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Please answer the following prompt verbally.", color = Color.Gray, textAlign = TextAlign.Center, fontFamily = Jakarta)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Prompt: $selectedVoicePrompt", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFamily = Jakarta)
                    Spacer(modifier = Modifier.height(48.dp))

                    if (isProcessingQuiz) {
                        CircularProgressIndicator(color = FluenSceneGreenSolid)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if (isServerWaking) "Waking up server... (Up to 60s)" else "AI is analyzing your conversational fluency...", color = Color.Gray, fontFamily = Jakarta)
                    } else {
                        Button(
                            onClick = {
                                val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                if (permission == PackageManager.PERMISSION_GRANTED) {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Please speak now...")
                                    }
                                    speechLauncher.launch(intent)
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(100.dp).background(FluenSceneGradient, CircleShape),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Black),
                            contentPadding = PaddingValues()
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = "Speak", modifier = Modifier.size(48.dp), tint = Color.Black)
                        }
                    }
                }
            }
        }

        if (showResultDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Placement Test Complete", style = TextStyle(brush = FluenSceneGradient), fontWeight = FontWeight.Bold, fontFamily = Comfortaa) },
                text = {
                    Text("Your calculated English Fluency Level is:\n\n$finalCalculatedScore / 10\n\nFluenScene AI will now adapt its vocabulary strictly to your level.", color = Color.White, fontSize = 16.sp, fontFamily = Jakarta)
                },
                containerColor = Color(0xFF1E2924),
                confirmButton = {
                    Button(
                        onClick = {
                            showResultDialog = false
                            onQuizFinished(finalCalculatedScore)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        modifier = Modifier.background(FluenSceneGradient, RoundedCornerShape(8.dp))
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("Start Learning", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = Jakarta)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val userId = remember { getUserIdFingerprint() }

    var expanded by remember(userId) { mutableStateOf(false) }
    var regionText by remember(userId) { mutableStateOf(UserPreferences.getUserRegion(context, userId)) }
    val initialRegion = remember(userId) { UserPreferences.getUserRegion(context, userId) }

    var cloudFluency by remember(userId) { mutableIntStateOf(5) }
    var isPremium by remember(userId) { mutableStateOf(false) }

    var showDeleteDialog by remember(userId) { mutableStateOf(false) }
    var showUnsavedWarning by remember(userId) { mutableStateOf(false) }
    var isDeleting by remember(userId) { mutableStateOf(false) }
    var deleteError by remember(userId) { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("232965060388-ltj3mp9q0pgqjc1lhq7j88rfl3d64f6e.apps.googleusercontent.com")
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            try {
                val doc = db.collection("users").document(userId).get().await()
                cloudFluency = doc.getLong("fluencyLevel")?.toInt() ?: 5
                isPremium = doc.getBoolean("isPremium") ?: false
            } catch (e: Exception) {}
        }
    }

    val handleBackPress = {
        if (regionText != initialRegion) {
            showUnsavedWarning = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler { handleBackPress() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White, fontFamily = Comfortaa) },
                navigationIcon = {
                    IconButton(onClick = { handleBackPress() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.background(Color(0xFF0A0A0A))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
            AmbientGreenGlow()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Account Status", style = TextStyle(brush = FluenSceneGradient), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp), fontFamily = Comfortaa)

                val emailText = auth.currentUser?.email ?: "Unknown"
                Text("Email: $emailText", color = Color.White, fontSize = 16.sp, fontFamily = Jakarta)

                val premiumText = if (isPremium) "Active" else "Free Tier"
                val premiumColor = if (isPremium) FluenSceneGreenSolid else Color.White
                Text("Premium: $premiumText", color = premiumColor, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp), fontFamily = Jakarta)

                Spacer(modifier = Modifier.height(40.dp))

                Text("Cultural Localization", style = TextStyle(brush = FluenSceneGradient), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp), fontFamily = Comfortaa)

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { isExpanded -> expanded = isExpanded }
                ) {
                    OutlinedTextField(
                        value = regionText,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Search your State / Country", color = Color.Gray) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.White,
                            disabledBorderColor = FluenSceneGreenSolid,
                            disabledLabelColor = Color.Gray,
                            disabledTrailingIconColor = Color.White
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth().clickable { expanded = true }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF1E2924))
                    ) {
                        globalRegionsList.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = Color.White, fontFamily = Jakarta) },
                                onClick = { regionText = option; expanded = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                Text("AI Assigned Fluency Level", style = TextStyle(brush = FluenSceneGradient), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp), fontFamily = Comfortaa)
                Text("Level: $cloudFluency / 10", color = Color.White, fontSize = 18.sp, fontFamily = Jakarta)
                Text("(This increases dynamically as you request more translations)", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), fontFamily = Jakarta)

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = {
                        UserPreferences.saveRegion(context, userId, regionText)
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).background(FluenSceneGradient, RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Text("Save Settings", color = Color.Black, fontFamily = Jakarta, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        auth.signOut()
                        googleSignInClient.signOut().addOnCompleteListener {
                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            (context as? Activity)?.finish()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Sign Out / Switch Google Account", fontFamily = Jakarta)
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete Account & Personal Data", fontWeight = FontWeight.Bold, fontFamily = Jakarta)
                }

                Spacer(modifier = Modifier.height(32.dp))

                val privacyUrl = "https://sanjaygireesh.github.io/fluenscene-privacy/"
                Text(
                    text = "Privacy Policy",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
                            context.startActivity(intent)
                        },
                    textAlign = TextAlign.Center,
                    fontFamily = Jakarta
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showUnsavedWarning) {
        AlertDialog(
            onDismissRequest = { showUnsavedWarning = false },
            title = { Text("Unsaved Changes", style = TextStyle(brush = FluenSceneGradient), fontWeight = FontWeight.Bold, fontFamily = Comfortaa) },
            text = { Text("You changed your region but haven't saved. Go back anyway?", color = Color.White, fontFamily = Jakarta) },
            containerColor = Color(0xFF1E2924),
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedWarning = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) { Text("Discard", fontFamily = Jakarta) }
            },
            dismissButton = {
                Button(
                    onClick = {
                        UserPreferences.saveRegion(context, userId, regionText)
                        showUnsavedWarning = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier.background(FluenSceneGradient, RoundedCornerShape(8.dp)),
                    contentPadding = PaddingValues()
                ) { Box(modifier = Modifier.padding(16.dp, 8.dp)) { Text("Save & Leave", color = Color.Black, fontFamily = Jakarta) } }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text("Delete Account permanently?", color = Color.Red, fontWeight = FontWeight.Bold, fontFamily = Comfortaa) },
            text = {
                Column {
                    Text("This action cannot be undone. All custom history, localization tracking, and fluency rankings will be permanently purged to respect your privacy.", color = Color.White, fontFamily = Jakarta)
                    if (deleteError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = deleteError!!, color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = Color(0xFF1E2924),
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                    enabled = !isDeleting,
                    onClick = {
                        isDeleting = true
                        val currentUser = auth.currentUser

                        if (userId.isNotEmpty() && currentUser != null) {
                            coroutineScope.launch {
                                try {
                                    db.collection("users").document(userId).update(
                                        mapOf(
                                            "email" to com.google.firebase.firestore.FieldValue.delete(),
                                            "fluencyLevel" to com.google.firebase.firestore.FieldValue.delete(),
                                            "accountStatus" to "deleted"
                                        )
                                    ).await()

                                    UserPreferences.clearLocalData(context, userId)
                                    currentUser.delete().await()

                                    auth.signOut()
                                    googleSignInClient.signOut().await()

                                    isDeleting = false
                                    showDeleteDialog = false

                                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    (context as? Activity)?.finish()

                                } catch (e: Exception) {
                                    isDeleting = false
                                    deleteError = "Security Error: Please log out, log back in, and try deleting immediately."
                                }
                            }
                        }
                    }
                ) {
                    Text(if (isDeleting) "Purging..." else "Confirm Delete", fontFamily = Jakarta)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, enabled = !isDeleting) {
                    Text("Cancel", color = Color.Gray, fontFamily = Jakarta)
                }
            }
        )
    }
}

@Composable
fun LocalVideoLibrary(
    selectedFolder: String?,
    onFolderSelected: (String?) -> Unit,
    onVideoSelected: (List<Uri>, Int) -> Unit,
    onSettingsClicked: () -> Unit
) {
    val context = LocalContext.current
    var videoList by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            videoList = loadVideosFromStorage(context)
        } else {
            val targetPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
            permissionLauncher.launch(targetPermission)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundParticles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(animation = tween(200000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "MasterTime"
    )

    val particles = remember {
        List(6) {
            GlassParticle(
                type = GlassShapeType.entries.random(),
                size = Random.nextFloat() * 150f + 80f,
                centerX = Random.nextFloat(), centerY = Random.nextFloat(),
                orbitPhaseX = Random.nextFloat() * (2 * Math.PI).toFloat(), orbitPhaseY = Random.nextFloat() * (2 * Math.PI).toFloat(),
                speedX = (Random.nextFloat() * 0.005f) + 0.002f, speedY = (Random.nextFloat() * 0.005f) + 0.002f,
                orbitRadiusX = Random.nextFloat() * 0.3f + 0.1f, orbitRadiusY = Random.nextFloat() * 0.3f + 0.1f,
                rotationSpeed = (Random.nextFloat() * 0.05f) - 0.02f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientGreenGlow()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val subtleGreen = Color(0xFF2ADF8E)

            fun draw3DGlassSphere(x: Float, y: Float, radius: Float) {
                val sphereBrush = Brush.radialGradient(colors = listOf(subtleGreen.copy(alpha = 0.08f), subtleGreen.copy(alpha = 0.01f), Color.Transparent), center = Offset(x - radius * 0.3f, y - radius * 0.3f), radius = radius * 1.5f)
                val edgeBrush = Brush.linearGradient(colors = listOf(subtleGreen.copy(alpha = 0.15f), Color.Transparent), start = Offset(x - radius, y - radius), end = Offset(x + radius, y + radius))
                drawCircle(brush = sphereBrush, radius = radius, center = Offset(x, y))
                drawCircle(brush = edgeBrush, radius = radius, center = Offset(x, y), style = Stroke(width = 1f))
            }

            fun draw3DGlassCube(x: Float, y: Float, cubeSize: Float, rotation: Float) {
                rotate(degrees = rotation, pivot = Offset(x + cubeSize / 2, y + cubeSize / 2)) {
                    val cubeBrush = Brush.linearGradient(colors = listOf(subtleGreen.copy(alpha = 0.05f), Color.Transparent), start = Offset(x, y), end = Offset(x + cubeSize, y + cubeSize))
                    val edgeBrush = Brush.linearGradient(colors = listOf(subtleGreen.copy(alpha = 0.15f), Color.Transparent, subtleGreen.copy(alpha = 0.05f)), start = Offset(x, y), end = Offset(x + cubeSize, y + cubeSize))
                    val cornerRad = CornerRadius(cubeSize * 0.3f, cubeSize * 0.3f)
                    drawRoundRect(brush = cubeBrush, topLeft = Offset(x, y), size = Size(cubeSize, cubeSize), cornerRadius = cornerRad)
                    drawRoundRect(brush = edgeBrush, topLeft = Offset(x, y), size = Size(cubeSize, cubeSize), cornerRadius = cornerRad, style = Stroke(width = 1f))
                }
            }

            fun draw3DGlassCapsule(x: Float, y: Float, baseSize: Float, rotation: Float) {
                val capWidth = baseSize; val capHeight = baseSize * 2.5f
                rotate(degrees = rotation, pivot = Offset(x + capWidth / 2, y + capHeight / 2)) {
                    val capBrush = Brush.linearGradient(colors = listOf(subtleGreen.copy(alpha = 0.06f), Color.Transparent), start = Offset(x, y), end = Offset(x + capWidth, y + capHeight))
                    val edgeBrush = Brush.linearGradient(colors = listOf(subtleGreen.copy(alpha = 0.15f), Color.Transparent, subtleGreen.copy(alpha = 0.05f)), start = Offset(x, y), end = Offset(x + capWidth, y + capHeight))
                    val cornerRad = CornerRadius(capWidth / 2, capWidth / 2)
                    drawRoundRect(brush = capBrush, topLeft = Offset(x, y), size = Size(capWidth, capHeight), cornerRadius = cornerRad)
                    drawRoundRect(brush = edgeBrush, topLeft = Offset(x, y), size = Size(capWidth, capHeight), cornerRadius = cornerRad, style = Stroke(width = 1f))
                }
            }

            particles.forEach { p ->
                val currentX = (w * p.centerX) + (cos(time * p.speedX + p.orbitPhaseX) * w * p.orbitRadiusX).toFloat()
                val currentY = (h * p.centerY) + (sin(time * p.speedY + p.orbitPhaseY) * h * p.orbitRadiusY).toFloat()
                val currentRot = time * p.rotationSpeed * 50f
                when (p.type) {
                    GlassShapeType.SPHERE -> draw3DGlassSphere(currentX, currentY, p.size / 2)
                    GlassShapeType.CUBE -> draw3DGlassCube(currentX, currentY, p.size, currentRot)
                    GlassShapeType.CAPSULE -> draw3DGlassCapsule(currentX, currentY, p.size / 1.5f, currentRot)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).systemBarsPadding()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSettingsClicked) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Settings", tint = FluenSceneGreenSolid, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("FluenScene", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = Comfortaa)
                        Text("Select a video to learn", style = TextStyle(brush = FluenSceneGradient), fontSize = 12.sp, fontFamily = Jakarta)
                    }
                }
            }

            if (!hasPermission) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        val targetPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
                        permissionLauncher.launch(targetPermission)
                    }) { Text("Grant Storage Permission") }
                }
            } else if (videoList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No playable videos found.", color = Color.Gray, fontFamily = Jakarta)
                }
            } else {
                val groupedVideos = videoList.groupBy { video: LocalVideo -> video.folder }

                if (selectedFolder == null) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 90.dp)
                    ) {
                        items(groupedVideos.keys.toList().sorted()) { folder: String ->
                            Card(
                                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x332ADF8E), RoundedCornerShape(12.dp)).clickable { onFolderSelected(folder) },
                                colors = CardDefaults.cardColors(containerColor = Color(0x660A1F14))
                            ) {
                                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Folder, contentDescription = "Folder", tint = FluenSceneGreenSolid, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(folder, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = Jakarta, maxLines = 1, textAlign = TextAlign.Center)
                                    Text("${groupedVideos[folder]?.size ?: 0} Videos", color = Color.Gray, fontSize = 12.sp, fontFamily = Jakarta)
                                }
                            }
                        }
                    }
                } else {
                    val folderVideos = groupedVideos[selectedFolder] ?: emptyList()
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 90.dp)) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onFolderSelected(null) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                                Text(selectedFolder, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Comfortaa)
                            }
                        }
                        items(folderVideos) { video: LocalVideo ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Color(0x332ADF8E), RoundedCornerShape(12.dp)).clickable {
                                    onVideoSelected(folderVideos.map { it.uri }, folderVideos.indexOf(video))
                                },
                                colors = CardDefaults.cardColors(containerColor = Color(0x660A1F14))
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    VideoThumbnail(uri = video.uri, videoId = video.id, modifier = Modifier.size(80.dp, 60.dp).clip(RoundedCornerShape(8.dp)))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(video.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, fontFamily = Jakarta)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(video.duration, color = Color.Gray, fontSize = 12.sp, fontFamily = Jakarta)
                                            Text(video.size, style = TextStyle(brush = FluenSceneGradient), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Jakarta)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerScreen(playlist: List<Uri>, startIndex: Int, onDismissPlayer: () -> Unit, userId: String) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val userRegion = remember(userId) { UserPreferences.getUserRegion(context, userId) }
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? ComponentActivity

    var showPaymentDialog by remember { mutableStateOf(false) }

    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    val performHardVibration = {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(25, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25)
            }
        } catch (e: Exception) {}
    }

    var currentSubtitleText by remember { mutableStateOf("") }
    var aiExplanationText by remember { mutableStateOf<String?>(null) }
    var isThinking by remember { mutableStateOf(false) }
    var isServerWaking by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }

    var seekPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val mediaRetriever = remember { MediaMetadataRetriever() }
    var lastFrameExtractionTime by remember { mutableLongStateOf(0L) }
    var isScrubbingBar by remember { mutableStateOf(false) }
    var scrubBarTimeString by remember { mutableStateOf("") }
    var isSubtitleEnabled by remember { mutableStateOf(true) }

    var showRemainingTime by remember { mutableStateOf(false) }

    var showLangMenu by remember { mutableStateOf(false) }
    var textTracks by remember { mutableStateOf<List<Triple<String, Int, Int>>>(emptyList()) }

    var showSettingsMenu by remember { mutableStateOf(false) }
    var showAudioTracksMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<Triple<String, Int, Int>>>(emptyList()) }

    var hasSeenSubTutorial by remember(userId) { mutableStateOf(UserPreferences.hasSeenSubTutorial(context, userId)) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var underlyingPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    var isInPipMode by remember { mutableStateOf(false) }

    var volumeMultiplier by remember { mutableIntStateOf(1) }
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }

    val dynamicSpeedLevels = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
    var currentDynamicSpeedIndex by remember { mutableIntStateOf(3) }
    var isDynamicSpeedActive by remember { mutableStateOf(false) }
    var speedSwipeAccumulator by remember { mutableFloatStateOf(0f) }

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
    )
    var currentResizeModeIndex by remember { mutableIntStateOf(0) }

    var customSubtitleUri by remember { mutableStateOf<Uri?>(null) }
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) { customSubtitleUri = uri }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isThinking) {
        if (isThinking) {
            delay(4000)
            isServerWaking = true
        } else {
            isServerWaking = false
        }
    }

    val exoPlayer = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(false)

        ExoPlayer.Builder(context, renderersFactory).build().apply {
            val mediaItems = playlist.map { MediaItem.fromUri(it) }
            setMediaItems(mediaItems, startIndex, C.TIME_UNSET)

            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
                .build()

            addListener(object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) {
                    currentSubtitleText = cueGroup.cues.joinToString("\n") { it.text?.toString() ?: "" }
                }
                override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                    isPlaying = isPlayingChange
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        videoDuration = duration.coerceAtLeast(0L)
                    }
                }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    currentPosition = currentPosition.coerceAtLeast(0L)
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    currentSubtitleText = ""
                    aiExplanationText = null
                    customSubtitleUri = null

                    val localUri = mediaItem?.localConfiguration?.uri
                    if (localUri != null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                mediaRetriever.setDataSource(context, localUri)
                            } catch (e: Exception) { }
                        }
                    }
                }
            })
        }
    }

    LaunchedEffect(isPlaying, isScrubbingBar) {
        while(isPlaying && !isScrubbingBar) {
            currentPosition = exoPlayer.currentPosition
            delay(1000)
        }
    }

    LaunchedEffect(customSubtitleUri) {
        if (customSubtitleUri != null) {
            val currentMediaItem = exoPlayer.currentMediaItem
            val currentIndex = exoPlayer.currentMediaItemIndex
            if (currentMediaItem != null) {
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(customSubtitleUri!!)
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage("en")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()

                val updatedMediaItem = currentMediaItem.buildUpon()
                    .setSubtitleConfigurations(listOf(subtitleConfig))
                    .build()

                exoPlayer.replaceMediaItem(currentIndex, updatedMediaItem)
            }
        }
    }

    DisposableEffect(Unit) {
        isVideoPlayingGlobally = true
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
            if (isInPipMode) {
                areControlsVisible = false
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.addOnPictureInPictureModeChangedListener(pipListener)
        }

        val savedPos = UserPreferences.getVideoPosition(context, playlist[startIndex].toString())
        exoPlayer.seekTo(startIndex, savedPos)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        onDispose {
            isVideoPlayingGlobally = false
            loudnessEnhancer?.release()
            try { mediaRetriever.release() } catch (e: Exception) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity?.removeOnPictureInPictureModeChangedListener(pipListener)
            }
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            val currentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri
            if (currentUri != null) {
                UserPreferences.saveVideoPosition(context, currentUri.toString(), exoPlayer.currentPosition)
            }
            exoPlayer.release()
        }
    }

    var isHorizontalSeeking by remember { mutableStateOf(false) }
    var seekAccumulator by remember { mutableFloatStateOf(0f) }
    var seekStartPosition by remember { mutableStateOf(0L) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    subtitleView?.visibility = View.INVISIBLE
                    underlyingPlayerView = this
                }
            },
            update = { view -> view.resizeMode = resizeModes[currentResizeModeIndex].first },
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (!isInPipMode) areControlsVisible = !areControlsVisible },
                        onDoubleTap = {
                            if (!isInPipMode) {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                performHardVibration()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            if (!isInPipMode && exoPlayer.isPlaying) {
                                isDynamicSpeedActive = true
                                currentDynamicSpeedIndex = dynamicSpeedLevels.indexOf(playbackSpeed).coerceAtLeast(3)
                                speedSwipeAccumulator = 0f
                                exoPlayer.playbackParameters = PlaybackParameters(dynamicSpeedLevels[currentDynamicSpeedIndex])
                                performHardVibration()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (isDynamicSpeedActive) {
                                change.consume()
                                speedSwipeAccumulator += dragAmount.x
                                val threshold = 80f
                                if (abs(speedSwipeAccumulator) >= threshold) {
                                    val steps = (speedSwipeAccumulator / threshold).toInt()
                                    val newIndex = (currentDynamicSpeedIndex + steps).coerceIn(0, dynamicSpeedLevels.size - 1)
                                    if (newIndex != currentDynamicSpeedIndex) {
                                        currentDynamicSpeedIndex = newIndex
                                        speedSwipeAccumulator = 0f
                                        exoPlayer.playbackParameters = PlaybackParameters(dynamicSpeedLevels[currentDynamicSpeedIndex])
                                        performHardVibration()
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (isDynamicSpeedActive) {
                                isDynamicSpeedActive = false
                                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                            }
                        },
                        onDragCancel = {
                            if (isDynamicSpeedActive) {
                                isDynamicSpeedActive = false
                                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isHorizontalSeeking = false
                            seekAccumulator = 0f
                            seekStartPosition = exoPlayer.currentPosition
                        },
                        onDragEnd = {
                            if (!isInPipMode) {
                                gestureIndicatorText = null
                                isHorizontalSeeking = false
                                seekPreviewBitmap = null
                            }
                        },
                        onDragCancel = {
                            if (!isInPipMode) {
                                gestureIndicatorText = null
                                isHorizontalSeeking = false
                                seekPreviewBitmap = null
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (isInPipMode || isDynamicSpeedActive) return@detectDragGestures
                            change.consume()
                            val halfWidth = size.width / 2

                            if (!isHorizontalSeeking && abs(dragAmount.x) > abs(dragAmount.y) && abs(dragAmount.x) > 2f) {
                                isHorizontalSeeking = true
                            }

                            if (isHorizontalSeeking) {
                                seekAccumulator += dragAmount.x
                                val seekOffset = (seekAccumulator * 80).toLong()
                                val targetPos = (seekStartPosition + seekOffset).coerceIn(0, exoPlayer.duration.coerceAtLeast(0))
                                exoPlayer.seekTo(targetPos)
                                currentPosition = targetPos

                                if (System.currentTimeMillis() - lastFrameExtractionTime > 300) {
                                    lastFrameExtractionTime = System.currentTimeMillis()
                                    extractSeekPreviewFrame(targetPos, mediaRetriever, coroutineScope) { bmp -> seekPreviewBitmap = bmp }
                                }

                                gestureIndicatorText = "Seek: ${formatTime(targetPos)}"
                            } else {
                                val sensitivity = 0.005f
                                if (change.position.x < halfWidth) {
                                    activity?.window?.let { window ->
                                        val lp = window.attributes
                                        var currentBrightness = lp.screenBrightness
                                        if (currentBrightness < 0) currentBrightness = 0.5f
                                        currentBrightness = (currentBrightness - (dragAmount.y * sensitivity)).coerceIn(0.01f, 1.0f)
                                        lp.screenBrightness = currentBrightness
                                        window.attributes = lp
                                        window.attributes = lp
                                        gestureIndicatorText = "Brightness: ${(currentBrightness * 100).toInt()}%"
                                    }
                                } else {
                                    val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                    val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                    val delta = -(dragAmount.y * sensitivity * maxVol).toInt()
                                    val targetVol = (currentVol + delta).coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                                    gestureIndicatorText = "Volume: $targetVol / $maxVol"
                                }
                            }
                        }
                    )
                }
        )

        // ------------------------------------------------------------------------------------------------
        // CUSTOM COMPOSE PLAYBACK UI
        // ------------------------------------------------------------------------------------------------

        // --- 1. Top Control Bar Pill ---
        AnimatedVisibility(
            visible = areControlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).displayCutoutPadding().padding(top = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            val containerModifier = if (isPortrait) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                    .border(1.dp, FluenSceneGreenSolid.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            } else {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                    .border(1.dp, FluenSceneGreenSolid.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            }

            Box(modifier = containerModifier) {
                if (isPortrait) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismissPlayer, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Player", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.DarkGray)
                            Text(
                                text = "Vol ${volumeMultiplier}00%",
                                color = if (volumeMultiplier > 1) Color.Red else FluenSceneGreenSolid,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.clickable {
                                    volumeMultiplier = if (volumeMultiplier == 3) 1 else volumeMultiplier + 1
                                    try {
                                        if (loudnessEnhancer == null && exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                                            loudnessEnhancer = LoudnessEnhancer(exoPlayer.audioSessionId)
                                        }
                                        val gainMb = when (volumeMultiplier) {
                                            2 -> 1500
                                            3 -> 3000
                                            else -> 0
                                        }
                                        loudnessEnhancer?.setTargetGain(gainMb)
                                        loudnessEnhancer?.enabled = (volumeMultiplier > 1)
                                        performHardVibration()
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            )
                            Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.DarkGray)
                            Text(
                                text = resizeModes[currentResizeModeIndex].second,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.clickable {
                                    currentResizeModeIndex = (currentResizeModeIndex + 1) % resizeModes.size
                                    performHardVibration()
                                }
                            )
                        }

                        Divider(modifier = Modifier.width(200.dp).height(1.dp), color = Color.DarkGray)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSubtitleEnabled) "CC: ON" else "CC: OFF",
                                color = if (isSubtitleEnabled) FluenSceneGreenSolid else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.clickable {
                                    isSubtitleEnabled = !isSubtitleEnabled
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                                        .build()
                                    performHardVibration()
                                }
                            )
                            Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.DarkGray)
                            Text(
                                text = "Load",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.clickable {
                                    subtitlePickerLauncher.launch("*/*")
                                    performHardVibration()
                                }
                            )
                            Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.DarkGray)
                            IconButton(
                                onClick = {
                                    val currentOrientation = activity?.resources?.configuration?.orientation
                                    if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    } else {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                    performHardVibration()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate Screen", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismissPlayer, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Player", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.DarkGray)
                        Text(
                            text = "Vol ${volumeMultiplier}00%",
                            color = if (volumeMultiplier > 1) Color.Red else FluenSceneGreenSolid,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.clickable {
                                volumeMultiplier = if (volumeMultiplier == 3) 1 else volumeMultiplier + 1
                                try {
                                    if (loudnessEnhancer == null && exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                                        loudnessEnhancer = LoudnessEnhancer(exoPlayer.audioSessionId)
                                    }
                                    val gainMb = when (volumeMultiplier) {
                                        2 -> 1500
                                        3 -> 3000
                                        else -> 0
                                    }
                                    loudnessEnhancer?.setTargetGain(gainMb)
                                    loudnessEnhancer?.enabled = (volumeMultiplier > 1)
                                    performHardVibration()
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        )
                        Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.DarkGray)
                        Text(
                            text = resizeModes[currentResizeModeIndex].second,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.clickable {
                                currentResizeModeIndex = (currentResizeModeIndex + 1) % resizeModes.size
                                performHardVibration()
                            }
                        )
                        Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.DarkGray)
                        Text(
                            text = if (isSubtitleEnabled) "CC: ON" else "CC: OFF",
                            color = if (isSubtitleEnabled) FluenSceneGreenSolid else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.clickable {
                                isSubtitleEnabled = !isSubtitleEnabled
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                                    .build()
                                performHardVibration()
                            }
                        )
                        Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.DarkGray)
                        Text(
                            text = "Load",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.clickable {
                                subtitlePickerLauncher.launch("*/*")
                                performHardVibration()
                            }
                        )
                        Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.DarkGray)
                        IconButton(
                            onClick = {
                                val currentOrientation = activity?.resources?.configuration?.orientation
                                if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                } else {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                                performHardVibration()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate Screen", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        // --- 2. Center Media Controls ---
        AnimatedVisibility(
            visible = areControlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val skipSize = if (isPortrait) 48.dp else 40.dp
            val playBtnOuterSize = if (isPortrait) 72.dp else 64.dp
            val playBtnInnerSize = if (isPortrait) 56.dp else 48.dp
            val skipIconSize = if (isPortrait) 24.dp else 22.dp
            val playIconSize = if (isPortrait) 32.dp else 28.dp

            Row(
                modifier = Modifier.fillMaxWidth(if (isPortrait) 1f else 0.5f),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip Previous
                Box(modifier = Modifier.size(skipSize).clickable { exoPlayer.seekToPreviousMediaItem() }, contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.background(Color.Black.copy(alpha=0.6f), CircleShape).padding(8.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(skipIconSize))
                    }
                }
                // Jump Back 5
                Box(modifier = Modifier.size(skipSize).clickable { exoPlayer.seekTo((currentPosition - 5000).coerceAtLeast(0)) }, contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.background(Color.Black.copy(alpha=0.6f), CircleShape).padding(8.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(skipIconSize))
                            if (!isPortrait) {
                                Text("5", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-4).dp))
                            }
                        }
                    }
                }

                // Play / Pause Master (With Green Glow Effect)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(playBtnOuterSize)) {
                    // Glow layer
                    Box(modifier = Modifier
                        .size(playBtnInnerSize + 16.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(FluenSceneGreenSolid.copy(alpha = 0.6f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                    )
                    // Actual button
                    Box(modifier = Modifier
                        .size(playBtnInnerSize)
                        .background(FluenSceneGreenSolid, CircleShape)
                        .clickable { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(playIconSize)
                        )
                    }
                }

                // Jump Forward 15
                Box(modifier = Modifier.size(skipSize).clickable { exoPlayer.seekTo((currentPosition + 15000).coerceAtMost(videoDuration)) }, contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.background(Color.Black.copy(alpha=0.6f), CircleShape).padding(8.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(skipIconSize))
                            if (!isPortrait) {
                                Text("15", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-4).dp))
                            }
                        }
                    }
                }
                // Skip Next
                Box(modifier = Modifier.size(skipSize).clickable { exoPlayer.seekToNextMediaItem() }, contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.background(Color.Black.copy(alpha=0.6f), CircleShape).padding(8.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(skipIconSize))
                    }
                }
            }
        }

        // --- 3. Bottom Scrubber Pill (Integrated CC: LANG to prevent collisions) ---
        AnimatedVisibility(
            visible = areControlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (isPortrait) 32.dp else 24.dp, start = 24.dp, end = 24.dp)
        ) {
            val scrubberModifier = if (isPortrait) {
                Modifier
                    .fillMaxWidth(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            } else {
                Modifier
                    .fillMaxWidth(0.95f)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            }

            Column(
                modifier = scrubberModifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Slider(
                    value = if (videoDuration > 0) currentPosition.toFloat() / videoDuration.toFloat() else 0f,
                    onValueChange = { percent ->
                        isScrubbingBar = true
                        val newPos = (percent * videoDuration).toLong()
                        currentPosition = newPos
                        exoPlayer.seekTo(newPos)
                        scrubBarTimeString = formatTime(newPos)
                        if (System.currentTimeMillis() - lastFrameExtractionTime > 150) {
                            lastFrameExtractionTime = System.currentTimeMillis()
                            extractSeekPreviewFrame(newPos, mediaRetriever, coroutineScope) { bmp -> seekPreviewBitmap = bmp }
                        }
                    },
                    onValueChangeFinished = {
                        isScrubbingBar = false
                        seekPreviewBitmap = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = FluenSceneGreenSolid,
                        activeTrackColor = FluenSceneGreenSolid,
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth(1f).height(if (isPortrait) 40.dp else 24.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showRemainingTime) "-${formatTime(videoDuration - currentPosition)}" else "${formatTime(currentPosition)}  •  ${formatTime(videoDuration)}",
                        color = FluenSceneGreenSolid,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Embedded CC: LANG Selection properly aligned with settings to prevent collision
                        Box {
                            Text(
                                text = "CC: LANG",
                                color = FluenSceneGreenSolid,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.clickable {
                                    val tracks = mutableListOf<Triple<String, Int, Int>>()
                                    val groups = exoPlayer.currentTracks.groups
                                    for (i in groups.indices) {
                                        val group = groups[i]
                                        if (group.type == C.TRACK_TYPE_TEXT) {
                                            for (j in 0 until group.length) {
                                                val format = group.mediaTrackGroup.getFormat(j)
                                                val langName = format.language?.uppercase() ?: format.label ?: "TRACK ${j+1}"
                                                tracks.add(Triple(langName, i, j))
                                            }
                                        }
                                    }
                                    textTracks = tracks
                                    showLangMenu = true
                                }
                            )

                            DropdownMenu(
                                expanded = showLangMenu,
                                onDismissRequest = { showLangMenu = false }
                            ) {
                                if (textTracks.isEmpty()) {
                                    DropdownMenuItem(text = { Text("No Subtitles Found", color = Color.Gray) }, onClick = { showLangMenu = false })
                                } else {
                                    textTracks.forEach { track ->
                                        DropdownMenuItem(
                                            text = { Text(track.first, color = Color.Black) },
                                            onClick = {
                                                val override = androidx.media3.common.TrackSelectionOverride(
                                                    exoPlayer.currentTracks.groups[track.second].mediaTrackGroup,
                                                    track.third
                                                )
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                    .setOverrideForType(override)
                                                    .build()
                                                showLangMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Settings Gear
                        Box {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable {
                                        performHardVibration()
                                        showSettingsMenu = true
                                    }
                            )

                            // Settings Main Menu
                            DropdownMenu(
                                expanded = showSettingsMenu,
                                onDismissRequest = { showSettingsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Audio Tracks", color = Color.Black) },
                                    onClick = {
                                        val tracks = mutableListOf<Triple<String, Int, Int>>()
                                        val groups = exoPlayer.currentTracks.groups
                                        for (i in groups.indices) {
                                            val group = groups[i]
                                            if (group.type == C.TRACK_TYPE_AUDIO) {
                                                for (j in 0 until group.length) {
                                                    val format = group.mediaTrackGroup.getFormat(j)
                                                    val langName = format.language?.uppercase() ?: format.label ?: "AUDIO ${j+1}"
                                                    val codec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: ""
                                                    val channels = if (format.channelCount > 0) "${format.channelCount}ch" else ""
                                                    val fullName = listOf(langName, codec, channels).filter { it.isNotEmpty() }.joinToString(" • ")
                                                    tracks.add(Triple(fullName, i, j))
                                                }
                                            }
                                        }
                                        audioTracks = tracks
                                        showSettingsMenu = false
                                        showAudioTracksMenu = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Playback Speed", color = Color.Black) },
                                    onClick = {
                                        showSettingsMenu = false
                                        showSpeedMenu = true
                                    }
                                )
                            }

                            // Audio Tracks Sub-Menu
                            DropdownMenu(
                                expanded = showAudioTracksMenu,
                                onDismissRequest = { showAudioTracksMenu = false }
                            ) {
                                if (audioTracks.isEmpty()) {
                                    DropdownMenuItem(text = { Text("No Audio Tracks Found", color = Color.Gray) }, onClick = { showAudioTracksMenu = false })
                                } else {
                                    audioTracks.forEach { track ->
                                        DropdownMenuItem(
                                            text = { Text(track.first, color = Color.Black) },
                                            onClick = {
                                                val override = androidx.media3.common.TrackSelectionOverride(
                                                    exoPlayer.currentTracks.groups[track.second].mediaTrackGroup,
                                                    track.third
                                                )
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                    .setOverrideForType(override)
                                                    .build()
                                                showAudioTracksMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Playback Speed Sub-Menu
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                speeds.forEach { speed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${speed}x",
                                                color = if (playbackSpeed == speed) FluenSceneGreenSolid else Color.Black,
                                                fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            playbackSpeed = speed
                                            exoPlayer.playbackParameters = PlaybackParameters(speed)
                                            showSpeedMenu = false
                                            performHardVibration()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------------------------------------------
        // GESTURE PREVIEWS & DYNAMIC SPEED
        // ------------------------------------------------------------------------------------------------

        AnimatedVisibility(
            visible = isDynamicSpeedActive && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (isPortrait) {
                // Curved Speedometer Layout for Portrait
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(bottom = 40.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val radius = 130.dp
                    dynamicSpeedLevels.forEachIndexed { index, speed ->
                        val isActive = index == currentDynamicSpeedIndex
                        val count = dynamicSpeedLevels.size

                        // Sweeping arc from 180 degrees to 0 degrees to expand the spread entirely
                        val startAngle = 180.0 * PI / 180.0
                        val endAngle = 0.0 * PI / 180.0
                        val angle = startAngle - (index.toDouble() / (count - 1)) * (startAngle - endAngle)

                        val xOffset = (cos(angle) * radius.value).dp
                        val yOffset = (-sin(angle) * radius.value).dp

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(x = xOffset, y = yOffset)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${speed}x",
                                color = if (isActive) FluenSceneGreenSolid else Color.White,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (isActive) 24.sp else 16.sp,
                                style = TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black,
                                        offset = Offset(2f, 2f),
                                        blurRadius = 8f
                                    )
                                )
                            )
                        }
                    }
                    Text(
                        text = "Slide left/right to adjust",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 20.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                // Horizontal Pill Layout for Landscape
                Box(modifier = Modifier
                    .align(Alignment.TopCenter)
                    .displayCutoutPadding()
                    .padding(top = 100.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(50))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        dynamicSpeedLevels.forEachIndexed { index, speed ->
                            val isActive = index == currentDynamicSpeedIndex
                            Text(
                                text = "${speed}x",
                                color = if (isActive) FluenSceneGreenSolid else Color.Gray,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (isActive) 22.sp else 16.sp
                            )
                        }
                    }
                }
            }
        }

        if (isScrubbingBar && seekPreviewBitmap != null) {
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = seekPreviewBitmap!!.asImageBitmap(),
                        contentDescription = "Seek Preview",
                        modifier = Modifier.size(160.dp, 90.dp).clip(RoundedCornerShape(4.dp)).padding(bottom = 8.dp),
                        contentScale = ContentScale.Crop
                    )
                    Text("Seek: $scrubBarTimeString", style = TextStyle(brush = FluenSceneGradient), fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = Jakarta)
                }
            }
        }

        gestureIndicatorText?.let { text ->
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (seekPreviewBitmap != null && isHorizontalSeeking) {
                        Image(
                            bitmap = seekPreviewBitmap!!.asImageBitmap(),
                            contentDescription = "Seek Preview",
                            modifier = Modifier.size(160.dp, 90.dp).clip(RoundedCornerShape(4.dp)).padding(bottom = 8.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(text, style = TextStyle(brush = FluenSceneGradient), fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = Jakarta)
                }
            }
        }

        // ------------------------------------------------------------------------------------------------
        // FLUENSCENE AI SUBTITLES & INSIGHT POPUP
        // ------------------------------------------------------------------------------------------------

        val subtitleBottomPadding by animateDpAsState(
            targetValue = if (areControlsVisible && !isInPipMode) 90.dp else 40.dp
        )

        if (currentSubtitleText.isNotEmpty() && aiExplanationText == null && !isInPipMode && isSubtitleEnabled && !areControlsVisible) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = subtitleBottomPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!hasSeenSubTutorial) {
                    Text("Tap subtitles to translate & learn!", color = Color.Yellow, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp), fontFamily = Jakarta)
                }

                Text(
                    text = currentSubtitleText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .background(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .clickable {
                            if (!hasSeenSubTutorial) {
                                UserPreferences.setSubTutorialSeen(context, userId)
                                hasSeenSubTutorial = true
                            }
                            exoPlayer.pause()
                            isThinking = true

                            coroutineScope.launch {
                                try {
                                    val userRef = db.collection("users").document(userId)
                                    val snapshot = userRef.get().await()

                                    val apiCallsUsed = snapshot.getLong("apiCallsUsed") ?: 0
                                    val isPremium = snapshot.getBoolean("isPremium") ?: false
                                    val currentFluency = snapshot.getLong("fluencyLevel") ?: 5
                                    val totalTranslations = snapshot.getLong("totalTranslationsRequested") ?: 0

                                    if (apiCallsUsed >= 30 && !isPremium) {
                                        isThinking = false
                                        showPaymentDialog = true
                                    } else {
                                        aiExplanationText = getAiExplanation(currentSubtitleText, BuildConfig.GROQ_API_KEY, userRegion, currentFluency.toInt())

                                        var newFluency = currentFluency
                                        val newTotal = totalTranslations + 1
                                        if (newTotal % 50 == 0L && newFluency < 10) {
                                            newFluency += 1
                                        }

                                        userRef.update(
                                            "apiCallsUsed", apiCallsUsed + 1,
                                            "totalTranslationsRequested", newTotal,
                                            "fluencyLevel", newFluency
                                        )
                                        isThinking = false
                                    }
                                } catch (e: Exception) {
                                    aiExplanationText = "Network Error communicating with Firebase. Check your connection."
                                    isThinking = false
                                }
                            }
                        }
                )
            }
        }

        if (isThinking && !isInPipMode) {
            Text(
                text = if (isServerWaking) "Waking up secure server... (Up to 60s)" else "FluenScene AI is analyzing...",
                style = TextStyle(brush = FluenSceneGradient),
                fontWeight = FontWeight.Bold,
                fontFamily = Comfortaa,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp)).padding(16.dp)
            )
        }

        if (aiExplanationText != null && !isInPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush = FluenSceneGradient)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0A0A10))
                    .padding(24.dp)
                    .widthIn(max = 400.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())
                ) {
                    Text("FLUENSCENE INSIGHT", style = TextStyle(brush = FluenSceneGradient), fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp, fontFamily = Comfortaa)
                    Spacer(modifier = Modifier.height(12.dp))

                    val cleanAiText = aiExplanationText!!.replace("*", "").replace("`", "").trim()

                    Text(text = cleanAiText, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp), fontFamily = Jakarta)

                    Text("AI-generated explanations can occasionally be inaccurate or inappropriate.", color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp), fontFamily = Jakarta)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            Toast.makeText(context, "Report submitted. Thank you.", Toast.LENGTH_SHORT).show()
                            aiExplanationText = null
                            exoPlayer.play()
                        }) {
                            Icon(Icons.Filled.Flag, contentDescription = "Report AI Response", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Report", color = Color.Gray, fontSize = 12.sp, fontFamily = Jakarta)
                        }

                        Button(onClick = { aiExplanationText = null; exoPlayer.play() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                            Text("Resume Movie", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = Jakarta)
                        }
                    }
                }
            }
        }

        if (showPaymentDialog) {
            PlayBillingDialog(onDismiss = { showPaymentDialog = false; exoPlayer.play() }, uid = userId)
        }
    }
}

@Composable
fun PlayBillingDialog(onDismiss: () -> Unit, uid: String) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    var productDetails by remember { mutableStateOf<ProductDetails?>(null) }
    var billingMessage by remember { mutableStateOf("Connecting to Google Play...") }
    var isProcessing by remember { mutableStateOf(false) }

    val billingClient = remember {
        BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            billingMessage = "Payment Successful! Upgrading account..."
                            isProcessing = true
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    if (!purchase.isAcknowledged) {
                                        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                                            .setPurchaseToken(purchase.purchaseToken)
                                            .build()
                                        BillingClient.newBuilder(context).enablePendingPurchases().build().acknowledgePurchase(acknowledgeParams)
                                    }
                                    db.collection("users").document(uid).update("isPremium", true).await()
                                    withContext(Dispatchers.Main) {
                                        billingMessage = "Welcome to Premium!"
                                        delay(1500)
                                        onDismiss()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        billingMessage = "Upgrade failed to sync. Contact Support."
                                    }
                                }
                            }
                        }
                    }
                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    billingMessage = "Payment cancelled."
                    isProcessing = false
                } else {
                    billingMessage = "Error: ${billingResult.debugMessage}"
                    isProcessing = false
                }
            }
            .enablePendingPurchases()
            .build()
    }

    DisposableEffect(Unit) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingMessage = "Fetching subscription details..."
                    val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                        .setProductList(
                            listOf(
                                QueryProductDetailsParams.Product.newBuilder()
                                    .setProductId("fluenscene_premium_monthly")
                                    .setProductType(BillingClient.ProductType.SUBS)
                                    .build()
                            )
                        )
                        .build()

                    billingClient.queryProductDetailsAsync(queryProductDetailsParams) { result, productDetailsList ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                            productDetails = productDetailsList.first()
                            billingMessage = ""
                        } else {
                            billingMessage = "Subscription not found. Ensure app is uploaded to Internal Testing."
                        }
                    }
                }
            }
            override fun onBillingServiceDisconnected() {
                billingMessage = "Disconnected from Google Play."
            }
        })

        onDispose {
            billingClient.endConnection()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Unlock FluenScene Premium", style = TextStyle(brush = FluenSceneGradient), fontWeight = FontWeight.Bold, fontFamily = Comfortaa) },
        text = {
            Column {
                Text("You've used your 30 free AI explanations! Upgrade to premium for unlimited AI context, dynamic fluency tracking, and advanced translations.", color = Color.White, fontFamily = Jakarta)
                Spacer(modifier = Modifier.height(16.dp))
                if (billingMessage.isNotEmpty()) {
                    Text(text = billingMessage, color = if (billingMessage.contains("Success") || billingMessage.isEmpty()) FluenSceneGreenSolid else Color.Yellow, fontWeight = FontWeight.Bold, fontFamily = Jakarta)
                }
            }
        },
        containerColor = Color(0xFF1E2924),
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                enabled = productDetails != null && !isProcessing,
                modifier = Modifier.background(if (productDetails != null && !isProcessing) FluenSceneGradient else Brush.linearGradient(listOf(Color.DarkGray, Color.DarkGray)), RoundedCornerShape(8.dp)),
                onClick = {
                    productDetails?.let { product ->
                        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
                        if (offerToken != null && activity != null) {
                            val productDetailsParamsList = listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(product)
                                    .setOfferToken(offerToken)
                                    .build()
                            )
                            val billingFlowParams = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(productDetailsParamsList)
                                .build()
                            billingClient.launchBillingFlow(activity, billingFlowParams)
                        }
                    }
                }
            ) {
                Box(modifier = Modifier.padding(16.dp, 8.dp)) {
                    val price = productDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "Loading..."
                    Text(if (isProcessing) "Processing..." else "Subscribe for $price / month", color = Color.Black, fontFamily = Jakarta)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancel", color = Color.Gray, fontFamily = Jakarta) }
        }
    )
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = (totalSeconds / 60) % 60
    val h = (totalSeconds / 3600)
    val s = totalSeconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

fun extractSeekPreviewFrame(positionMs: Long, retriever: MediaMetadataRetriever, scope: kotlinx.coroutines.CoroutineScope, onResult: (Bitmap?) -> Unit) {
    scope.launch(Dispatchers.IO) {
        try {
            val frame = retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            withContext(Dispatchers.Main) { onResult(frame) }
        } catch (e: Exception) { }
    }
}

suspend fun loadVideosFromStorage(context: Context): List<LocalVideo> = withContext(Dispatchers.IO) {
    val list = mutableListOf<LocalVideo>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
    )

    context.contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val folderCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "Unknown"
            val dur = cursor.getLong(durCol)
            val size = cursor.getLong(sizeCol)
            val folder = cursor.getString(folderCol) ?: "Internal Storage"
            val uri = ContentUris.withAppendedId(collection, id)

            val durStr = if (dur > 0) {
                val s = (dur / 1000) % 60
                val m = (dur / (1000 * 60)) % 60
                val h = (dur / (1000 * 60 * 60))
                if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
            } else "00:00"

            val sizeStr = String.format("%.1f MB", size / (1024f * 1024f))
            list.add(LocalVideo(uri, name, durStr, sizeStr, folder, id))
        }
    }
    list
}

@Composable
fun VideoThumbnail(uri: Uri, videoId: Long, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bitmap = context.contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap = MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, videoId, MediaStore.Video.Thumbnails.MINI_KIND, null)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Thumbnail", modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier = modifier.background(Color.DarkGray), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Gray)
        }
    }
}

suspend fun getAiExplanation(subtitle: String, apiKey: String, userRegion: String, fluency: Int): String {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build()

            val url = "https://api.groq.com/openai/v1/chat/completions"
            val jsonPayloadObject = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                put("temperature", 0.3)

                val levelInstruction = when (fluency) {
                    in 1..3 -> "CRITICAL: The user has low fluency. Use extremely basic vocabulary (A1-A2 level), very short sentences, and explain idioms literally. DO NOT overcomplicate."
                    in 4..7 -> "The user has intermediate fluency (B1-B2 level). Provide a clear, natural explanation without being overly simplistic."
                    else -> "The user has high fluency (C1-C2 level). Use advanced vocabulary, nuanced context, and explain the deep cultural or idiomatic structure."
                }

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are an elite English language tutor built into a video player. The user is from $userRegion. Their fluency is $fluency out of 10. $levelInstruction \n\nCRITICAL OUTPUT FORMAT:\n1. English Explanation: Clean, direct context (under 3 short paragraphs).\n2. Native Translation: You MUST conclude with exactly two lines explaining the core meaning in the native regional language of $userRegion using its native script. Label it \"💡 Regional Context:\". Do NOT skip the native script translation.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Explain this subtitle in context: \"$subtitle\"")
                    })
                }
                put("messages", messagesArray)
            }
            val body = jsonPayloadObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder().url(url).addHeader("Authorization", "Bearer $apiKey").post(body).build()
            val response = client.newCall(request).execute()
            val responseData = response.body?.string()

            if (response.isSuccessful && responseData != null) {
                JSONObject(responseData).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } else "Error communicating with context parser code: ${response.code}"
        } catch (e: Exception) {
            "Network error parsing context. Try again."
        }
    }
}

suspend fun generateTextFromGroq(prompt: String): String {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()

        val url = "https://api.groq.com/openai/v1/chat/completions"
        val jsonPayloadObject = JSONObject().apply {
            put("model", "llama-3.1-8b-instant")
            put("temperature", 0.3)
            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messagesArray)
        }
        val body = jsonPayloadObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder().url(url).addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}").post(body).build()

        val response = client.newCall(request).execute()
        val responseData = response.body?.string()

        if (response.isSuccessful && responseData != null) {
            JSONObject(responseData).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } else "[]"
    }
}

object UserPreferences {
    private const val PREFS_NAME = "FluenScenePrefs"

    fun hasSeenSubTutorial(context: Context, userId: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("sub_tutorial_seen_$userId", false)
    }

    fun setSubTutorialSeen(context: Context, userId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("sub_tutorial_seen_$userId", true).apply()
    }

    fun clearLocalData(context: Context, userId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("user_region_$userId")
            .remove("sub_tutorial_seen_$userId")
            .apply()
    }

    fun saveRegion(context: Context, userId: String, region: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("user_region_$userId", region).apply()
    }

    fun getUserRegion(context: Context, userId: String): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("user_region_$userId", "Global / Neutral") ?: "Global / Neutral"
    }

    fun saveVideoPosition(context: Context, videoUri: String, positionMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("pos_$videoUri", positionMs).apply()
    }

    fun getVideoPosition(context: Context, videoUri: String): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong("pos_$videoUri", 0L)
    }
}
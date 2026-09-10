package com.beatnova.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

private val Bg = Color(0xFF08090F)
private val Panel = Color(0xFF12141D)
private val Panel2 = Color(0xFF1B1D28)
private val White = Color(0xFFF8F8FC)
private val Muted = Color(0xFF9699A9)
private val Purple = Color(0xFFC65CFF)
private val Blue = Color(0xFF617CFF)
private val Pink = Color(0xFFFF3E9D)

private data class Song(val id: String, val title: String, val artist: String, val audioUrl: String)

private object SongRepository {
    private val client = OkHttpClient()
    suspend fun load(): Result<List<Song>> = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        val session = supabase.auth.currentSessionOrNull()
        val accessToken = session?.accessToken
        if (base.isBlank() || key.isBlank()) return@withContext Result.failure(Exception("SUPABASE_CONFIG"))
        if (accessToken.isNullOrBlank()) return@withContext Result.failure(Exception("AUTH_REQUIRED"))
        try {
            val request = Request.Builder()
                .url("$base/rest/v1/songs?select=id,title,artist,audio_url&order=created_at.desc")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP_${response.code}"))
                val array = JSONArray(response.body?.string().orEmpty())
                val result = mutableListOf<Song>()
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val audio = o.optString("audio_url")
                    if (audio.isNotBlank()) result += Song(
                        o.optString("id", i.toString()),
                        o.optString("title", "بدون نام"),
                        o.optString("artist", "هنرمند ناشناس"),
                        audio
                    )
                }
                Result.success(result)
            }
        } catch (e: Exception) { Result.failure(e) }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BeatNovaApp() }
    }
}

@Composable
private fun BeatNovaApp() {
    var authenticated by remember { mutableStateOf(supabase.auth.currentSessionOrNull() != null) }
    var checking by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        authenticated = supabase.auth.currentSessionOrNull() != null
        checking = false
    }

    if (checking) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Bg, surface = Panel, onSurface = White)) {
            Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Purple)
            }
        }
    } else if (!authenticated) {
        AuthScreen(onAuthenticated = { authenticated = true })
    } else {
        MainMusicScreen(onSignedOut = { authenticated = false })
    }
}

@Composable
private fun AuthScreen(onAuthenticated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun submit() {
    val normalizedEmail = email.trim()
        .filterNot { it.isWhitespace() || it.category == CharCategory.FORMAT }
        .lowercase()
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
        error = "فرمت ایمیل صحیح نیست. مثلاً: name@gmail.com"
        return
    }
    if (password.length < 6) {
        error = "رمز عبور باید حداقل ۶ کاراکتر باشد."
        return
    }
    scope.launch {
        busy = true
        error = null
        info = null
        try {
            if (isSignUp) {
                val result = AuthHttp.signUp(normalizedEmail, password)
                if (result.sessionImported) onAuthenticated()
                else info = "ثبت‌نام انجام شد. ایمیلت را بررسی و تأیید کن، سپس وارد شو."
            } else {
                AuthHttp.signIn(normalizedEmail, password)
                onAuthenticated()
            }
        } catch (e: Exception) {
            error = e.message ?: "ورود یا ثبت‌نام ناموفق بود."
        } finally {
            busy = false
        }
    }
}

    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Bg, surface = Panel, onSurface = White)) {
        Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BeatNova", color = White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (isSignUp) "ساخت حساب کاربری" else "ورود به BeatNova", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("ایمیل") })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("رمز عبور") })
                Spacer(Modifier.height(18.dp))
                if (error != null) Text(error!!, color = Color(0xFFFF7B7B), fontSize = 12.sp)
                if (info != null) Text(info!!, color = Color(0xFF9BE7B0), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (isSignUp) "ثبت‌نام" else "ورود")
                }
                TextButton(onClick = { isSignUp = !isSignUp; error = null; info = null }) {
                    Text(if (isSignUp) "حساب داری؟ وارد شو" else "حساب نداری؟ ثبت‌نام کن")
                }
            }
        }
    }
}

@Composable
private fun MainMusicScreen(onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var current by remember { mutableStateOf<Song?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(emptySet<String>()) }

    fun refresh() = scope.launch {
        loading = true; error = null
        SongRepository.load().onSuccess { songs = it }.onFailure { error = it.message }
        loading = false
    }
    fun play(song: Song) { current = song; player.setMediaItem(MediaItem.fromUri(song.audioUrl)); player.prepare(); player.play() }
    fun togglePlay() { if (player.isPlaying) player.pause() else if (current != null) player.play() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) playing = false }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(Unit) { refresh() }

    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Bg, surface = Panel, onSurface = White)) {
        Column(Modifier.fillMaxSize().background(Bg)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> HomeScreen(songs, loading, error, current, playing, ::refresh, ::play)
                    1 -> SearchScreen(query, { query = it }, songs.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }, current, ::play)
                    2 -> LibraryScreen(songs.filter { it.id in favorites }, current, ::play)
                    else -> SettingsScreen(onSignOut = { scope.launch { runCatching { supabase.auth.signOut() }; onSignedOut() } })
                }
            }
            current?.let { MiniPlayer(it, playing, ::togglePlay) }
            BottomBar(tab) { tab = it }
        }
    }
}

@Composable
private fun HomeScreen(songs: List<Song>, loading: Boolean, error: String?, current: Song?, playing: Boolean, refresh: () -> Unit, play: (Song) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("موسیقی برای هر لحظه", color = Muted, fontSize = 13.sp); Text("BeatNova", color = White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold) }
                IconButton(onClick = refresh) { Icon(Icons.Default.Refresh, "به‌روزرسانی", tint = White) }
            }
        }
        item { HeroCard(songs.size, current != null && playing) }
        item { SectionHeader("کشف موسیقی", "ویژه شما") }
        item { FeatureCard("ترندهای امروز", "آهنگ‌های جدید و محبوب خودت را پیدا کن", Icons.Default.TrendingUp, Pink) }
        item { FeatureCard("کتابخانه شخصی", "آهنگ‌های مورد علاقه‌ات را یکجا نگه دار", Icons.Default.Favorite, Purple) }
        item { SectionHeader("جدیدترین آهنگ‌ها", if (songs.isEmpty()) "آنلاین" else "${songs.size} آهنگ") }
        when {
            loading -> item { LoadingBox() }
            error != null -> item { EmptyState(if (error == "AUTH_REQUIRED") "نیاز به ورود است" else "اتصال به کتابخانه آماده نیست", if (error == "AUTH_REQUIRED") "دوباره وارد حساب شو." else "Supabase یا جدول songs را بررسی کن", Icons.Default.CloudOff) }
            songs.isEmpty() -> item { EmptyState("هنوز آهنگی اضافه نشده", "آهنگ‌های مجاز خودت را در جدول songs قرار بده.", Icons.Default.LibraryMusic) }
            else -> items(songs.take(30), key = { it.id }) { song -> SongRow(song, current?.id == song.id, play) }
        }
    }
}

@Composable private fun HeroCard(count: Int, isPlaying: Boolean) { Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF32135C), Color(0xFF1B2354), Color(0xFF12131D)))).padding(22.dp)) { Column { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Pink, Purple))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = White, modifier = Modifier.size(34.dp)) }; Spacer(Modifier.width(14.dp)); Column { Text("ONLINE MUSIC", color = Color(0xFFE0B9FF), fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("صدای لحظه‌های تو", color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold) } }; Spacer(Modifier.height(20.dp)); Text("کشف کن • پخش کن • لذت ببر", color = White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(7.dp)); Text("BeatNova یک پخش‌کننده موسیقی ساده و سریع برای کتابخانه مجاز توست.", color = Color(0xFFD1D0DC), fontSize = 13.sp); Spacer(Modifier.height(18.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { BadgePill("$count آهنگ", Icons.Default.QueueMusic); BadgePill(if (isPlaying) "در حال پخش" else "آماده پخش", Icons.Default.PlayArrow) } } } }
@Composable private fun FeatureCard(title: String, subtitle: String, icon: ImageVector, color: Color) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(22.dp)).background(Panel).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(25.dp)) }; Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 2) }; Icon(Icons.Default.ChevronLeft, null, tint = Muted) } }
@Composable private fun BadgePill(text: String, icon: ImageVector) { Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .10f)).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = White, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(text, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) } }
@Composable private fun SectionHeader(title: String, action: String) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(action, color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun SongRow(song: Song, active: Boolean, play: (Song) -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(if (active) Panel2 else Panel).clickable { play(song) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Pink, Purple))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = White) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(song.title, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = Muted, fontSize = 11.sp) }; Icon(Icons.Default.PlayArrow, null, tint = Purple) } }
@Composable private fun LoadingBox() { Box(Modifier.fillMaxWidth().padding(25.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Purple) } }
@Composable private fun EmptyState(title: String, subtitle: String, icon: ImageVector) { Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = Purple, modifier = Modifier.size(46.dp)); Spacer(Modifier.height(12.dp)); Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp) } }
@Composable private fun SearchScreen(query: String, onQuery: (String) -> Unit, songs: List<Song>, current: Song?, play: (Song) -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp)) { Text("جستجو", color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Text("آهنگ یا هنرمند مورد علاقه‌ات را پیدا کن", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(18.dp)); OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("نام آهنگ یا خواننده") }, leadingIcon = { Icon(Icons.Default.Search, null) }); Spacer(Modifier.height(15.dp)); LazyColumn { if (songs.isEmpty()) item { EmptyState("نتیجه‌ای پیدا نشد", "جستجو را تغییر بده یا آهنگ اضافه کن", Icons.Default.SearchOff) } else items(songs, key = { it.id }) { SongRow(it, current?.id == it.id, play) } } } }
@Composable private fun LibraryScreen(songs: List<Song>, current: Song?, play: (Song) -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp)) { Text("کتابخانه من", color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Text("آهنگ‌های مورد علاقه‌ات", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(18.dp)); if (songs.isEmpty()) EmptyState("کتابخانه خالی است", "بعداً آهنگ‌های مورد علاقه‌ات را اینجا می‌بینی", Icons.Default.FavoriteBorder) else LazyColumn { items(songs, key = { it.id }) { SongRow(it, current?.id == it.id, play) } } } }
@Composable private fun SettingsScreen(onSignOut: () -> Unit) { Column(Modifier.fillMaxSize().padding(22.dp)) { Text("تنظیمات", color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(20.dp)); SettingRow("کیفیت پخش", "بهینه برای اینترنت موبایل", Icons.Default.HighQuality); SettingRow("ظاهر برنامه", "تم تیره BeatNova", Icons.Default.DarkMode); SettingRow("درباره BeatNova", "نسخه 1.1.0", Icons.Default.Info); Spacer(Modifier.height(18.dp)); Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A2235))) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("خروج از حساب") } } }
@Composable private fun SettingRow(title: String, subtitle: String, icon: ImageVector) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Purple, modifier = Modifier.size(25.dp)); Spacer(Modifier.width(14.dp)); Column { Text(title, color = White, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp) } } }
@Composable private fun MiniPlayer(song: Song, playing: Boolean, toggle: () -> Unit) { Row(Modifier.fillMaxWidth().background(Color(0xFF171824)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Pink, Purple))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = White) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(song.title, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = Muted, fontSize = 11.sp) }; IconButton(onClick = toggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = White) } } }
@Composable private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) { Row(Modifier.fillMaxWidth().height(76.dp).background(Color(0xFF0E0F16)), verticalAlignment = Alignment.CenterVertically) { BottomItem(0, "خانه", Icons.Default.Home, selected, onSelect); BottomItem(1, "جستجو", Icons.Default.Search, selected, onSelect); BottomItem(2, "کتابخانه", Icons.Default.LibraryMusic, selected, onSelect); BottomItem(3, "تنظیمات", Icons.Default.Settings, selected, onSelect) } }
@Composable private fun RowScope.BottomItem(index: Int, label: String, icon: ImageVector, selected: Int, onSelect: (Int) -> Unit) { val active = index == selected; Box(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(index) }, contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = if (active) Purple else Muted, modifier = Modifier.size(25.dp)); Spacer(Modifier.height(3.dp)); Text(label, color = if (active) White else Muted, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) } } }
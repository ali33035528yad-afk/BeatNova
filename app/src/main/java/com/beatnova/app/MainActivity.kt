package com.beatnova.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF08090F)
private val Panel = Color(0xFF12141D)
private val Panel2 = Color(0xFF1B1D28)
private val White = Color(0xFFF8F8FC)
private val Muted = Color(0xFF9699A9)
private val Purple = Color(0xFFC65CFF)
private val Pink = Color(0xFFFF3E9D)
private typealias Song = BeatNovaSong

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { BeatNovaApp() }
    }
}

@Composable private fun BeatNovaApp() {
    var authenticated by remember { mutableStateOf(supabase.auth.currentSessionOrNull() != null) }
    var checking by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { authenticated = supabase.auth.currentSessionOrNull() != null; checking = false }
    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Bg, surface = Panel, onSurface = White)) {
        when { checking -> Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Purple) }; !authenticated -> AuthScreen { authenticated = true }; else -> MainMusicScreen { authenticated = false } }
    }
}

@Composable private fun AuthScreen(onAuthenticated: () -> Unit) {
    val scope = rememberCoroutineScope(); var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var signUp by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var info by remember { mutableStateOf<String?>(null) }
    fun submit() {
        val e = email.trim().filterNot { it.isWhitespace() || it.category == CharCategory.FORMAT }.lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) { error = "فرمت ایمیل صحیح نیست. مثلاً: name@gmail.com"; return }
        if (password.length < 6) { error = "رمز عبور باید حداقل ۶ کاراکتر باشد."; return }
        scope.launch { busy = true; error = null; info = null; try { if (signUp) { val r = AuthHttp.signUp(e, password); if (r.sessionImported) onAuthenticated() else info = "ثبت‌نام انجام شد؛ ایمیلت را تأیید کن و سپس وارد شو." } else { AuthHttp.signIn(e, password); onAuthenticated() } } catch (x: Exception) { error = x.message ?: "عملیات ناموفق بود." } finally { busy = false } }
    }
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("BeatNova", color = White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold); Text(if (signUp) "ساخت حساب کاربری" else "ورود به BeatNova", color = Muted); Spacer(Modifier.height(28.dp))
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("ایمیل") }); Spacer(Modifier.height(12.dp)); OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("رمز عبور") }); Spacer(Modifier.height(18.dp))
        error?.let { Text(it, color = Color(0xFFFF7B7B), fontSize = 12.sp) }; info?.let { Text(it, color = Color(0xFF9BE7B0), fontSize = 12.sp) }; Spacer(Modifier.height(10.dp))
        Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (signUp) "ثبت‌نام" else "ورود") }
        TextButton(onClick = { signUp = !signUp; error = null; info = null }) { Text(if (signUp) "حساب داری؟ وارد شو" else "حساب نداری؟ ثبت‌نام کن") }
    } }
}

@Composable private fun MainMusicScreen(onSignedOut: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val player = remember { ExoPlayer.Builder(context).build() }
    var tab by remember { mutableIntStateOf(0) }; var songs by remember { mutableStateOf(emptyList<Song>()) }; var current by remember { mutableStateOf<Song?>(null) }; var playing by remember { mutableStateOf(false) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var query by remember { mutableStateOf("") }; var favorites by remember { mutableStateOf(loadFavorites(context)) }; var showPlayer by remember { mutableStateOf(false) }; var showTrending by remember { mutableStateOf(false) }; var position by remember { mutableLongStateOf(0L) }; var duration by remember { mutableLongStateOf(0L) }
    fun refresh() = scope.launch { loading = true; error = null; SongRepository.load().onSuccess { songs = it }.onFailure { error = it.message }; loading = false }
    fun play(song: Song, open: Boolean = false) { current = song; player.setMediaItem(MediaItem.fromUri(song.audioUrl)); player.prepare(); player.play(); showPlayer = showPlayer || open }
    fun toggle() { if (player.isPlaying) player.pause() else current?.let { player.play() } }
    fun next() { if (songs.isEmpty()) return; val i = songs.indexOfFirst { it.id == current?.id }; play(songs[if (i < 0 || i == songs.lastIndex) 0 else i + 1], true) }
    fun previous() { if (songs.isEmpty()) return; if (player.currentPosition > 5000) player.seekTo(0) else { val i = songs.indexOfFirst { it.id == current?.id }; play(songs[if (i <= 0) songs.lastIndex else i - 1], true) } }
    fun favorite(id: String) { favorites = if (id in favorites) favorites - id else favorites + id; saveFavorites(context, favorites) }
    DisposableEffect(player) {
        val session = MediaSession.Builder(context, player).setId("BeatNovaSession").build()
        val manager = PlayerNotificationManager.Builder(context, 1001, "beatnova_playback").setChannelNameResourceId(R.string.notification_channel_name).setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
            override fun getCurrentContentTitle(p: Player) = current?.title ?: "BeatNova"
            override fun createCurrentContentIntent(p: Player) = null
            override fun getCurrentContentText(p: Player) = current?.artist
            override fun getCurrentLargeIcon(p: Player, cb: PlayerNotificationManager.BitmapCallback) = null
        }).setSmallIconResourceId(R.drawable.ic_beatnova).build()
        manager.setMediaSessionToken(session.platformToken); manager.setPlayer(player)
        val listener = object : Player.Listener { override fun onIsPlayingChanged(v: Boolean) { playing = v }; override fun onPlaybackStateChanged(s: Int) { duration = player.duration.coerceAtLeast(0); if (s == Player.STATE_ENDED) next() } }
        player.addListener(listener)
        onDispose { player.removeListener(listener); manager.setPlayer(null); session.release(); player.release() }
    }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(playing, current) { while (true) { position = player.currentPosition.coerceAtLeast(0); duration = player.duration.coerceAtLeast(0); delay(500) } }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                showPlayer && current != null -> FullPlayer(current!!, playing, position, duration, current!!.id in favorites, { showPlayer = false }, ::toggle, ::previous, ::next, { player.seekTo(it) }, ::favorite)
                showTrending -> TrendingScreen(songs, current, { showTrending = false }, ::play, favorites, ::favorite)
                tab == 0 -> HomeScreen(songs, loading, error, current, playing, { showTrending = true }, ::refresh, ::play, favorites, ::favorite)
                tab == 1 -> SearchScreen(query, { query = it }, songs.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }, current, ::play, favorites, ::favorite)
                tab == 2 -> LibraryScreen(songs.filter { it.id in favorites }, current, ::play, favorites, ::favorite)
                else -> SettingsScreen { scope.launch { runCatching { supabase.auth.signOut() }; onSignedOut() } }
            }
        }
        current?.let { MiniPlayer(it, playing, ::toggle) { showPlayer = true } }
        BottomBar(tab) { tab = it }
    }
}

private fun loadFavorites(c: Context): Set<String> = c.getSharedPreferences("beatnova", Context.MODE_PRIVATE).getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
private fun saveFavorites(c: Context, ids: Set<String>) { c.getSharedPreferences("beatnova", Context.MODE_PRIVATE).edit().putStringSet("favorites", ids).apply() }
private fun formatTime(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

@Composable private fun HomeScreen(songs: List<Song>, loading: Boolean, error: String?, current: Song?, playing: Boolean, openTrending: () -> Unit, refresh: () -> Unit, play: (Song) -> Unit, favorites: Set<String>, favorite: (String) -> Unit) { LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
    item { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("موسیقی برای هر لحظه", color = Muted, fontSize = 13.sp); Text("BeatNova", color = White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold) }; IconButton(onClick = refresh) { Icon(Icons.Default.Refresh, null, tint = White) } } }
    item { HeroCard(songs.size, playing) }; item { SectionHeader("کشف موسیقی", "ویژه شما") }; item { FeatureCard("ترندهای امروز", "آهنگ‌های جدید و محبوب را ببین", Icons.Default.TrendingUp, Pink, openTrending) }; item { FeatureCard("کتابخانه شخصی", "آهنگ‌های مورد علاقه‌ات را همیشه نگه دار", Icons.Default.Favorite, Purple, {}) }; item { SectionHeader("جدیدترین آهنگ‌ها", "${songs.size} آهنگ") }
    when { loading -> item { LoadingBox() }; error != null -> item { EmptyState("اتصال آماده نیست", "اتصال Supabase را بررسی کن", Icons.Default.CloudOff) }; songs.isEmpty() -> item { EmptyState("هنوز آهنگی اضافه نشده", "آهنگ مجاز را در جدول songs قرار بده", Icons.Default.LibraryMusic) }; else -> items(songs.take(30), key = { it.id }) { SongRow(it, current?.id == it.id, it.id in favorites, play, favorite) } }
} }

@Composable private fun TrendingScreen(songs: List<Song>, current: Song?, back: () -> Unit, play: (Song) -> Unit, favorites: Set<String>, favorite: (String) -> Unit) { Column(Modifier.fillMaxSize().padding(top = 10.dp)) { Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = White) }; Column(Modifier.weight(1f)) { Text("ترندهای امروز", color = White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text("محبوب‌ترین آهنگ‌های در دسترس BeatNova", color = Muted, fontSize = 12.sp) }; Icon(Icons.Default.Whatshot, null, tint = Pink) }; LazyColumn(contentPadding = PaddingValues(vertical = 10.dp)) { if (songs.isEmpty()) item { EmptyState("ترندی پیدا نشد", "فعلاً آهنگی در کتابخانه نیست", Icons.Default.TrendingUp) } else items(songs.take(20), key = { it.id }) { SongRow(it, current?.id == it.id, it.id in favorites, play, favorite) } } } }

@Composable private fun FullPlayer(song: Song, playing: Boolean, position: Long, duration: Long, favorite: Boolean, close: () -> Unit, toggle: () -> Unit, previous: () -> Unit, next: () -> Unit, seek: (Long) -> Unit, toggleFavorite: (String) -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.Default.KeyboardArrowDown, null, tint = White) }; Text("در حال پخش", color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = { toggleFavorite(song.id) }) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Pink else White) } }; Spacer(Modifier.height(20.dp)); Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Pink, Purple, Color(0xFF25326B)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = White, modifier = Modifier.size(100.dp)) }; Spacer(Modifier.height(22.dp)); Text(song.title, color = White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = Muted); Spacer(Modifier.height(15.dp)); Slider(value = if (duration > 0) position.coerceIn(0, duration).toFloat() / duration else 0f, onValueChange = { seek((it * duration).toLong()) }, modifier = Modifier.fillMaxWidth()); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(position), color = Muted, fontSize = 11.sp); Text(formatTime(duration), color = Muted, fontSize = 11.sp) }; Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = previous) { Icon(Icons.Default.SkipPrevious, null, tint = White, modifier = Modifier.size(38.dp)) }; FilledIconButton(onClick = toggle, modifier = Modifier.size(68.dp)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = White) }; IconButton(onClick = next) { Icon(Icons.Default.SkipNext, null, tint = White, modifier = Modifier.size(38.dp)) } } } }

@Composable private fun SongRow(song: Song, active: Boolean, favorite: Boolean, play: (Song) -> Unit, toggleFavorite: (String) -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(if (active) Panel2 else Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Pink, Purple))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = White) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f).clickable { play(song) }) { Text(song.title, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = Muted, fontSize = 11.sp) }; IconButton(onClick = { toggleFavorite(song.id) }) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Pink else Muted) }; IconButton(onClick = { play(song) }) { Icon(if (active) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Purple) } } }

@Composable private fun SearchScreen(query: String, onQuery: (String) -> Unit, songs: List<Song>, current: Song?, play: (Song) -> Unit, favorites: Set<String>, favorite: (String) -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp)) { Text("جستجو", color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(12.dp)); OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("نام آهنگ یا خواننده") }, leadingIcon = { Icon(Icons.Default.Search, null) }); Spacer(Modifier.height(12.dp)); LazyColumn { if (songs.isEmpty()) item { EmptyState("نتیجه‌ای پیدا نشد", "جستجو را تغییر بده", Icons.Default.SearchOff) } else items(songs, key = { it.id }) { SongRow(it, current?.id == it.id, it.id in favorites, play, favorite) } } } }
@Composable private fun LibraryScreen(songs: List<Song>, current: Song?, play: (Song) -> Unit, favorites: Set<String>, favorite: (String) -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp)) { Text("کتابخانه من", color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Text("ذخیره‌شده روی همین دستگاه", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(14.dp)); if (songs.isEmpty()) EmptyState("کتابخانه خالی است", "روی قلب آهنگ بزن تا اضافه شود", Icons.Default.FavoriteBorder) else LazyColumn { items(songs, key = { it.id }) { SongRow(it, current?.id == it.id, it.id in favorites, play, favorite) } } } }
@Composable private fun SettingsScreen(onSignOut: () -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp)) { Text("تنظیمات", color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(18.dp)); SettingRow("نوار اعلان پخش", "کنترل پخش از اعلان گوشی", Icons.Default.Notifications); SettingRow("ظاهر برنامه", "تم تیره BeatNova", Icons.Default.DarkMode); SettingRow("درباره BeatNova", "نسخه 1.2.0 • پلیر حرفه‌ای", Icons.Default.Info); Spacer(Modifier.height(18.dp)); Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A2235))) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("خروج از حساب") } } }
@Composable private fun SettingRow(title: String, subtitle: String, icon: ImageVector) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Purple); Spacer(Modifier.width(14.dp)); Column { Text(title, color = White, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp) } } }
@Composable private fun MiniPlayer(song: Song, playing: Boolean, toggle: () -> Unit, open: () -> Unit) { Row(Modifier.fillMaxWidth().background(Color(0xFF171824)).clickable { open() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Pink, Purple))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = White) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(song.title, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = Muted, fontSize = 11.sp) }; IconButton(onClick = toggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = White) } } }
@Composable private fun HeroCard(count: Int, playing: Boolean) { Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF32135C), Color(0xFF1B2354), Color(0xFF12131D)))).padding(22.dp)) { Column { Text("ONLINE MUSIC", color = Color(0xFFE0B9FF), fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("صدای لحظه‌های تو", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(8.dp)); Text("کشف کن • پخش کن • لذت ببر", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { BadgePill("$count آهنگ", Icons.Default.QueueMusic); BadgePill(if (playing) "در حال پخش" else "آماده پخش", Icons.Default.PlayArrow) } } } }
@Composable private fun FeatureCard(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(22.dp)).background(Panel).clickable { onClick() }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }; Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(subtitle, color = Muted, fontSize = 11.sp) }; Icon(Icons.Default.ChevronLeft, null, tint = Muted) } }
@Composable private fun BadgePill(text: String, icon: ImageVector) { Row(Modifier.clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = .10f)).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = White, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(text, color = White, fontSize = 11.sp) } }
@Composable private fun SectionHeader(title: String, action: String) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(action, color = Purple, fontSize = 12.sp) } }
@Composable private fun LoadingBox() { Box(Modifier.fillMaxWidth().padding(25.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Purple) } }
@Composable private fun EmptyState(title: String, subtitle: String, icon: ImageVector) { Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = Purple, Modifier.size(46.dp)); Spacer(Modifier.height(12.dp)); Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp) } }
@Composable private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) { Row(Modifier.fillMaxWidth().height(76.dp).background(Color(0xFF0E0F16)), verticalAlignment = Alignment.CenterVertically) { BottomItem(0,"خانه",Icons.Default.Home,selected,onSelect); BottomItem(1,"جستجو",Icons.Default.Search,selected,onSelect); BottomItem(2,"کتابخانه",Icons.Default.LibraryMusic,selected,onSelect); BottomItem(3,"تنظیمات",Icons.Default.Settings,selected,onSelect) } }
@Composable private fun RowScope.BottomItem(i: Int, label: String, icon: ImageVector, selected: Int, onSelect: (Int) -> Unit) { val active=i==selected; Box(Modifier.weight(1f).fillMaxHeight().clickable{onSelect(i)}, contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Icon(icon,null,tint=if(active)Purple else Muted,Modifier.size(25.dp)); Spacer(Modifier.height(3.dp)); Text(label,color=if(active)White else Muted,fontSize=11.sp) } } }

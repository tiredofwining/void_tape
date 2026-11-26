package com.voidtape.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class Track(val id: Long, val title: String, val artist: String, val uri: android.net.Uri)

class MainActivity : AppCompatActivity() {
    private val lime = Color.rgb(168, 255, 96)
    private val bg = Color.rgb(12, 13, 12)
    private val panel = Color.rgb(25, 27, 24)
    private lateinit var list: LinearLayout
    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var playButton: Button
    private lateinit var status: TextView
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val tracks = mutableListOf<Track>()
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) loadTracks() else status.text = "Нужен доступ к аудиофайлам на устройстве."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        connectController()
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadTracks()
        else permissionLauncher.launch(permission)
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18))
        }
        val brand = TextView(this).apply {
            text = "VOID TAPE  /  OFFLINE"
            textSize = 12f
            letterSpacing = .18f
            setTextColor(lime)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        outer.addView(brand, LinearLayout.LayoutParams(-1, dp(36)))
        outer.addView(TextView(this).apply {
            text = "ТВОЯ МУЗЫКА.\nБЕЗ СЕТИ."
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(100)))
        status = TextView(this).apply { text = "Ищу музыку на устройстве…"; textSize = 13f; setTextColor(0xFF999E94.toInt()) }
        outer.addView(status, LinearLayout.LayoutParams(-1, dp(36)))
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val playerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(14))
        }
        nowTitle = TextView(this).apply { text = "Ничего не играет"; textSize = 16f; setTextColor(Color.WHITE); maxLines = 1 }
        nowArtist = TextView(this).apply { text = "Выбери трек из библиотеки"; textSize = 12f; setTextColor(0xFF9DA398.toInt()); maxLines = 1 }
        playerPanel.addView(nowTitle)
        playerPanel.addView(nowArtist, LinearLayout.LayoutParams(-1, dp(26)))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        val prev = button("◀") { controller?.seekToPreviousMediaItem() }
        playButton = button("▶") { togglePlayback() }
        val next = button("▶▶") { controller?.seekToNextMediaItem() }
        controls.addView(prev, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(playButton, LinearLayout.LayoutParams(0, dp(48), 1.3f))
        controls.addView(next, LinearLayout.LayoutParams(0, dp(48), 1f))
        playerPanel.addView(controls)
        outer.addView(playerPanel, LinearLayout.LayoutParams(-1, dp(142)))
        setContentView(outer)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 17f
        setTextColor(lime)
        setBackgroundColor(panel)
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateNowPlaying()
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playButton.text = if (isPlaying) "Ⅱ" else "▶" }
                })
                updateNowPlaying()
            } catch (_: Exception) { status.text = "Не удалось запустить плеер." }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadTracks() {
        tracks.clear()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        try {
            contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c: Cursor ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    tracks.add(Track(id, c.getString(titleCol) ?: "Без названия", c.getString(artistCol) ?: "Неизвестный исполнитель", uri))
                }
            }
        } catch (_: Exception) {}
        list.removeAllViews()
        status.text = if (tracks.isEmpty()) "Музыка не найдена. Закинь аудиофайлы на телефон." else "ЛОКАЛЬНАЯ БИБЛИОТЕКА  •  ${tracks.size} ТРЕКОВ"
        tracks.forEachIndexed { index, track ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12))
                setBackgroundColor(if (index % 2 == 0) panel else bg)
                isClickable = true
                isFocusable = true
                setOnClickListener { playTrack(index) }
            }
            row.addView(TextView(this).apply {
                text = track.title
                textSize = 15f
                setTextColor(Color.WHITE)
                maxLines = 1
            })
            row.addView(TextView(this).apply {
                text = track.artist
                textSize = 12f
                setTextColor(0xFF9DA398.toInt())
                maxLines = 1
            })
            list.addView(row, LinearLayout.LayoutParams(-1, dp(64)))
            val divider = View(this).apply { setBackgroundColor(0xFF33372F.toInt()) }
            list.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }

    private fun playTrack(index: Int) {
        val c = controller ?: run { status.text = "Плеер запускается, попробуй ещё раз через секунду."; return }
        val items = tracks.map { MediaItem.fromUri(it.uri) }
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
        updateNowPlaying()
    }

    private fun togglePlayback() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.mediaItemCount == 0 && tracks.isNotEmpty()) playTrack(0) else c.play()
        }
        playButton.text = if (c.isPlaying) "Ⅱ" else "▶"
    }

    private fun updateNowPlaying() {
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        if (index in tracks.indices) {
            nowTitle.text = tracks[index].title
            nowArtist.text = tracks[index].artist
        }
        playButton.text = if (c.isPlaying) "Ⅱ" else "▶"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        executor.shutdownNow()
        super.onDestroy()
    }
}

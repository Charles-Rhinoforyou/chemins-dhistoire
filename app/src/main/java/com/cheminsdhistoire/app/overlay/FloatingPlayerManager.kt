package com.cheminsdhistoire.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.cheminsdhistoire.app.MainActivity
import com.cheminsdhistoire.app.model.PlaybackState
import com.cheminsdhistoire.app.model.PlayerUiState
import com.cheminsdhistoire.app.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mini-lecteur flottant façon YouTube : une petite fenêtre superposée
 * (TYPE_APPLICATION_OVERLAY), DÉPLAÇABLE (glisser la barre de titre) et
 * REDIMENSIONNABLE (coin bas-droit), visible par-dessus n'importe quelle
 * application — y compris un GPS de navigation.
 *
 * Deux modes en arrière-plan : fenêtre flottante ([show]) ou invisible ([hide],
 * l'audio continue via le service de premier plan).
 */
object FloatingPlayerManager {

    private var wm: WindowManager? = null
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null

    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var playPause: ImageButton? = null

    val isShowing: Boolean get() = view != null

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun toggle(context: Context) {
        if (isShowing) hide() else show(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context) {
        val app = context.applicationContext
        if (!canDraw(app) || view != null) return
        val windowManager = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val density = app.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        // --- Construction programmatique de la fenêtre (pas de layout XML) ---
        val root = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#F2262019"))
                setStroke(dp(1), Color.parseColor("#D9A441"))
            }
        }

        val topRow = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(app).apply {
            text = "Chemins d'Histoire"
            setTextColor(Color.parseColor("#D9A441"))
            textSize = 13f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val expandBtn = ImageButton(app).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            background = null
            setColorFilter(Color.parseColor("#F2E9D8"))
            setOnClickListener { openApp(app) }
        }
        val closeBtn = ImageButton(app).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            setColorFilter(Color.parseColor("#F2E9D8"))
            setOnClickListener { hide() } // passe en mode invisible (audio continue)
        }
        topRow.addView(title)
        topRow.addView(expandBtn)
        topRow.addView(closeBtn)

        val subtitle = TextView(app).apply {
            text = "…"
            setTextColor(Color.parseColor("#CFC1A8"))
            textSize = 11f
            maxLines = 2
            setPadding(0, dp(2), 0, dp(4))
        }

        val controls = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val playBtn = ImageButton(app).apply {
            setImageResource(android.R.drawable.ic_media_play)
            background = null
            setColorFilter(Color.parseColor("#F2E9D8"))
            setOnClickListener {
                val s = PlaybackController.state.value
                if (s.playbackState == PlaybackState.SPEAKING) PlaybackController.pause()
                else PlaybackController.resume()
            }
        }
        val nextBtn = ImageButton(app).apply {
            setImageResource(android.R.drawable.ic_media_next)
            background = null
            setColorFilter(Color.parseColor("#F2E9D8"))
            setOnClickListener { PlaybackController.skip() }
        }
        val resizeHandle = View(app).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#D9A441"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                leftMargin = dp(10)
            }
        }
        controls.addView(playBtn)
        controls.addView(nextBtn)
        val spacer = View(app).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
        }
        controls.addView(spacer)
        controls.addView(resizeHandle)

        root.addView(topRow)
        root.addView(subtitle)
        root.addView(controls)

        val lp = WindowManager.LayoutParams(
            dp(240),
            dp(140),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(120)
        }

        // Déplacement : glisser la barre de titre.
        topRow.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x; startY = lp.y
                        touchX = e.rawX; touchY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = startX + (e.rawX - touchX).toInt()
                        lp.y = startY + (e.rawY - touchY).toInt()
                        runCatching { windowManager.updateViewLayout(root, lp) }
                    }
                }
                return false
            }
        })

        // Redimensionnement : glisser le coin bas-droit.
        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var startW = 0
            private var startH = 0
            private var touchX = 0f
            private var touchY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startW = lp.width; startH = lp.height
                        touchX = e.rawX; touchY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.width = (startW + (e.rawX - touchX).toInt()).coerceIn(dp(160), dp(400))
                        lp.height = (startH + (e.rawY - touchY).toInt()).coerceIn(dp(110), dp(360))
                        runCatching { windowManager.updateViewLayout(root, lp) }
                    }
                }
                return true
            }
        })

        windowManager.addView(root, lp)
        wm = windowManager
        view = root
        params = lp
        titleView = title
        subtitleView = subtitle
        playPause = playBtn

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch { PlaybackController.state.collect { render(it) } }
    }

    fun hide() {
        scope?.cancel()
        scope = null
        val v = view
        val w = wm
        if (v != null && w != null) runCatching { w.removeView(v) }
        view = null
        params = null
        titleView = null
        subtitleView = null
        playPause = null
    }

    private fun render(state: PlayerUiState) {
        titleView?.text = state.currentStory?.title ?: "Chemins d'Histoire"
        val sub = when (state.playbackState) {
            PlaybackState.SPEAKING ->
                state.currentStory?.segments?.getOrNull(state.currentSegmentIndex) ?: "Lecture…"
            PlaybackState.GENERATING -> "Écriture du récit…"
            PlaybackState.SEARCHING -> "Recherche de lieux…"
            PlaybackState.PAUSED -> "En pause"
            else -> "En attente"
        }
        subtitleView?.text = sub
        playPause?.setImageResource(
            if (state.playbackState == PlaybackState.SPEAKING)
                android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun openApp(context: Context) {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching { context.startActivity(i) }
    }
}

package com.cleanhome.proximus

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private data class TvApp(
    val label: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable,
    val category: AppCategory,
    val tvCompatible: Boolean
)

private enum class AppCategory(val title: String) {
    ALL("Accueil"), FAVORITES("Ma liste"), VIDEO("Films et séries"),
    MUSIC("Musique"), GAMES("Jeux"), OTHER("Applications")
}

class MainActivity : Activity() {
    private val bg = Color.rgb(7, 9, 17)
    private val panel = Color.rgb(19, 23, 35)
    private val panelFocus = Color.rgb(42, 48, 66)
    private val white = Color.WHITE
    private val secondary = Color.rgb(166, 174, 190)
    private val blue = Color.rgb(105, 166, 255)
    private val prefs by lazy { getSharedPreferences("vanta_settings", MODE_PRIVATE) }
    private val favorites = linkedSetOf<String>()
    private val recent = mutableListOf<String>()
    private var apps: List<TvApp> = emptyList()
    private var selectedCategory = AppCategory.ALL
    private var searchQuery = ""
    private lateinit var root: LinearLayout
    private lateinit var mainScroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var clock: TextView
    private lateinit var navButtons: Map<AppCategory, TextView>
    private val clockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            if (::clock.isInitialized) clock.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            clockHandler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        favorites.addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
        recent.addAll(prefs.getString("recent", "").orEmpty().split("|").filter { it.isNotBlank() })
        apps = discoverApps()
        buildInterface()
        clockHandler.post(clockTick)
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockTick)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) {
            apps = discoverApps()
            renderHome()
        }
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
            setPadding(dp(34), dp(24), dp(34), dp(18))
        }

        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        sidebar.addView(TextView(this).apply {
            text = "V."
            textSize = 30f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(white)
            gravity = Gravity.CENTER
            letterSpacing = -0.06f
        }, LinearLayout.LayoutParams(-1, dp(58)))
        sidebar.addView(TextView(this).apply {
            text = "VANTA"
            textSize = 10f
            letterSpacing = 0.22f
            gravity = Gravity.CENTER
            setTextColor(secondary)
            setPadding(0, 0, 0, dp(34))
        })
        val buttons = linkedMapOf<AppCategory, TextView>()
        AppCategory.entries.forEach { category ->
            val item = TextView(this).apply {
                text = when (category) {
                    AppCategory.ALL -> "⌂"
                    AppCategory.FAVORITES -> "♡"
                    AppCategory.VIDEO -> "▣"
                    AppCategory.MUSIC -> "♫"
                    AppCategory.GAMES -> "⌘"
                    AppCategory.OTHER -> "▦"
                }
                textSize = 25f
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                contentDescription = category.title
                background = rounded(Color.TRANSPARENT, 18)
                setTextColor(secondary)
                setOnClickListener { selectCategory(category) }
                setOnFocusChangeListener { v, focused ->
                    v.background = rounded(if (focused || selectedCategory == category) panelFocus else Color.TRANSPARENT, 18)
                    (v as TextView).setTextColor(if (focused || selectedCategory == category) white else secondary)
                    if (focused) selectCategory(category)
                }
                setOnKeyListener { _, key, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        firstContentFocus()
                        true
                    } else false
                }
            }
            buttons[category] = item
            sidebar.addView(item, LinearLayout.LayoutParams(dp(58), dp(54)).apply { bottomMargin = dp(8) })
        }
        navButtons = buttons
        sidebar.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        sidebar.addView(TextView(this).apply {
            text = "●"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(90, 196, 154))
        }, LinearLayout.LayoutParams(dp(58), dp(40)))
        root.addView(sidebar, LinearLayout.LayoutParams(dp(78), -1))

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(10), dp(14))
        }
        header.addView(TextView(this).apply {
            text = when (selectedCategory) {
                AppCategory.ALL -> "Regarder"
                else -> selectedCategory.title
            }
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(white)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        search = EditText(this).apply {
            hint = "Rechercher"
            textSize = 13f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(secondary)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(panel, dp(20))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.trim().orEmpty()
                    renderHome()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        header.addView(search, LinearLayout.LayoutParams(dp(210), dp(42)))
        clock = TextView(this).apply {
            textSize = 16f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(20), 0, dp(8), 0)
        }
        header.addView(clock)
        right.addView(header)

        mainScroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(12), dp(30))
        }
        mainScroll.addView(content)
        right.addView(mainScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        setContentView(root)
        renderHome()
        navButtons[AppCategory.ALL]?.requestFocus()
    }

    private fun selectCategory(category: AppCategory) {
        if (selectedCategory == category && content.childCount > 0) return
        selectedCategory = category
        navButtons.forEach { (key, view) ->
            view.background = rounded(if (key == category) panelFocus else Color.TRANSPARENT, 18)
            view.setTextColor(if (key == category) white else secondary)
        }
        renderHome()
    }

    private fun renderHome() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        if (searchQuery.isBlank() && selectedCategory == AppCategory.ALL) {
            content.addView(buildHero(), LinearLayout.LayoutParams(-1, dp(290)).apply { bottomMargin = dp(26) })
            val recently = recent.mapNotNull { key -> apps.firstOrNull { it.packageName == key } }.distinctBy { it.packageName }
            if (recently.isNotEmpty()) addRail("Reprendre", recently)
            val favs = apps.filter { favorites.contains(it.packageName) }
            if (favs.isNotEmpty()) addRail("Ma liste", favs)
            addRail("Vos applications", apps)
            addRail("Films et séries", apps.filter { it.category == AppCategory.VIDEO })
            addRail("Jeux", apps.filter { it.category == AppCategory.GAMES })
            addRail("Musique", apps.filter { it.category == AppCategory.MUSIC })
            addRail("Autres applications", apps.filter { it.category == AppCategory.OTHER })
        } else {
            val filtered = when (selectedCategory) {
                AppCategory.ALL -> apps
                AppCategory.FAVORITES -> apps.filter { favorites.contains(it.packageName) }
                else -> apps.filter { it.category == selectedCategory }
            }.filter { searchQuery.isBlank() || it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
            content.addView(TextView(this).apply {
                text = if (searchQuery.isNotBlank()) "Résultats de recherche" else selectedCategory.title
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
                setPadding(dp(4), dp(12), 0, dp(16))
            })
            addRail("", filtered)
            if (filtered.isEmpty()) content.addView(TextView(this).apply {
                text = "Aucune application trouvée"
                textSize = 15f
                setTextColor(secondary)
                setPadding(dp(6), dp(24), 0, 0)
            })
        }
    }

    private fun buildHero(): View {
        val hero = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(37, 57, 91), Color.rgb(24, 37, 62), Color.rgb(13, 19, 32))).apply {
                cornerRadius = dp(22).toFloat()
            }
            clipToOutline = true
            setPadding(dp(34), dp(26), dp(30), dp(24))
        }
        val glow = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(100, 83, 143, 230), Color.argb(8, 80, 120, 210))).apply {
                shape = GradientDrawable.OVAL
            }
        }
        hero.addView(glow, FrameLayout.LayoutParams(dp(360), dp(360), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(-70)
        })
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        copy.addView(TextView(this).apply {
            text = "VANTA  •  ESPACE DE DIVERTISSEMENT"
            textSize = 10f
            letterSpacing = 0.18f
            setTextColor(Color.rgb(190, 211, 245))
            typeface = Typeface.DEFAULT_BOLD
        })
        copy.addView(TextView(this).apply {
            text = "Tout votre\n divertissement.\nAu même endroit."
            textSize = 31f
            setLineSpacing(dp(1).toFloat(), 1.0f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(white)
            setPadding(0, dp(10), 0, dp(8))
        })
        copy.addView(TextView(this).apply {
            text = "Retrouvez vos applications et lancez Proximus TV en un instant."
            textSize = 13f
            setTextColor(Color.rgb(206, 215, 230))
            maxLines = 2
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, 0)
        }
        actions.addView(actionButton("▶   Regarder la TV", true) { openProximus() },
            LinearLayout.LayoutParams(-2, dp(46)).apply { marginEnd = dp(12) })
        actions.addView(actionButton("Explorer les apps", false) {
            selectCategory(AppCategory.OTHER)
        }, LinearLayout.LayoutParams(-2, dp(46)))
        copy.addView(actions)
        hero.addView(copy, FrameLayout.LayoutParams(dp(650), -1, Gravity.LEFT or Gravity.CENTER_VERTICAL))
        hero.isFocusable = false
        return hero
    }

    private fun addRail(title: String, items: List<TvApp>) {
        if (items.isEmpty()) return
        if (title.isNotBlank()) content.addView(TextView(this).apply {
            text = title
            textSize = 19f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(white)
            setPadding(dp(4), dp(3), 0, dp(12))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = false
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { app -> row.addView(appCard(app), LinearLayout.LayoutParams(dp(206), dp(158)).apply {
            marginEnd = dp(14)
            bottomMargin = dp(19)
        }) }
        horizontal.addView(row)
        content.addView(horizontal, LinearLayout.LayoutParams(-1, dp(174)))
    }

    private fun appCard(app: TvApp): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            background = rounded(panel, dp(15))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            contentDescription = "Ouvrir ${app.label}. Appui long pour ajouter ou retirer des favoris."
        }
        val art = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(37, 47, 67), Color.rgb(20, 27, 42))).apply { cornerRadius = dp(10).toFloat() }
        }
        art.addView(ImageView(this).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(22), dp(10), dp(22), dp(10))
        }, FrameLayout.LayoutParams(-1, -1))
        card.addView(art, LinearLayout.LayoutParams(-1, dp(94)))
        card.addView(TextView(this).apply {
            text = app.label
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(white)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        card.setOnClickListener { launchApp(app.packageName) }
        card.setOnLongClickListener {
            if (!favorites.add(app.packageName)) favorites.remove(app.packageName)
            prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
            Toast.makeText(this, if (favorites.contains(app.packageName)) "Ajouté à Ma liste" else "Retiré de Ma liste", Toast.LENGTH_SHORT).show()
            renderHome()
            true
        }
        card.setOnFocusChangeListener { view, focused ->
            view.background = rounded(if (focused) panelFocus else panel, dp(15))
            view.animate().scaleX(if (focused) 1.07f else 1f).scaleY(if (focused) 1.07f else 1f)
                .setDuration(170).start()
            view.elevation = if (focused) dp(12).toFloat() else 0f
            if (focused) view.parent?.let { parent ->
                (parent as? HorizontalScrollView)?.smoothScrollTo(view.left - dp(24), 0)
            }
        }
        card.setOnKeyListener { _, key, event ->
            if (event.action == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_UP) {
                mainScroll.smoothScrollBy(0, -dp(220))
                false
            } else false
        }
        return card
    }

    private fun firstContentFocus() {
        val scroll = content.getChildAt(1) as? HorizontalScrollView
        val row = scroll?.getChildAt(0) as? ViewGroup
        row?.getChildAt(0)?.requestFocus() ?: search.requestFocus()
    }

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            setTextColor(if (primary) Color.rgb(10, 18, 30) else white)
            background = rounded(if (primary) white else Color.argb(55, 255, 255, 255), dp(24))
            isFocusable = true
            isClickable = true
            setOnClickListener { action() }
            setOnFocusChangeListener { v, focused ->
                v.scaleX = if (focused) 1.04f else 1f
                v.scaleY = if (focused) 1.04f else 1f
                v.elevation = if (focused) dp(8).toFloat() else 0f
            }
        }

    private fun discoverApps(): List<TvApp> {
        val found = linkedMapOf<String, TvApp>()
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )
        intents.forEach { intent ->
            val results = if (Build.VERSION.SDK_INT >= 33) packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            val tv = intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            results.forEach resultLoop@{ info ->
                val pkg = info.activityInfo?.packageName ?: return@resultLoop
                if (pkg == packageName) return@resultLoop
                if (pkg.lowercase().contains("youtube") && !pkg.lowercase().contains("smarttube")) return@resultLoop
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@resultLoop
                val icon = runCatching { info.loadIcon(packageManager) }.getOrNull() ?: return@resultLoop
                val old = found[pkg]
                if (old == null) found[pkg] = TvApp(label, pkg, icon, categorize(pkg, label), tv)
                else if (tv && !old.tvCompatible) found[pkg] = old.copy(tvCompatible = true)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    private fun categorize(pkg: String, label: String): AppCategory {
        val value = "$pkg $label".lowercase()
        return when {
            listOf("youtube", "netflix", "disney", "crunchy", "primevideo", "plex", "twitch", "auvio", "rtl", "tvi", "f1", "stb", "proximus", "vlc", "tvbro", "smarttube").any(value::contains) -> AppCategory.VIDEO
            listOf("spotify", "music", "deezer", "soundcloud", "radio").any(value::contains) -> AppCategory.MUSIC
            listOf("game", "geforce", "steam", "xbox", "retroarch", "emulator", "bombsquad", "sonic").any(value::contains) -> AppCategory.GAMES
            else -> AppCategory.OTHER
        }
    }

    private fun openProximus() {
        val intent = Intent().setClassName("be.px.stbtvclient", "tv.threess.threeready.ui.generic.activity.MainActivity")
        runCatching {
            startActivity(intent)
            rememberRecent("be.px.stbtvclient")
        }.onFailure {
            val fallback = packageManager.getLaunchIntentForPackage("be.px.stbtvclient")
            if (fallback != null) runCatching { startActivity(fallback); rememberRecent("be.px.stbtvclient") }
            else Toast.makeText(this, "Proximus TV est introuvable. Ne désinstallez pas l'application Proximus.", Toast.LENGTH_LONG).show()
        }
    }

    private fun rememberRecent(pkg: String) {
        recent.remove(pkg)
        recent.add(0, pkg)
        while (recent.size > 10) recent.removeAt(recent.lastIndex)
        prefs.edit().putString("recent", recent.joinToString("|")).apply()
    }

    private fun launchApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "Application non lançable", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { startActivity(intent); rememberRecent(pkg) }
            .onFailure { Toast.makeText(this, "Impossible d'ouvrir cette application", Toast.LENGTH_SHORT).show() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_GUIDE) {
            openProximus()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && searchQuery.isNotBlank()) {
            search.setText("")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && selectedCategory != AppCategory.ALL) {
            selectCategory(AppCategory.ALL)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

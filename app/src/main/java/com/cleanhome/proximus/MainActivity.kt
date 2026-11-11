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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TvApp(
    val label: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable,
    val category: AppCategory,
    val tvCompatible: Boolean
)

private enum class AppCategory(val title: String, val symbol: String) {
    ALL("Accueil", "⌂"),
    FAVORITES("Ma liste", "♡"),
    VIDEO("Films et séries", "▣"),
    MUSIC("Musique", "♫"),
    GAMES("Jeux", "⌘"),
    OTHER("Applications", "▦")
}

class MainActivity : Activity() {
    private val backgroundColor = Color.rgb(7, 9, 16)
    private val surfaceColor = Color.rgb(21, 25, 36)
    private val focusedColor = Color.rgb(47, 55, 73)
    private val primaryText = Color.rgb(250, 251, 255)
    private val mutedText = Color.rgb(165, 174, 192)
    private val accent = Color.rgb(115, 170, 255)

    private val prefs by lazy { getSharedPreferences("vanta_settings", MODE_PRIVATE) }
    private val favorites = linkedSetOf<String>()
    private val recent = mutableListOf<String>()
    private var apps: List<TvApp> = emptyList()
    private var selectedCategory = AppCategory.ALL
    private var searchQuery = ""
    private var firstResume = true

    private lateinit var mainScroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var sidebar: LinearLayout
    private lateinit var navButtons: Map<AppCategory, TextView>
    private lateinit var navScroll: HorizontalScrollView

    private val clockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            if (::clock.isInitialized) clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            if (::date.isInitialized) date.text = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        favorites.addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
        recent.addAll(prefs.getString("recent", "").orEmpty().split("|").filter { it.isNotBlank() })
        apps = discoverApps()
        buildInterface()
        clockHandler.post(clockTick)
    }

    override fun onResume() {
        super.onResume()
        if (firstResume) {
            firstResume = false
            return
        }
        if (::content.isInitialized) {
            val oldFocus = focusedPackage()
            apps = discoverApps()
            renderHome()
            if (oldFocus != null) focusPackage(oldFocus)
        }
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockTick)
        super.onDestroy()
    }

    private fun buildInterface() {
        // Apple TV-inspired layout: top navigation, immersive featured area and horizontal content rails.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(42), dp(18), dp(42), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(14))
        }
        header.addView(TextView(this).apply {
            text = "VANTA"
            textSize = 23f
            letterSpacing = 0.16f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(primaryText)
        }, LinearLayout.LayoutParams(dp(125), dp(48)))

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val buttons = linkedMapOf<AppCategory, TextView>()
        AppCategory.values().forEach { category ->
            val item = TextView(this).apply {
                text = category.title
                textSize = 13f
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                contentDescription = category.title
                setPadding(dp(15), dp(10), dp(15), dp(10))
                background = rounded(Color.TRANSPARENT, dp(18))
                setTextColor(if (category == selectedCategory) primaryText else mutedText)
                setOnClickListener { selectCategory(category) }
                setOnFocusChangeListener { view, focused ->
                    view.background = rounded(if (focused) Color.rgb(48, 56, 73) else if (selectedCategory == category) Color.rgb(31, 37, 50) else Color.TRANSPARENT, dp(18))
                    (view as TextView).setTextColor(if (focused || selectedCategory == category) primaryText else mutedText)
                    if (focused && selectedCategory != category) selectCategory(category)
                }
            }
            buttons[category] = item
            tabs.addView(item, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(3) })
        }
        navButtons = buttons
        navScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
            clipToPadding = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(tabs, ViewGroup.LayoutParams(-2, -2))
        }
        header.addView(navScroll, LinearLayout.LayoutParams(0, dp(48), 1f))

        search = EditText(this).apply {
            hint = "⌕  Rechercher"
            textSize = 13f
            setSingleLine(true)
            setTextColor(primaryText)
            setHintTextColor(mutedText)
            setPadding(dp(15), 0, dp(15), 0)
            background = rounded(surfaceColor, dp(22))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.trim().orEmpty()
                    renderHome()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        header.addView(search, LinearLayout.LayoutParams(dp(190), dp(40)).apply { marginEnd = dp(18) })

        val timeColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        }
        clock = TextView(this).apply {
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primaryText)
            gravity = Gravity.RIGHT
        }
        date = TextView(this).apply {
            textSize = 10f
            setTextColor(mutedText)
            gravity = Gravity.RIGHT
        }
        timeColumn.addView(clock)
        timeColumn.addView(date)
        header.addView(timeColumn, LinearLayout.LayoutParams(dp(112), -2))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(58)))

        mainScroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(3), dp(12), dp(40))
        }
        mainScroll.addView(content)
        root.addView(mainScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderHome()
        navButtons[AppCategory.ALL]?.requestFocus()
    }

    private fun selectCategory(category: AppCategory) {
        selectedCategory = category
        navButtons.forEach { (key, view) ->
            view.background = rounded(if (key == category) focusedColor else Color.TRANSPARENT, dp(16))
            view.setTextColor(if (key == category) primaryText else mutedText)
        }
        renderHome()
        navScroll.post { navButtons[category]?.let { navScroll.smoothScrollTo(it.left - dp(12), 0) } }
    }

    private fun renderHome() {
        if (!::content.isInitialized) return
        val previousY = mainScroll.scrollY
        content.removeAllViews()

        if (searchQuery.isBlank() && selectedCategory == AppCategory.ALL) {
            content.addView(buildHero(), LinearLayout.LayoutParams(-1, dp(340)).apply {
                bottomMargin = dp(25)
            })
            val recentlyUsed = recent.mapNotNull { key -> apps.firstOrNull { it.packageName == key } }
                .distinctBy { it.packageName }
            if (recentlyUsed.isNotEmpty()) addRail("Continuer", "Reprenez là où vous en étiez", recentlyUsed)
            val favoriteApps = apps.filter { favorites.contains(it.packageName) }
            if (favoriteApps.isNotEmpty()) addRail("Ma liste", "Vos applications préférées", favoriteApps)
            addRail("Vos applications", "Tout votre divertissement", apps)
            addRail("Films et séries", "Vos plateformes vidéo", apps.filter { it.category == AppCategory.VIDEO })
            addRail("Jeux", "Jouez sur grand écran", apps.filter { it.category == AppCategory.GAMES })
            addRail("Musique", "Écoutez vos playlists", apps.filter { it.category == AppCategory.MUSIC })
            addRail("Autres applications", "Toutes les autres applications", apps.filter { it.category == AppCategory.OTHER })
        } else {
            val base = when (selectedCategory) {
                AppCategory.ALL, AppCategory.OTHER -> apps
                AppCategory.FAVORITES -> apps.filter { favorites.contains(it.packageName) }
                else -> apps.filter { it.category == selectedCategory }
            }
            val filtered = base.filter {
                searchQuery.isBlank() || it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true)
            }
            content.addView(TextView(this).apply {
                text = if (searchQuery.isNotBlank()) "Résultats" else selectedCategory.title
                textSize = 23f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(primaryText)
                setPadding(dp(4), dp(10), 0, dp(16))
            })
            addGrid(filtered)
            if (filtered.isEmpty()) content.addView(TextView(this).apply {
                text = "Aucune application trouvée"
                textSize = 15f
                setTextColor(mutedText)
                setPadding(dp(6), dp(25), 0, 0)
            })
        }
        if (previousY > 0) mainScroll.post { mainScroll.scrollTo(0, previousY.coerceAtMost(content.height)) }
    }

    private fun addGrid(items: List<TvApp>) {
        if (items.isEmpty()) return
        val availableWidth = (resources.configuration.screenWidthDp - 110).coerceAtLeast(320)
        val columns = when {
            availableWidth >= 1350 -> 4
            availableWidth >= 900 -> 3
            availableWidth >= 580 -> 2
            else -> 1
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), dp(8), dp(24))
        }
        items.chunked(columns).forEachIndexed { rowIndex, rowApps ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            rowApps.forEach { app ->
                row.addView(appCard(app, false), LinearLayout.LayoutParams(0, dp(172), 1f).apply {
                    marginEnd = dp(14)
                })
            }
            repeat(columns - rowApps.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(172), 1f).apply {
                    marginEnd = dp(14)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, dp(184)).apply {
                if (rowIndex > 0) topMargin = dp(12)
            })
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildHero(): View {
        val featured = findFeaturedApp()
        val hero = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                intArrayOf(Color.rgb(20, 37, 65), Color.rgb(18, 27, 47), Color.rgb(10, 14, 25))
            ).apply { cornerRadius = dp(23).toFloat() }
            clipToOutline = true
            setPadding(dp(34), dp(25), dp(30), dp(24))
        }

        val ambient = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(135, 65, 113, 190), Color.argb(15, 65, 113, 190))
            ).apply { shape = GradientDrawable.OVAL }
        }
        hero.addView(ambient, FrameLayout.LayoutParams(dp(420), dp(420), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(-65)
        })

        val art = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(50, 72, 111), Color.rgb(24, 35, 57))
            ).apply { cornerRadius = dp(22).toFloat() }
            elevation = dp(8).toFloat()
        }
        if (featured != null) {
            art.addView(ImageView(this).apply {
                setImageDrawable(featured.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            art.addView(TextView(this).apply {
                text = "V"
                textSize = 100f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setTextColor(Color.argb(225, 255, 255, 255))
            }, FrameLayout.LayoutParams(-1, -1))
        }
        hero.addView(art, FrameLayout.LayoutParams(dp(190), dp(190), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(58)
        })

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        copy.addView(TextView(this).apply {
            text = "V A N T A     •     À LA UNE"
            textSize = 10f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(188, 210, 245))
        })
        copy.addView(TextView(this).apply {
            text = featured?.label ?: "Tout votre divertissement"
            textSize = 33f
            maxLines = 2
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(primaryText)
            setPadding(0, dp(10), 0, dp(6))
        })
        copy.addView(TextView(this).apply {
            text = if (featured?.packageName == "be.px.stbtvclient")
                "Retrouvez vos chaînes et vos programmes en direct."
            else "Vos applications préférées, réunies dans un seul endroit."
            textSize = 13f
            setTextColor(Color.rgb(205, 215, 231))
            maxLines = 2
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, 0)
        }
        actions.addView(heroButton("▶   Ouvrir", true) {
            if (featured?.packageName == "be.px.stbtvclient") openProximus()
            else if (featured != null) launchApp(featured.packageName)
            else openProximus()
        }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(12) })
        actions.addView(heroButton("Explorer", false) { focusFirstCard() },
            LinearLayout.LayoutParams(-2, dp(44)))
        copy.addView(actions)
        hero.addView(copy, FrameLayout.LayoutParams(dp(590), -1, Gravity.LEFT or Gravity.CENTER_VERTICAL))
        return hero
    }

    private fun findFeaturedApp(): TvApp? {
        val preferred = listOf("be.px.stbtvclient", "com.disney.disneyplus", "com.google.android.youtube.tv", "com.teamsmart.videomanager.tv", "com.crunchyroll.crunchyroid")
        return preferred.firstNotNullOfOrNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
            ?: apps.firstOrNull()
    }

    private fun addRail(title: String, subtitle: String, items: List<TvApp>) {
        if (items.isEmpty()) return
        if (title.isNotBlank()) {
            val heading = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(3), 0, dp(11))
            }
            heading.addView(TextView(this).apply {
                text = title
                textSize = 19f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(primaryText)
            })
            if (subtitle.isNotBlank()) heading.addView(TextView(this).apply {
                text = subtitle
                textSize = 10f
                setTextColor(mutedText)
                setPadding(0, dp(2), 0, 0)
            })
            content.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        }

        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = false
            setPadding(dp(2), dp(5), dp(12), dp(12))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEachIndexed { index, app ->
            row.addView(appCard(app, index == 0), LinearLayout.LayoutParams(dp(292), dp(172)).apply {
                marginEnd = dp(15)
            })
        }
        horizontal.addView(row)
        content.addView(horizontal, LinearLayout.LayoutParams(-1, dp(194)))
    }

    private fun appCard(app: TvApp, firstInRow: Boolean): View {
        val card = FrameLayout(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            background = rounded(surfaceColor, dp(15))
            clipToOutline = true
            contentDescription = "Ouvrir ${app.label}. Appui long pour ajouter ou retirer des favoris."
        }

        val palette = paletteFor(app.packageName)
        card.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(palette.first, palette.second, Color.rgb(16, 20, 31))
        ).apply { cornerRadius = dp(15).toFloat() }

        val glow = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(100, 255, 255, 255), Color.argb(0, 255, 255, 255))
            ).apply { shape = GradientDrawable.OVAL }
        }
        card.addView(glow, FrameLayout.LayoutParams(dp(185), dp(185), Gravity.RIGHT or Gravity.TOP).apply {
            rightMargin = dp(-32)
            topMargin = dp(-48)
        })

        // Large artwork area: the installed app identity becomes the visual cover, not a tiny launcher icon.
        card.addView(ImageView(this).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            alpha = 0.96f
        }, FrameLayout.LayoutParams(dp(118), dp(118), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(24)
            bottomMargin = dp(12)
        })

        if (favorites.contains(app.packageName)) {
            card.addView(TextView(this).apply {
                text = "♥"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = rounded(Color.argb(95, 0, 0, 0), dp(14))
            }, FrameLayout.LayoutParams(dp(27), dp(27), Gravity.TOP or Gravity.RIGHT).apply {
                topMargin = dp(12)
                rightMargin = dp(12)
            })
        }

        val bottomShade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.rgb(9, 12, 20), Color.argb(0, 9, 12, 20))
            )
        }
        card.addView(bottomShade, FrameLayout.LayoutParams(-1, dp(78), Gravity.BOTTOM))

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(142), dp(13))
            gravity = Gravity.BOTTOM
        }
        labels.addView(TextView(this).apply {
            text = app.label
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        labels.addView(TextView(this).apply {
            text = when (app.category) {
                AppCategory.VIDEO -> "FILMS ET SÉRIES"
                AppCategory.MUSIC -> "MUSIQUE"
                AppCategory.GAMES -> "JEUX"
                else -> if (app.tvCompatible) "TV" else "APPLICATION"
            }
            textSize = 8f
            letterSpacing = 0.1f
            setTextColor(Color.rgb(195, 205, 222))
            setPadding(0, dp(3), 0, 0)
        })
        card.addView(labels, FrameLayout.LayoutParams(-1, dp(76), Gravity.BOTTOM))

        card.setOnClickListener { launchApp(app.packageName) }
        card.setOnLongClickListener {
            if (!favorites.add(app.packageName)) favorites.remove(app.packageName)
            prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
            Toast.makeText(this, if (favorites.contains(app.packageName)) "Ajouté à Ma liste" else "Retiré de Ma liste", Toast.LENGTH_SHORT).show()
            renderHome()
            true
        }
        card.setOnFocusChangeListener { view, focused ->
            view.animate()
                .scaleX(if (focused) 1.075f else 1f)
                .scaleY(if (focused) 1.075f else 1f)
                .setDuration(180)
                .start()
            view.elevation = if (focused) dp(16).toFloat() else 0f
            if (focused) {
                view.parent?.let { parent ->
                    (parent as? HorizontalScrollView)?.smoothScrollTo(view.left - dp(18), 0)
                }
            }
        }
        card.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val row = card.parent as? ViewGroup
                    val rowIndex = if (row != null) (row.parent as? ViewGroup)?.indexOfChild(row) ?: 0 else 0
                    if (row != null && row.indexOfChild(card) == 0 && rowIndex == 0) {
                        navButtons[selectedCategory]?.requestFocus()
                        true
                    } else false
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    val y = card.top
                    if (y > dp(160)) mainScroll.smoothScrollBy(0, -dp(170))
                    false
                }
                else -> false
            }
        }
        return card
    }

    private fun paletteFor(pkg: String): Pair<Int, Int> {
        val key = pkg.lowercase()
        return when {
            "disney" in key -> Color.rgb(31, 56, 110) to Color.rgb(32, 83, 145)
            "crunchy" in key -> Color.rgb(115, 48, 27) to Color.rgb(75, 30, 24)
            "spotify" in key -> Color.rgb(24, 91, 70) to Color.rgb(17, 54, 46)
            "twitch" in key -> Color.rgb(83, 48, 139) to Color.rgb(48, 30, 91)
            "youtube" in key || "smarttube" in key -> Color.rgb(125, 35, 43) to Color.rgb(71, 24, 35)
            "geforce" in key -> Color.rgb(49, 91, 44) to Color.rgb(24, 53, 34)
            "f1" in key -> Color.rgb(120, 30, 39) to Color.rgb(62, 20, 31)
            "retroarch" in key || "bomb" in key || "game" in key -> Color.rgb(45, 66, 102) to Color.rgb(30, 38, 70)
            else -> Color.rgb(46, 66, 96) to Color.rgb(26, 37, 61)
        }
    }

    private fun heroButton(label: String, primary: Boolean, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            setTextColor(if (primary) Color.rgb(10, 16, 27) else primaryText)
            background = rounded(if (primary) Color.WHITE else Color.argb(48, 255, 255, 255), dp(24))
            isFocusable = true
            isClickable = true
            setOnClickListener { action() }
            setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(if (focused) 1.05f else 1f).scaleY(if (focused) 1.05f else 1f).setDuration(150).start()
                view.elevation = if (focused) dp(9).toFloat() else 0f
            }
        }

    private fun focusFirstCard() {
        val card = findFirstCard(content)
        if (card != null) card.requestFocus() else search.requestFocus()
    }

    private fun findFirstCard(group: ViewGroup): View? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is HorizontalScrollView) {
                val row = child.getChildAt(0) as? ViewGroup
                if (row != null && row.childCount > 0) return row.getChildAt(0)
            }
            if (child is ViewGroup) {
                val nested = findFirstCard(child)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun focusedPackage(): String? {
        val focused = window.decorView.findFocus() ?: return null
        return focused.contentDescription?.toString()
            ?.removePrefix("Ouvrir ")?.substringBefore(". Appui long")
            ?.let { label -> apps.firstOrNull { it.label == label }?.packageName }
    }

    private fun focusPackage(pkg: String) {
        val app = apps.firstOrNull { it.packageName == pkg } ?: return
        fun searchView(group: ViewGroup): View? {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child.contentDescription?.toString()?.startsWith("Ouvrir ${app.label}") == true) return child
                if (child is ViewGroup) searchView(child)?.let { return it }
            }
            return null
        }
        searchView(content)?.requestFocus()
    }

    private fun discoverApps(): List<TvApp> {
        val found = linkedMapOf<String, TvApp>()
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )
        intents.forEach { intent ->
            val results = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            val tvCompatible = intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            results.forEach resultLoop@{ info ->
                val pkg = info.activityInfo?.packageName ?: return@resultLoop
                if (pkg == packageName) return@resultLoop
                if (pkg.lowercase().contains("youtube") && !pkg.lowercase().contains("smarttube")) return@resultLoop
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@resultLoop
                val icon = runCatching { info.loadIcon(packageManager) }.getOrNull() ?: return@resultLoop
                val old = found[pkg]
                if (old == null) found[pkg] = TvApp(label, pkg, icon, categorize(pkg, label), tvCompatible)
                else if (tvCompatible && !old.tvCompatible) found[pkg] = old.copy(tvCompatible = true)
            }
        }
        return found.values.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun categorize(pkg: String, label: String): AppCategory {
        val value = "$pkg $label".lowercase(Locale.getDefault())
        return when {
            listOf("twitch", "game", "geforce", "steam", "xbox", "retroarch", "emulator", "bombsquad", "sonic").any(value::contains) -> AppCategory.GAMES
            listOf("youtube", "netflix", "disney", "crunchy", "primevideo", "plex", "auvio", "rtl", "tvi", "f1", "stb", "proximus", "vlc", "tvbro", "smarttube").any(value::contains) -> AppCategory.VIDEO
            listOf("spotify", "music", "deezer", "soundcloud", "radio").any(value::contains) -> AppCategory.MUSIC
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
            if (fallback != null) runCatching {
                startActivity(fallback)
                rememberRecent("be.px.stbtvclient")
            } else Toast.makeText(this, "Proximus TV est introuvable.", Toast.LENGTH_LONG).show()
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
        runCatching {
            rememberRecent(pkg)
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, "Impossible d'ouvrir cette application", Toast.LENGTH_SHORT).show()
        }
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

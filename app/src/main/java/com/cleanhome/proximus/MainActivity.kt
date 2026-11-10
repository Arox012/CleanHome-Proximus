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
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

private data class TvApp(
    val label: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable,
    val category: AppCategory
)

private enum class AppCategory(val title: String) {
    ALL("Découvrir"),
    FAVORITES("Favoris"),
    VIDEO("Films & séries"),
    MUSIC("Musique"),
    GAMES("Jeux"),
    OTHER("Applications")
}

class MainActivity : Activity() {
    private val backgroundColor = Color.rgb(5, 7, 14)
    private val surface = Color.rgb(17, 21, 34)
    private val surfaceRaised = Color.rgb(31, 28, 53)
    private val accent = Color.rgb(153, 105, 255)
    private val muted = Color.rgb(157, 166, 190)

    private val prefs by lazy { getSharedPreferences("vanta_settings", MODE_PRIVATE) }
    private val favorites = linkedSetOf<String>()
    private val recent = mutableListOf<String>()
    private var searchQuery = ""
    private var selectedCategory = AppCategory.ALL
    private lateinit var grid: GridView
    private lateinit var adapter: AppsAdapter
    private lateinit var countLabel: TextView
    private lateinit var emptyLabel: TextView
    private lateinit var recentSection: LinearLayout
    private lateinit var recentRail: LinearLayout
    private val categoryButtons = mutableMapOf<AppCategory, TextView>()
    private var apps: List<TvApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.decorView.systemUiVisibility = 0
        favorites.addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
        recent.addAll(prefs.getString("recent", "").orEmpty().split("|").filter { it.isNotBlank() })
        apps = discoverApps()
        buildInterface()
    }

    override fun onResume() {
        super.onResume()
        if (::grid.isInitialized) {
            apps = discoverApps()
            refreshGrid()
            buildRecentRail()
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(54), dp(30), dp(54), dp(28))
            setBackgroundColor(backgroundColor)
            isFocusable = false
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(TextView(this).apply {
            text = "V A N T A"
            textSize = 16f
            letterSpacing = 0.24f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
        })
        brand.addView(TextView(this).apply {
            text = "TV  /  ENTERTAINMENT SYSTEM"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, 0)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(makeActionButton("PROXIMUS", accent) { openProximus() },
            LinearLayout.LayoutParams(-2, dp(50)))
        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(
                intArrayOf(Color.rgb(47, 27, 86), Color.rgb(13, 35, 55)),
                dp(22)
            )
            setPadding(dp(26), dp(19), dp(24), dp(19))
        }
        val heroText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroText.addView(TextView(this).apply {
            text = "VANTA  /  HOME EXPERIENCE"
            textSize = 10f
            letterSpacing = 0.16f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
        })
        heroText.addView(TextView(this).apply {
            text = "Votre univers. Sans limites."
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(5), 0, 0)
        })
        heroText.addView(TextView(this).apply {
            text = "Tous vos divertissements réunis au même endroit."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })
        hero.addView(heroText, LinearLayout.LayoutParams(0, -2, 1f))
        countLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(44, 53, 77), dp(14))
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        hero.addView(countLabel)
        val heroParams = LinearLayout.LayoutParams(-1, -2)
        heroParams.topMargin = dp(24)
        root.addView(hero, heroParams)

        val search = android.widget.EditText(this).apply {
            hint = "⌕   Rechercher une application..."
            textSize = 13f
            singleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(surface, dp(13)).apply { setStroke(dp(1), Color.rgb(43, 49, 69)) }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.trim().orEmpty()
                    refreshGrid()
                    if (::recentSection.isInitialized) buildRecentRail()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        val searchParams = LinearLayout.LayoutParams(dp(370), dp(42))
        searchParams.topMargin = dp(14)
        root.addView(search, searchParams)

        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
        }
        val categoryBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), dp(8), dp(16))
        }
        AppCategory.entries.forEach { category ->
            val button = TextView(this).apply {
                text = category.title
                textSize = 14f
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                setPadding(dp(19), dp(11), dp(19), dp(11))
                setOnClickListener {
                    selectedCategory = category
                    updateCategoryStyles()
                    refreshGrid()
                }
                setOnFocusChangeListener { view, focused ->
                    if (focused) {
                        view.background = rounded(accent, dp(18))
                        (view as TextView).setTextColor(Color.BLACK)
                    } else {
                        updateCategoryStyles()
                    }
                }
            }
            categoryButtons[category] = button
            val params = LinearLayout.LayoutParams(-2, dp(44))
            params.marginEnd = dp(10)
            categoryBar.addView(button, params)
        }
        categoryScroll.addView(categoryBar)
        root.addView(categoryScroll)

        recentSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        recentSection.addView(TextView(this).apply {
            text = "RÉCEMMENT OUVERTES"
            textSize = 10f
            letterSpacing = 0.14f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(0), 0, dp(7))
        })
        val recentScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        recentRail = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        recentScroll.addView(recentRail)
        recentSection.addView(recentScroll, LinearLayout.LayoutParams(-1, dp(66)))
        root.addView(recentSection, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val sectionHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(12))
        }
        sectionHeader.addView(TextView(this).apply {
            text = "VOTRE BIBLIOTHÈQUE"
            textSize = 11f
            letterSpacing = 0.14f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        sectionHeader.addView(TextView(this).apply {
            text = "NAVIGUEZ AVEC LA TÉLÉCOMMANDE  •  OK POUR OUVRIR"
            textSize = 11f
            setTextColor(Color.rgb(111, 122, 147))
        })
        root.addView(sectionHeader)

        val content = FrameLayout(this)
        grid = GridView(this).apply {
            val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
            numColumns = if (widthDp >= 1500f) 6 else 5
            horizontalSpacing = dp(16)
            verticalSpacing = dp(16)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(8))
        }
        adapter = AppsAdapter()
        grid.adapter = adapter
        content.addView(grid, FrameLayout.LayoutParams(-1, -1))

        emptyLabel = TextView(this).apply {
            text = "Aucune application dans cette catégorie."
            textSize = 15f
            setTextColor(muted)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        content.addView(emptyLabel, FrameLayout.LayoutParams(-1, -1))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        updateCategoryStyles()
        refreshGrid()
        buildRecentRail()
        grid.requestFocus()
    }

    private fun makeActionButton(title: String, color: Int, action: () -> Unit): TextView =
        TextView(this).apply {
            text = title
            textSize = 13f
            letterSpacing = 0.06f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = rounded(color, dp(15))
            isFocusable = true
            isClickable = true
            setPadding(dp(22), dp(13), dp(22), dp(13))
            setOnClickListener { action() }
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused) Color.WHITE else color, dp(15))
                (view as TextView).setTextColor(Color.BLACK)
                view.scaleX = if (focused) 1.04f else 1f
                view.scaleY = if (focused) 1.04f else 1f
            }
        }

    private fun updateCategoryStyles() {
        categoryButtons.forEach { (category, view) ->
            val active = selectedCategory == category
            view.background = rounded(if (active) accent else surface, dp(18))
            view.setTextColor(if (active) Color.BLACK else Color.LTGRAY)
        }
    }

    private fun visibleApps(): List<TvApp> {
        val categoryApps = when (selectedCategory) {
            AppCategory.ALL -> apps
            AppCategory.FAVORITES -> apps.filter { favorites.contains(it.packageName) }
            else -> apps.filter { it.category == selectedCategory }
        }
        val filtered = if (searchQuery.isBlank()) categoryApps else categoryApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }
        return if (selectedCategory == AppCategory.ALL && searchQuery.isBlank())
            filtered.sortedWith(compareByDescending<TvApp> { favorites.contains(it.packageName) }.thenBy { it.label.lowercase() })
        else filtered.sortedBy { it.label.lowercase() }
    }

    private fun refreshGrid() {
        if (!::adapter.isInitialized) return
        adapter.notifyDataSetChanged()
        val count = visibleApps().size
        countLabel.text = count.toString() + " application" + if (count > 1) "s" else ""
        emptyLabel.visibility = if (count == 0) View.VISIBLE else View.GONE
    }

    private fun buildRecentRail() {
        if (!::recentSection.isInitialized) return
        recentRail.removeAllViews()
        val items = recent.mapNotNull { key -> apps.firstOrNull { it.packageName == key } }.take(6)
        recentSection.visibility = if (selectedCategory == AppCategory.ALL && searchQuery.isBlank() && items.isNotEmpty()) View.VISIBLE else View.GONE
        items.forEach { app ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(surface, dp(12))
                isFocusable = true
                isClickable = true
                setPadding(dp(10), dp(6), dp(14), dp(6))
                setOnClickListener { launchApp(app.packageName) }
                setOnFocusChangeListener { view, focused ->
                    view.background = rounded(if (focused) surfaceRaised else surface, dp(12)).apply {
                        if (focused) setStroke(dp(2), accent)
                    }
                }
            }
            item.addView(ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            item.addView(TextView(this).apply {
                text = app.label
                textSize = 12f
                setTextColor(Color.WHITE)
                maxLines = 1
                setPadding(dp(8), 0, 0, 0)
            })
            recentRail.addView(item, LinearLayout.LayoutParams(-2, dp(56)).apply { marginEnd = dp(8) })
        }
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
            results.forEach resultLoop@{ info ->
                val packageName = info.activityInfo?.packageName ?: return@resultLoop
                if (packageName == packageNameOfSelf() || found.containsKey(packageName)) return@resultLoop
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@resultLoop
                val icon = runCatching { info.loadIcon(packageManager) }.getOrNull() ?: return@resultLoop
                found[packageName] = TvApp(label, packageName, icon, categorize(packageName, label))
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    private fun packageNameOfSelf() = applicationContext.packageName

    private fun categorize(packageName: String, label: String): AppCategory {
        val value = "$packageName $label".lowercase()
        return when {
            listOf("youtube", "netflix", "disney", "crunchy", "primevideo", "prime video", "plex", "twitch", "auvio", "rtl", "tvi", "f1", "stb", "proximus").any(value::contains) -> AppCategory.VIDEO
            listOf("spotify", "music", "deezer", "soundcloud", "radio").any(value::contains) -> AppCategory.MUSIC
            listOf("game", "geforce", "steam", "xbox", "playstation", "retroarch", "emulator").any(value::contains) -> AppCategory.GAMES
            else -> AppCategory.OTHER
        }
    }

    private fun openProximus() {
        val intent = Intent().setClassName(
            "be.px.stbtvclient",
            "tv.threess.threeready.ui.generic.activity.MainActivity"
        )
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, "Impossible d'ouvrir l'interface Proximus", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun rounded(colors: IntArray, radius: Int) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, colors
    ).apply {
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private inner class AppsAdapter : BaseAdapter() {
        override fun getCount() = visibleApps().size
        override fun getItem(position: Int) = visibleApps()[position]
        override fun getItemId(position: Int) = getItem(position).packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val app = getItem(position)
            val card = (convertView as? LinearLayout) ?: createAppCard()
            card.findViewWithTag<ImageView>("app_icon").setImageDrawable(app.icon)
            card.findViewWithTag<TextView>("app_label").text = app.label
            card.setOnClickListener { launchApp(app.packageName) }
            card.setOnLongClickListener {
                if (!favorites.add(app.packageName)) favorites.remove(app.packageName)
                prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
                Toast.makeText(this@MainActivity, if (favorites.contains(app.packageName)) "Ajouté aux favoris" else "Retiré des favoris", Toast.LENGTH_SHORT).show()
                refreshGrid()
                true
            }
            return card
        }

        private fun createAppCard() = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setPadding(dp(10), dp(16), dp(10), dp(14))
            layoutParams = AbsListView.LayoutParams(-1, dp(154))
            background = rounded(surface, dp(17))

            addView(ImageView(this@MainActivity).apply {
                tag = "app_icon"
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(68), dp(68)))

            addView(TextView(this@MainActivity).apply {
                tag = "app_label"
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(Color.WHITE)
                setPadding(0, dp(10), 0, 0)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }, LinearLayout.LayoutParams(-1, -2))

            setOnFocusChangeListener { view, focused ->
                view.background = if (focused) rounded(surfaceRaised, dp(17)).apply { setStroke(dp(2), accent) } else rounded(surface, dp(17))
                view.scaleX = if (focused) 1.045f else 1f
                view.scaleY = if (focused) 1.045f else 1f
                view.elevation = if (focused) dp(8).toFloat() else 0f
            }
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(this, "Application non lançable", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(intent)
            recent.remove(packageName)
            recent.add(0, packageName)
            while (recent.size > 10) recent.removeAt(recent.lastIndex)
            prefs.edit().putString("recent", recent.joinToString("|")).apply()
            buildRecentRail()
        }.onFailure {
            Toast.makeText(this, "Impossible d'ouvrir cette application", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && searchQuery.isNotBlank()) {
            searchQuery = ""
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && selectedCategory != AppCategory.ALL) {
            selectedCategory = AppCategory.ALL
            updateCategoryStyles()
            refreshGrid()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

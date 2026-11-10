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
import android.widget.BaseAdapter
import android.widget.GridView
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
    ALL("Tout"),
    VIDEO("Films & séries"),
    MUSIC("Musique"),
    GAMES("Jeux"),
    OTHER("Autres")
}

class MainActivity : Activity() {
    private val backgroundColor = Color.rgb(8, 10, 16)
    private val surface = Color.rgb(22, 26, 38)
    private val accent = Color.rgb(139, 155, 255)
    private var selectedCategory = AppCategory.ALL
    private lateinit var grid: GridView
    private lateinit var adapter: AppsAdapter
    private lateinit var categoryBar: LinearLayout
    private lateinit var apps: List<TvApp>
    private val categoryButtons = mutableMapOf<AppCategory, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        apps = discoverApps()
        buildInterface()
    }

    override fun onResume() {
        super.onResume()
        if (::grid.isInitialized) {
            apps = discoverApps()
            refreshGrid()
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(42), dp(28), dp(42), dp(24))
            setBackgroundColor(backgroundColor)
            isFocusable = false
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(TextView(this).apply {
            text = "CLEANHOME"
            textSize = 12f
            letterSpacing = 0.24f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
        })
        titleColumn.addView(TextView(this).apply {
            text = "Tout ce que vous aimez, au même endroit."
            textSize = 25f
            setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, 0)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        top.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))

        val proximus = TextView(this).apply {
            text = "  Proximus  "
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(surface, dp(12))
            isFocusable = true
            isClickable = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { openProximus() }
            setOnFocusChangeListener { v, hasFocus ->
                v.background = rounded(if (hasFocus) accent else surface, dp(12))
                (v as TextView).setTextColor(if (hasFocus) Color.BLACK else Color.WHITE)
            }
        }
        top.addView(proximus, LinearLayout.LayoutParams(-2, dp(48)))
        root.addView(top)

        categoryBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(22), 0, dp(16))
        }
        AppCategory.entries.forEach { category ->
            val button = TextView(this).apply {
                text = category.title
                textSize = 14f
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                setPadding(dp(17), dp(10), dp(17), dp(10))
                setOnClickListener {
                    selectedCategory = category
                    updateCategoryStyles()
                    refreshGrid()
                }
                setOnFocusChangeListener { v, focused ->
                    if (focused) v.background = rounded(accent, dp(18))
                    else updateCategoryStyles()
                    (v as TextView).setTextColor(if (focused || selectedCategory == category) Color.BLACK else Color.LTGRAY)
                }
            }
            categoryButtons[category] = button
            val params = LinearLayout.LayoutParams(-2, dp(42))
            params.marginEnd = dp(8)
            categoryBar.addView(button, params)
        }
        root.addView(categoryBar)

        grid = GridView(this).apply {
            numColumns = 5
            horizontalSpacing = dp(16)
            verticalSpacing = dp(18)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        adapter = AppsAdapter()
        grid.adapter = adapter
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateCategoryStyles()
        grid.requestFocus()
    }

    private fun updateCategoryStyles() {
        categoryButtons.forEach { (category, view) ->
            val active = selectedCategory == category
            view.background = rounded(if (active) accent else surface, dp(18))
            view.setTextColor(if (active) Color.BLACK else Color.LTGRAY)
        }
    }

    private fun refreshGrid() {
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
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
            results.forEach { info ->
                val packageName = info.activityInfo?.packageName ?: return@forEach
                if (packageName == packageNameOfSelf() || found.containsKey(packageName)) return@forEach
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@forEach
                val icon = runCatching { info.loadIcon(packageManager) }.getOrNull() ?: return@forEach
                found[packageName] = TvApp(label, packageName, icon, categorize(packageName, label))
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    private fun packageNameOfSelf() = applicationContext.packageName

    private fun categorize(packageName: String, label: String): AppCategory {
        val value = "$packageName $label".lowercase()
        return when {
            listOf("youtube", "netflix", "disney", "crunchy", "prime video", "plex", "twitch", "auvio", "rtl", "tvi", "tv").any(value::contains) -> AppCategory.VIDEO
            listOf("spotify", "music", "deezer", "soundcloud").any(value::contains) -> AppCategory.MUSIC
            listOf("game", "geforce", "steam", "xbox", "play").any(value::contains) -> AppCategory.GAMES
            else -> AppCategory.OTHER
        }
    }

    private fun openProximus() {
        val intent = packageManager.getLaunchIntentForPackage("be.px.stbtvclient")
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Interface Proximus introuvable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class AppsAdapter : BaseAdapter() {
        private val visibleApps: List<TvApp>
            get() = if (selectedCategory == AppCategory.ALL) apps
            else apps.filter { it.category == selectedCategory }

        override fun getCount() = visibleApps.size
        override fun getItem(position: Int) = visibleApps[position]
        override fun getItemId(position: Int) = visibleApps[position].packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val app = getItem(position)
            val card = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                setPadding(dp(10), dp(14), dp(10), dp(12))
                layoutParams = android.widget.AbsListView.LayoutParams(-1, dp(132))
                val icon = ImageView(this@MainActivity).apply {
                    tag = "app_icon"
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)))
                val label = TextView(this@MainActivity).apply {
                    tag = "app_label"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(9), 0, 0)
                }
                addView(label, LinearLayout.LayoutParams(-1, -2))
                setOnFocusChangeListener { v, focused ->
                    v.background = rounded(if (focused) Color.rgb(48, 56, 78) else surface, dp(16))
                    v.scaleX = if (focused) 1.045f else 1f
                    v.scaleY = if (focused) 1.045f else 1f
                }
                setOnClickListener { launchApp(app.packageName) }
            }
            (card.findViewWithTag<ImageView>("app_icon")).setImageDrawable(app.icon)
            card.findViewWithTag<TextView>("app_label").text = app.label
            card.setOnClickListener { launchApp(app.packageName) }
            return card
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(this, "Application non lançable", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "Impossible d'ouvrir cette application", Toast.LENGTH_SHORT).show() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (selectedCategory != AppCategory.ALL) {
                selectedCategory = AppCategory.ALL
                updateCategoryStyles()
                refreshGrid()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

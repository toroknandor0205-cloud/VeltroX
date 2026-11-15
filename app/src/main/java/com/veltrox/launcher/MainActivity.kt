package com.veltrox.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.liquidglass.LiquidGlassView
import org.json.JSONArray
import org.json.JSONObject

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    var isFavorite: Boolean = false
)

class MainActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var appsRecycler: RecyclerView
    private lateinit var favoritesRecycler: RecyclerView
    private lateinit var letterScroller: LinearLayout
    private lateinit var headerGlass: LiquidGlassView

    private val allApps = mutableListOf<AppItem>()
    private val displayedApps = mutableListOf<AppItem>()
    private val favoritePackages = mutableSetOf<String>()

    private lateinit var appsAdapter: AppAdapter
    private lateinit var favoritesAdapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        searchInput = findViewById(R.id.searchInput)
        appsRecycler = findViewById(R.id.appsRecycler)
        favoritesRecycler = findViewById(R.id.favoritesRecycler)
        letterScroller = findViewById(R.id.letterScroller)
        headerGlass = findViewById(R.id.headerGlass)
        headerGlass.enableDynamicBackground = true

        loadFavorites()
        loadApps()

        appsAdapter = AppAdapter(displayedApps, false)
        favoritesAdapter = AppAdapter(getFavoriteApps(), true)

        appsRecycler.layoutManager = LinearLayoutManager(this)
        appsRecycler.adapter = appsAdapter

        favoritesRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        favoritesRecycler.adapter = favoritesAdapter

        setupSearch()
        setupLetterScroller()
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveList: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        allApps.clear()
        for (info in resolveList) {
            val label = info.loadLabel(pm).toString()
            val packageName = info.activityInfo.packageName
            if (packageName == this.packageName) continue
            val icon = info.loadIcon(pm)
            allApps.add(AppItem(label, packageName, icon, favoritePackages.contains(packageName)))
        }
        allApps.sortBy { it.label.lowercase() }
        updateDisplayedApps("")
    }

    private fun getFavoriteApps(): List<AppItem> {
        return allApps.filter { it.isFavorite }
    }

    private fun updateDisplayedApps(query: String) {
        displayedApps.clear()
        val filtered = if (query.isBlank()) {
            allApps.filter { !it.isFavorite }
        } else {
            allApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        displayedApps.addAll(filtered)
        appsAdapter.notifyDataSetChanged()
        favoritesAdapter = AppAdapter(getFavoriteApps(), true)
        favoritesRecycler.adapter = favoritesAdapter
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateDisplayedApps(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupLetterScroller() {
        letterScroller.removeAllViews()
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toList()
        for (letter in letters) {
            val tv = TextView(this).apply {
                text = letter.toString()
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, 6, 0, 6)
                setOnClickListener {
                    scrollToLetter(letter.toString())
                }
            }
            letterScroller.addView(tv)
        }
    }

    private fun scrollToLetter(letter: String) {
        val index = displayedApps.indexOfFirst {
            val first = it.label.firstOrNull()?.uppercaseChar() ?: '#'
            if (letter == "#") !first.isLetter() else first == letter[0]
        }
        if (index >= 0) {
            appsRecycler.scrollToPosition(index)
        }
    }

    private fun toggleFavorite(app: AppItem) {
        app.isFavorite = !app.isFavorite
        if (app.isFavorite) favoritePackages.add(app.packageName)
        else favoritePackages.remove(app.packageName)
        saveFavorites()
        updateDisplayedApps(searchInput.text.toString())
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        }
    }

    private fun saveFavorites() {
        val arr = JSONArray()
        favoritePackages.forEach { arr.put(it) }
        getSharedPreferences("veltrox", Context.MODE_PRIVATE)
            .edit().putString("favorites", arr.toString()).apply()
    }

    private fun loadFavorites() {
        val json = getSharedPreferences("veltrox", Context.MODE_PRIVATE)
            .getString("favorites", "[]") ?: "[]"
        val arr = JSONArray(json)
        favoritePackages.clear()
        for (i in 0 until arr.length()) {
            favoritePackages.add(arr.getString(i))
        }
    }

    inner class AppAdapter(
        private val items: List<AppItem>,
        private val isFavoriteList: Boolean
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val glass = view as LiquidGlassView
            val icon: ImageView = view.findViewById(R.id.appIcon)
            val name: TextView = view.findViewById(R.id.appName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            (view as LiquidGlassView).enableDynamicBackground = true
            return VH(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = items[position]
            holder.icon.setImageDrawable(app.icon)
            holder.name.text = app.label

            holder.itemView.setOnClickListener {
                launchApp(app.packageName)
            }

            holder.itemView.setOnLongClickListener {
                toggleFavorite(app)
                Toast.makeText(
                    this@MainActivity,
                    if (app.isFavorite) "Hozzáadva a kedvencekhez" else "Eltávolítva a kedvencekből",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        }
    }
}

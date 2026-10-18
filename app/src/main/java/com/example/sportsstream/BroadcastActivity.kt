package com.example.sportsstream

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Date

/** Native public TV guide; account/DRM playback stays with the official service. */
class BroadcastActivity : AppCompatActivity() {
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var task: java.util.concurrent.Future<*>? = null
    private var generation = 0
    private var day = LocalDate.now()
    private lateinit var rows: androidx.recyclerview.widget.RecyclerView
    private lateinit var date: TextView
    private lateinit var status: TextView
    private var broadcasts = emptyList<Broadcast>()
    private var channel = "All channels"
    private var liveOnly = false
    private var matchOnly = false
    private var home = ""
    private var away = ""
    private val sport by lazy { intent.getStringExtra("sport") ?: "football" }
    private val accent = 0xFF5FDCA5.toInt()
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun label(value: String, size: Float = 14f) = TextView(this).apply {
        text=value; textSize=size; setTextColor(Color.WHITE); setPadding(dp(12),dp(12),dp(12),dp(12))
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        day = runCatching { LocalDate.parse(state?.getString("day") ?: intent.getStringExtra("day")) }.getOrDefault(LocalDate.now())
        home=intent.getStringExtra("home").orEmpty(); away=intent.getStringExtra("away").orEmpty()
        matchOnly=home.isNotBlank() && away.isNotBlank()
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(0xFF090E1A.toInt()) }
        setContentView(root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets
        }
        root.addView(label("‹ Back to matches").apply { setTextColor(accent); setOnClickListener { finish() } })
        root.addView(label("TV & watch",24f).apply { setTypeface(null,Typeface.BOLD) })
        root.addView(label("beIN MENA · Arabic / English lineup\nTimes: ${ZoneId.systemDefault().id} · airtimes are not kickoff times",11f))
        val nav=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        nav.addView(label("‹").apply { contentDescription="Previous guide day"; setOnClickListener { day=day.minusDays(1);load() } },LinearLayout.LayoutParams(dp(48),dp(48)))
        date=label(day.toString()).apply { gravity=Gravity.CENTER;setOnClickListener {
            android.app.DatePickerDialog(this@BroadcastActivity,{_,y,m,d -> day=LocalDate.of(y,m+1,d);load()},day.year,day.monthValue-1,day.dayOfMonth).show()
        } }
        nav.addView(date,LinearLayout.LayoutParams(0,dp(48),1f))
        nav.addView(label("›").apply { contentDescription="Next guide day";setOnClickListener { day=day.plusDays(1);load() } },LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(nav)
        val filters=LinearLayout(this)
        filters.addView(Button(this).apply { text="Channels";setOnClickListener {
            val names=listOf("All channels")+broadcasts.map { it.channel }.distinct().sorted()
            androidx.appcompat.app.AlertDialog.Builder(this@BroadcastActivity).setTitle("Channel").setItems(names.toTypedArray()) { _,i -> channel=names[i];render() }.show()
        } },LinearLayout.LayoutParams(0,dp(48),1f))
        filters.addView(CheckBox(this).apply { text="Live only";setTextColor(Color.WHITE);setOnCheckedChangeListener { _,checked -> liveOnly=checked;render() } })
        root.addView(filters)
        if(matchOnly) root.addView(CheckBox(this).apply { text="Match: $home / $away";setTextColor(Color.WHITE);isChecked=true;setOnCheckedChangeListener { _,c -> matchOnly=c;render() } })
        root.addView(Button(this).apply { text="Official services · ${intent.getStringExtra("sport_name") ?: sport}";setOnClickListener { services() } })
        status=label("Loading official channel schedules…",12f); root.addView(status)
        rows=androidx.recyclerview.widget.RecyclerView(this).apply {
            layoutManager=androidx.recyclerview.widget.LinearLayoutManager(this@BroadcastActivity)
            itemAnimator=null
        }
        root.addView(rows,LinearLayout.LayoutParams(-1,0,1f));load()
    }
    private fun load() {
        val seq=++generation; val requested=day
        task?.cancel(true);broadcasts=emptyList();date.text=day.toString();rows.adapter=null;status.text="Loading official channel schedules…"
        task=executor.submit {
            val result=runCatching { BeinGuide.load(requested) }
            runOnUiThread {
                if(isDestroyed || isFinishing || seq!=generation) return@runOnUiThread
                if(result.isFailure) { status.text="beIN schedule unavailable. Tap to retry.";status.setOnClickListener { load() } }
                else { broadcasts=result.getOrThrow();status.setOnClickListener(null);render() }
            }
        }
    }
    private fun render() {
        val shown=broadcasts.filter { (channel=="All channels" || it.channel==channel) && (!liveOnly || it.live && !it.replay) && (!matchOnly || BeinGuide.matches(it,home,away)) }
        status.text="${shown.size} programmes · $channel · official guide\n${if(matchOnly) "Exact team-name matches only; spelling differences can hide broadcasts." else "Live means a published live broadcast, not a verified match clock."}"
        val format=SimpleDateFormat("EEE HH:mm",java.util.Locale.getDefault())
        rows.adapter=object : androidx.recyclerview.widget.RecyclerView.Adapter<GuideHolder>() {
            override fun getItemCount()=shown.size.coerceAtLeast(1)
            override fun onCreateViewHolder(parent:android.view.ViewGroup,type:Int)=GuideHolder(label("",13f).apply {
                setBackgroundColor(0xFF141D2E.toInt())
                layoutParams=androidx.recyclerview.widget.RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=dp(2) }
            })
            override fun onBindViewHolder(holder:GuideHolder,position:Int) {
                val view=holder.itemView as TextView
                if(shown.isEmpty()) { view.text="No broadcasts in this view. Try another date, channel or turn off the match filter.";view.setOnClickListener(null);return }
                val b=shown[position]
                val kind=if(b.replay) "Replay" else if(b.live) "Live broadcast" else "Programme"
                view.text="${b.channel} · $kind\n${format.format(Date(b.start*1000))} – ${format.format(Date(b.end*1000))}\n${b.title}\n${if(b.watchUrl.isBlank()) "Official guide" else "Open official channel ↗"}"
                view.setOnClickListener { openExternal(b.watchUrl.ifBlank { "https://www.beinsports.com/en-mena/tv-guide" }) }
            }
        }
    }
    private fun services() {
        val resources=WatchResources.forSport(sport)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Official watch services")
            .setItems(resources.map { "${it.name}\n${it.note}" }.toTypedArray()) { _,i -> openExternal(resources[i].url) }
            .setNegativeButton("Close",null).show()
    }
    private fun openExternal(url: String) {
        if(UrlPolicy.https(url)==null) return
        try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
        catch(_:android.content.ActivityNotFoundException) { Toast.makeText(this,"No browser installed",Toast.LENGTH_SHORT).show() }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("day",day.toString());super.onSaveInstanceState(outState) }
    override fun onDestroy() { generation++;task?.cancel(true);executor.shutdownNow();super.onDestroy() }
    private class GuideHolder(view:TextView):androidx.recyclerview.widget.RecyclerView.ViewHolder(view)
}

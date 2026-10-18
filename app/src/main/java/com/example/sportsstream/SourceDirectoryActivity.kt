package com.example.sportsstream

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import org.json.JSONObject

class SourceDirectoryActivity:AppCompatActivity() {
    private val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
    private data class Source(val name:String,val url:String,val language:String,val origin:String,val availability:String)
    private val sources by lazy {
        val rows=assets.open("source_directory.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("sources") }
        (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let { r ->
            r.optString("url").takeIf { UrlPolicy.https(it)!=null && r.optString("name").isNotBlank() }?.let { Source(r.optString("name"),it,r.optString("language"),r.optString("origin"),r.optString("availability","Playback unverified")) }
        } }
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
    private fun text(value:String,size:Float=14f)=TextView(this).apply { text=value;textSize=size;setTextColor(Color.WHITE);setPadding(dp(16),dp(12),dp(16),dp(12)) }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF090E1A.toInt()) };setContentView(root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v,i -> val b=i.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);i }
        root.addView(text("‹ Back to matches").apply { setOnClickListener { finish() } })
        root.addView(text("Sources",24f))
        root.addView(text("Streamed players open in the app. Directory websites open in your browser. Availability and commentary are unverified unless stated.",12f))
        root.addView(Button(this).apply { text="Published channel servers";setOnClickListener { channels() } })
        root.addView(Button(this).apply { text="Streamed channel players";setOnClickListener {
            isEnabled=false;text="Loading published channels…"
            executor.submit {
                val result=runCatching { StreamedCatalogue.channels() }
                runOnUiThread {
                    if(isFinishing || isDestroyed) return@runOnUiThread
                    isEnabled=true;text="Streamed channel players"
                    val entries=result.getOrDefault(emptyList())
                    if(entries.isEmpty()) Toast.makeText(this@SourceDirectoryActivity,if(result.isFailure) "Channel API unavailable" else "No channel posts published",Toast.LENGTH_SHORT).show()
                    else androidx.appcompat.app.AlertDialog.Builder(this@SourceDirectoryActivity).setTitle("Channel posts · not match schedules")
                        .setItems(entries.map { it.first }.toTypedArray()) { _,i -> startActivity(Intent(this@SourceDirectoryActivity,ResolveActivity::class.java).putExtra("event_name",entries[i].first).putExtra("event_url",entries[i].second)) }.setNegativeButton("Close",null).show()
                }
            }
        } })
        val search=EditText(this).apply { hint="Search sources or Arabic websites";setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);isSingleLine=true }
        root.addView(search)
        val arabic=CheckBox(this).apply { text="Arabic & Tunisian websites";isChecked=true;setTextColor(Color.WHITE) };root.addView(arabic)
        val count=text("");root.addView(count)
        val list=RecyclerView(this).apply { layoutManager=LinearLayoutManager(this@SourceDirectoryActivity);itemAnimator=null };root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        fun render() {
            val q=search.text.toString().trim()
            val shown=sources.filter { (!arabic.isChecked || it.language.contains("Arabic") || it.language.contains("Tunisia")) && (it.name.contains(q,true)||it.url.contains(q,true)||it.language.contains(q,true)) }
            count.text="${shown.size} source links · mirrors may share servers"
            list.adapter=object:RecyclerView.Adapter<Holder>() {
                override fun getItemCount()=shown.size
                override fun onCreateViewHolder(p:ViewGroup,t:Int)=Holder(text("",13f).apply { layoutParams=RecyclerView.LayoutParams(-1,-2);minHeight=dp(72) })
                override fun onBindViewHolder(h:Holder,p:Int) {
                    val s=shown[p];(h.itemView as TextView).apply {
                        text="${s.name}\n${Uri.parse(s.url).host} · ${s.language}\n${s.availability}\nOpen website ↗ · ${s.origin}"
                        setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(s.url))) } catch(_:android.content.ActivityNotFoundException) { Toast.makeText(this@SourceDirectoryActivity,"No browser installed",Toast.LENGTH_SHORT).show() } }
                    }
                }
            }
        }
        search.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { render() }
            override fun afterTextChanged(e:android.text.Editable?) {}
        });arabic.setOnCheckedChangeListener { _,_ -> render() };render()
    }
    private fun channels() {
        val data=assets.open("channels.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("channels") }
        val rows=(0 until data.length()).map { data.getJSONObject(it) }.filter { UrlPolicy.allowsResource(it.optString("url"),listOf("pl.koralive1.cc")) }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Published channel pages").setItems(rows.map { it.optString("name") }.toTypedArray()) { _,i ->
            startActivity(Intent(this,ResolveActivity::class.java).putExtra("event_name",rows[i].optString("name")).putExtra("event_url",rows[i].optString("url")))
        }.setNegativeButton("Close",null).show()
    }
    private class Holder(view:TextView):RecyclerView.ViewHolder(view)
    override fun onDestroy() { executor.shutdownNow();super.onDestroy() }
}

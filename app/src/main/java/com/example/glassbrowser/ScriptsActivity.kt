package com.example.glassbrowser

import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

/** Felhasználói szkriptek (kiegészítők) listája: hozzáadás, szerkesztés, törlés. */
class ScriptsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var scripts: MutableList<UserScript>
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        ThemeHelper.apply(prefs)
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        scripts = UserScripts.load(prefs)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL

        val info = TextView(this)
        info.text = "A szkriptek az oldal betöltése után futnak le. Koppints egy szkriptre a szerkesztéshez."
        info.setPadding(pad, pad, pad, pad / 2)
        column.addView(info)

        val add = Button(this)
        add.text = "＋  Új szkript"
        add.setOnClickListener { edit(-1) }
        val addLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        addLp.gravity = Gravity.START
        addLp.leftMargin = pad
        column.addView(add, addLp)

        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> edit(position) }
        column.addView(
            list,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(column)
        refresh()
    }

    private fun refresh() {
        adapter.clear()
        adapter.addAll(scripts.map { (if (it.enabled) "✓  " else "✕  ") + it.name.ifBlank { "(névtelen)" } })
        adapter.notifyDataSetChanged()
    }

    private fun edit(index: Int) {
        val script = if (index >= 0) scripts[index] else UserScript("", "", "", true)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad / 2, pad, 0)

        val name = EditText(this)
        name.hint = "Név"
        name.setText(script.name)
        name.inputType = InputType.TYPE_CLASS_TEXT
        box.addView(name)

        val match = EditText(this)
        match.hint = "Hol fusson? (pl. youtube.com – üresen mindenhol)"
        match.setText(script.match)
        match.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        box.addView(match)

        val code = EditText(this)
        code.hint = "JavaScript kód"
        code.setText(script.code)
        code.typeface = Typeface.MONOSPACE
        code.textSize = 13f
        code.minLines = 6
        code.maxLines = 12
        code.gravity = Gravity.TOP or Gravity.START
        code.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        box.addView(code)

        val enabled = CheckBox(this)
        enabled.text = "Bekapcsolva"
        enabled.isChecked = script.enabled
        box.addView(enabled)

        val builder = AlertDialog.Builder(this)
            .setTitle(if (index >= 0) "Szkript szerkesztése" else "Új szkript")
            .setView(box)
            .setPositiveButton("Mentés") { _, _ ->
                script.name = name.text.toString().trim()
                script.match = match.text.toString().trim()
                script.code = code.text.toString()
                script.enabled = enabled.isChecked
                if (index < 0) scripts.add(script)
                UserScripts.save(prefs, scripts)
                refresh()
            }
            .setNegativeButton("Mégse", null)
        if (index >= 0) {
            builder.setNeutralButton("Törlés") { _, _ ->
                scripts.removeAt(index)
                UserScripts.save(prefs, scripts)
                refresh()
            }
        }
        builder.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

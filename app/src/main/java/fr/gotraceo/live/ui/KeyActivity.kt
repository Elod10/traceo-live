package fr.gotraceo.live.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.data.PrimClient
import fr.gotraceo.live.data.PrimDay
import fr.gotraceo.live.data.PrimException
import fr.gotraceo.live.widget.QuotaAlerts
import fr.gotraceo.live.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Clé API PRIM, facultative : l'enregistrer, la tester ou la retirer. */
class KeyActivity : Activity() {

    private val scope = MainScope()
    private lateinit var prefs: Prefs
    private lateinit var keyInput: EditText
    private lateinit var keyStatus: TextView
    private lateinit var keyTest: Button
    private var keyShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_key)
        SystemBars.pad(this)
        prefs = Prefs(this)

        keyInput = findViewById(R.id.key_input)
        keyStatus = findViewById(R.id.key_status)
        keyTest = findViewById(R.id.key_test)
        keyInput.setText(prefs.apiKey)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.key_show).setOnClickListener { toggleKey() }
        findViewById<View>(R.id.key_save).setOnClickListener { saveKey() }
        keyTest.setOnClickListener { testKey() }
        findViewById<View>(R.id.key_portal).setOnClickListener { UiText.openUrl(this, UiText.PRIM_PORTAL_URL) }
        findViewById<TextView>(R.id.key_privacy).movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onResume() {
        super.onResume()
        showUsage()
    }

    /** Requêtes du jour : la barre passe en couleur d'alerte à 80 % du quota. */
    private fun showUsage() {
        val card = findViewById<View>(R.id.key_usage)
        if (!prefs.hasApiKey) {
            card.visibility = View.GONE
            return
        }
        val today = PrimDay.today()
        val usage = prefs.primUsage(today)
        val exhausted = usage.isExhausted(today)
        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.key_usage_value).text =
            getString(R.string.key_usage_value, QuotaAlerts.number(usage.used), QuotaAlerts.number(usage.quota))
        findViewById<ProgressBar>(R.id.key_usage_bar).apply {
            max = usage.quota
            progress = usage.used.coerceAtMost(usage.quota)
            val alert = exhausted || usage.used >= usage.quota * 0.8
            progressTintList = getColorStateList(if (alert) R.color.error_text else R.color.text_primary)
        }
        findViewById<TextView>(R.id.key_usage_help).apply {
            setText(if (exhausted) R.string.key_usage_exhausted else R.string.key_usage_help)
            setTextColor(getColor(if (exhausted) R.color.error_text else R.color.text_secondary))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun toggleKey() {
        keyShown = !keyShown
        val variation = if (keyShown) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or variation or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        keyInput.typeface = Typeface.MONOSPACE
        keyInput.setSelection(keyInput.text.length)
    }

    /** Enregistre la clé saisie. Un champ vide retire la clé : les widgets reviennent sur Tracéo. */
    private fun saveKey() {
        val key = keyInput.text.toString().trim()
        val changed = key != prefs.apiKey
        prefs.apiKey = key
        showStatus(getString(if (key.isEmpty()) R.string.main_key_removed else R.string.main_key_saved), error = false)
        if (changed) {
            // Le quota appartient à la clé : celui qu'une autre clé a épuisé ne doit pas suspendre celle-ci.
            prefs.resetPrimUsage()
            WidgetUpdater.renderAll(this)
            WidgetUpdater.requestRefresh(this)
        }
        showUsage()
    }

    private fun testKey() {
        // « Tester » avec un champ vide ne doit pas retirer la clé enregistrée.
        if (keyInput.text.isNullOrBlank()) {
            showStatus(getString(R.string.main_key_empty), error = true)
            return
        }
        saveKey()
        val key = prefs.apiKey
        keyTest.isEnabled = false
        showStatus(getString(R.string.main_key_testing), error = false)
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    PrimClient(key, usage = QuotaAlerts.listener(this@KeyActivity)).testKey()
                    null
                } catch (e: PrimException) {
                    e
                }
            }
            keyTest.isEnabled = true
            showUsage()
            if (error == null) {
                showStatus(getString(R.string.main_key_ok), error = false)
            } else {
                showStatus(error.message.orEmpty(), error = true)
            }
        }
    }

    private fun showStatus(message: String, error: Boolean) {
        keyStatus.visibility = View.VISIBLE
        keyStatus.text = message
        keyStatus.setTextColor(getColor(if (error) R.color.error_text else R.color.success_text))
    }
}

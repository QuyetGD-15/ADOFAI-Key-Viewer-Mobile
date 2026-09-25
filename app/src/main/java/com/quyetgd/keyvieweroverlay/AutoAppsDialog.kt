package com.quyetgd.keyvieweroverlay

import android.app.AppOpsManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Only Save writes the app selection; only Continue can dismiss future battery advice. */
class AutoAppsDialog : DialogFragment() {
    companion object {
        private const val TAG = "auto_apps"
        fun show(activity: AppCompatActivity) {
            if (!activity.supportFragmentManager.isStateSaved &&
                activity.supportFragmentManager.findFragmentByTag(TAG) == null) {
                AutoAppsDialog().showNow(activity.supportFragmentManager, TAG)
            }
        }
        private fun isXiaomi(): Boolean = listOf("xiaomi", "redmi", "poco", "blackshark", "black shark").any {
            Build.MANUFACTURER.lowercase(Locale.ROOT).contains(it) || Build.BRAND.lowercase(Locale.ROOT).contains(it)
        }
    }

    private data class App(val label: String, val pkg: String, val icon: Drawable?)
    private var hideAdvice = false
    private lateinit var root: LinearLayout
    private var phase = "permission"
    private var query = ""
    private val draft = linkedSetOf<String>()
    private var apps: List<App>? = null
    private var loadJob: Job? = null
    private lateinit var body: LinearLayout
    private val usageSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        phase = if (hasUsageAccess()) nextPhase() else "denied"
        render()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prefs = requireContext().getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        draft.addAll(state?.getStringArrayList("draft") ?: prefs.getStringSet("allowed_apps", emptySet()).orEmpty())
        query = state?.getString("query").orEmpty()
        hideAdvice = state?.getBoolean("hideAdvice") ?: false
        phase = state?.getString("phase") ?: if (hasUsageAccess()) nextPhase() else "permission"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("draft", ArrayList(draft))
        outState.putString("query", query)
        outState.putString("phase", phase)
        outState.putBoolean("hideAdvice", hideAdvice)
    }

    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = requireContext()
        root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body = root
        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.auto_apps_title)
            .setView(root)
            .setNegativeButton(R.string.auto_apps_cancel, null)
            .setPositiveButton(R.string.auto_apps_save, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        val alert = dialog as AlertDialog
        alert.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            requireContext().getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
                .edit().putStringSet("allowed_apps", draft.toSet()).apply()
            dismiss()
        }
        // A restarted window may have replaced the views captured by the loader.
        loadJob?.cancel()
        loadJob = null
        render()
    }

    private fun nextPhase() = if (isXiaomi() && !requireContext()
        .getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        .getBoolean("xiaomi_warning_dismissed", false)) "advice" else "picker"

    private fun hasUsageAccess(): Boolean = try {
        val context = requireContext()
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    private fun text(id: Int) = TextView(requireContext()).also {
        it.setText(id)
        it.setPadding(0, 8, 0, 16)
        body.addView(it)
    }

    private fun button(id: Int, action: () -> Unit) {
        body.addView(Button(requireContext()).apply {
            setText(id)
            isAllCaps = false
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun render() {
        if (!isAdded || !::body.isInitialized) return
        root.removeAllViews()
        body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        if (phase == "picker") {
            dialog?.window?.setLayout(
                minOf(resources.displayMetrics.widthPixels, dp(640)),
                minOf(resources.displayMetrics.heightPixels, dp(860))
            )
            root.addView(body, LinearLayout.LayoutParams(-1, -1))
        } else {
            root.addView(ScrollView(requireContext()).apply { addView(body) }, LinearLayout.LayoutParams(-1, -1))
        }
        val save = (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)
        save?.visibility = if (phase == "picker") View.VISIBLE else View.GONE
        save?.isEnabled = apps != null
        when (phase) {
            "permission", "denied", "settings" -> {
                text(if (phase == "permission") R.string.auto_apps_usage else R.string.auto_apps_denied)
                button(if (phase == "permission") R.string.auto_apps_open_usage else R.string.auto_apps_retry) {
                    if (hasUsageAccess()) {
                        phase = nextPhase()
                        render()
                    } else {
                        phase = "settings"
                        try {
                            usageSettings.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } catch (_: Exception) {
                            phase = "denied"
                            render()
                            settingsError()
                        }
                    }
                }
            }
            "advice" -> {
                text(R.string.auto_apps_battery)
                button(R.string.auto_apps_open_settings) {
                    val context = requireContext()
                    try {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    } catch (_: Exception) {
                        try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        catch (_: Exception) { settingsError() }
                    }
                }
                body.addView(CheckBox(requireContext()).apply {
                    setText(R.string.auto_apps_dont_show_again)
                    isChecked = hideAdvice
                    setOnCheckedChangeListener { _, checked -> hideAdvice = checked }
                })
                button(R.string.auto_apps_continue) {
                    if (hideAdvice) requireContext().getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("xiaomi_warning_dismissed", true).apply()
                    phase = "picker"
                    render()
                }
            }
            "picker" -> renderPicker()
        }
    }

    private fun settingsError() = Toast.makeText(requireContext(), R.string.auto_apps_settings_error, Toast.LENGTH_LONG).show()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun renderPicker() {
        val explanation = MaterialCardView(requireContext()).apply {
            setCardBackgroundColor(resources.getColor(R.color.md3_surface, null))
            radius = dp(12).toFloat()
            strokeWidth = dp(1)
            strokeColor = resources.getColor(R.color.md3_primary_variant, null)
            val copy = TextView(context).apply {
                setText(R.string.auto_apps_explanation)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(resources.getColor(R.color.md3_primary, null))
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            addView(ScrollView(context).apply { addView(copy) })
        }
        body.addView(explanation, LinearLayout.LayoutParams(-1, dp(if (resources.configuration.screenHeightDp < 500) 56 else 104)).apply { bottomMargin = dp(10) })

        val count = TextView(requireContext()).apply {
            text = getString(R.string.auto_apps_count, draft.size)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.md3_primary, null))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(dp(2), 0, dp(2), dp(8))
        }
        body.addView(count)
        fun updateCount() { count.text = getString(R.string.auto_apps_count, draft.size) }

        val searchLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.auto_apps_search)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        }
        val search = TextInputEditText(searchLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(query)
            contentDescription = getString(R.string.auto_apps_search)
        }
        searchLayout.addView(search, LinearLayout.LayoutParams(-1, -2))
        body.addView(searchLayout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val status = TextView(requireContext()).apply {
            setText(R.string.auto_apps_loading)
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(dp(16), dp(20), dp(16), dp(20))
        }
        val progress = ProgressBar(requireContext()).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val statePanel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(status, LinearLayout.LayoutParams(-1, -2))
        }
        val adapter = AppAdapter { updateCount() }
        val rows = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            setPadding(dp(2), dp(4), dp(2), dp(4))
            clipToPadding = false
        }
        val listSurface = MaterialCardView(requireContext()).apply {
            setCardBackgroundColor(resources.getColor(R.color.md3_surface, null))
            radius = dp(12).toFloat()
            strokeWidth = dp(1)
            strokeColor = resources.getColor(R.color.md3_primary_variant, null)
            addView(FrameLayout(context).apply {
                addView(rows, FrameLayout.LayoutParams(-1, -1))
                addView(ScrollView(context).apply { addView(statePanel) }, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            }, ViewGroup.LayoutParams(-1, -1))
        }
        body.addView(listSurface, LinearLayout.LayoutParams(-1, 0, 1f))

        fun filter() {
            val loaded = apps ?: return
            val visible = loaded.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
            progress.visibility = View.GONE
            statePanel.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
            status.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
            status.setText(if (loaded.isEmpty()) R.string.auto_apps_empty else R.string.auto_apps_no_results)
            adapter.submitList(visible)
        }
        search.doAfterTextChanged { query = it.toString(); filter() }
        if (apps != null) {
            filter()
            if (apps!!.isEmpty()) button(R.string.auto_apps_retry) { apps = null; render() }
        } else {
            if (loadJob?.isActive == true) return
            val context = requireContext().applicationContext
            loadJob = lifecycleScope.launch {
                try {
                    val loaded = withContext(Dispatchers.IO) {
                        val pm = context.packageManager
                        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                            .filter { it.activityInfo.packageName != context.packageName }
                            .distinctBy { it.activityInfo.packageName }
                            .map {
                                val pkg = it.activityInfo.packageName
                                App(runCatching { it.loadLabel(pm).toString() }.getOrDefault(pkg), pkg,
                                    runCatching { it.loadIcon(pm) }.getOrNull())
                            }
                            .sortedWith(compareBy<App> { it.label.lowercase(Locale.ROOT) }.thenBy { it.pkg })
                    }
                    apps = loaded
                    filter()
                    (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    if (loaded.isEmpty()) button(R.string.auto_apps_retry) { apps = null; render() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    progress.visibility = View.GONE
                    status.setText(R.string.auto_apps_load_error)
                    status.visibility = View.VISIBLE
                    button(R.string.auto_apps_retry) { apps = null; render() }
                } finally { loadJob = null }
            }
        }
    }

    private inner class AppAdapter(private val changed: () -> Unit) :
        ListAdapter<App, AppHolder>(object : DiffUtil.ItemCallback<App>() {
            override fun areItemsTheSame(old: App, new: App) = old.pkg == new.pkg
            override fun areContentsTheSame(old: App, new: App) = old == new
        }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppHolder =
            AppHolder(CheckBox(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                    setMargins(dp(6), dp(4), dp(6), dp(4))
                }
                minHeight = dp(80)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundResource(R.drawable.auto_apps_row)
                buttonTintList = ColorStateList.valueOf(resources.getColor(R.color.md3_primary, null))
                compoundDrawablePadding = dp(12)
            })

        override fun onBindViewHolder(holder: AppHolder, position: Int) {
            val app = getItem(position)
            holder.row.apply {
                setOnCheckedChangeListener(null)
                text = SpannableString("${app.label}\n${app.pkg}").apply {
                    setSpan(StyleSpan(Typeface.BOLD), 0, app.label.length, 0)
                    setSpan(RelativeSizeSpan(0.78f), app.label.length + 1, length, 0)
                    val secondary = TypedValue()
                    context.theme.resolveAttribute(android.R.attr.textColorSecondary, secondary, true)
                    val color = if (secondary.resourceId != 0) context.getColorStateList(secondary.resourceId).defaultColor else secondary.data
                    setSpan(ForegroundColorSpan(color), app.label.length + 1, length, 0)
                }
                isChecked = app.pkg in draft
                val size = (40 * resources.displayMetrics.density).toInt()
                app.icon?.setBounds(0, 0, size, size)
                setCompoundDrawablesRelative(app.icon, null, null, null)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) draft.add(app.pkg) else draft.remove(app.pkg)
                    changed()
                }
            }
        }
    }

    // A full-row native checkable node: label, package and checked state; icon is decorative.
    private class AppHolder(val row: CheckBox) : RecyclerView.ViewHolder(row)

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        super.onDestroyView()
    }
}

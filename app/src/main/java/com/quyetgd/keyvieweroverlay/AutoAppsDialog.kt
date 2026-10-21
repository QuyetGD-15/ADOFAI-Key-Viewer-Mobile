package com.quyetgd.keyvieweroverlay

import android.app.AppOpsManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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

/** Auto-start app picker. Draft selection is written only when the user taps Save. */
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
            Build.MANUFACTURER.lowercase(Locale.ROOT).contains(it) ||
                Build.BRAND.lowercase(Locale.ROOT).contains(it)
        }
    }

    private data class App(val label: String, val pkg: String, val icon: Drawable?)

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var footer: LinearLayout
    private var phase = "permission"
    private var query = ""
    private var hideAdvice = false
    private val draft = linkedSetOf<String>()
    private var apps: List<App>? = null
    private var loadJob: Job? = null

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
        outState.putStringArrayList("draft", ArrayList(draft))
        outState.putString("query", query)
        outState.putString("phase", phase)
        outState.putBoolean("hideAdvice", hideAdvice)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateDialog(state: Bundle?): Dialog {
        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#211F26"))
        }
        content = LinearLayout(requireContext())
        footer = LinearLayout(requireContext())
        return MaterialAlertDialogBuilder(requireContext())
            .setView(root)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )
            val metrics = resources.displayMetrics
            setLayout(
                (metrics.widthPixels * 0.94f).toInt().coerceAtMost(dp(660)),
                (metrics.heightPixels * 0.90f).toInt().coerceAtMost(dp(860))
            )
        }
        loadJob?.cancel()
        loadJob = null
        render()
        root.post { resizeWindowToContent() }
    }

    private fun resizeWindowToContent() {
        val window = dialog?.window ?: return
        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels * 0.94f).toInt().coerceAtMost(dp(660))
        val maxHeight = (metrics.heightPixels * 0.82f).toInt().coerceAtMost(dp(760))
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun nextPhase(): String = if (
        isXiaomi() && !requireContext().getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
            .getBoolean("xiaomi_warning_dismissed", false)
    ) "advice" else "picker"

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

    private fun render() {
        if (!isAdded || !::root.isInitialized) return
        root.removeAllViews()
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), 0, dp(22), 0)
        }
        footer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(14))
        }

        addHeader()
        if (phase == "picker") {
            root.addView(content, LinearLayout.LayoutParams(-1, -2))
        } else {
            root.addView(ScrollView(requireContext()).apply {
                isFillViewport = true
                addView(content)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))

        when (phase) {
            "permission", "denied", "settings" -> renderPermission()
            "advice" -> renderXiaomiAdvice()
            "picker" -> renderPicker()
        }
    }

    private fun addHeader() {
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(10))
        }
        val top = LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        titleBlock.addView(TextView(requireContext()).apply {
            setText(R.string.auto_apps_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            setTypeface(typeface, Typeface.BOLD)
        })
        titleBlock.addView(TextView(requireContext()).apply {
            text = when (phase) {
                "permission", "denied", "settings" -> getString(R.string.usage_stats_required)
                "advice" -> getString(R.string.xiaomi_warning_title)
                "picker" -> getString(R.string.auto_apps_picker_hint)
                else -> getString(R.string.usage_stats_required)
            }
            setTextColor(if (phase == "advice") 0xFFFFC857.toInt() else 0xFFBDB8C8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        })
        top.addView(titleBlock)
        if (phase == "picker") {
            top.addView(TextView(requireContext()).apply {
                text = "ⓘ"
                contentDescription = getString(R.string.auto_apps_explanation)
                gravity = Gravity.CENTER
                setTextColor(0xFFD8C7FF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
                setOnClickListener { showInfoDialog() }
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            })
        }
        top.addView(TextView(requireContext()).apply {
            text = "×"
            contentDescription = getString(R.string.auto_apps_cancel)
            gravity = Gravity.CENTER
            setTextColor(0xFFD8C7FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 27f)
            setOnClickListener { dismiss() }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })
        header.addView(top)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showInfoDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auto_apps_info_title)
            .setMessage(R.string.auto_apps_explanation)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun renderPermission() {
        val card = card(0x3326B9B0, R.color.md3_primary_variant)
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        box.addView(TextView(requireContext()).apply {
            text = if (phase == "permission") "①  Quyền Truy cập dữ liệu sử dụng" else "①  Quyền chưa được cấp"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(TextView(requireContext()).apply {
            setText(if (phase == "permission") R.string.auto_apps_usage else R.string.auto_apps_denied)
            setTextColor(0xFFD0CBD7.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(10), 0, dp(14))
        })
        val action = button(if (phase == "permission") R.string.auto_apps_open_usage else R.string.auto_apps_retry, true) {
            if (hasUsageAccess()) {
                phase = nextPhase()
                render()
            } else {
                phase = "settings"
                try { usageSettings.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                catch (_: Exception) { phase = "denied"; render(); settingsError() }
            }
        }
        box.addView(action)
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        addFooterCancel()
    }

    private fun renderXiaomiAdvice() {
        val warning = card(0x332F2612, null).apply { strokeColor = 0xFFFFC857.toInt() }
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        box.addView(TextView(requireContext()).apply {
            text = getString(R.string.auto_apps_xiaomi_title)
            setTextColor(0xFFFFD166.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(TextView(requireContext()).apply {
            setText(R.string.auto_apps_xiaomi_short)
            setTextColor(0xFFFFE7A6.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(8), 0, 0)
        })
        warning.addView(box)
        content.addView(warning, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val open = button(R.string.auto_apps_open_settings, true) {
            val context = requireContext()
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
            catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { settingsError() } }
        }
        content.addView(open, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        content.addView(CheckBox(requireContext()).apply {
            setText(R.string.auto_apps_dont_show_again)
            setTextColor(0xFFD0CBD7.toInt())
            isChecked = hideAdvice
            setOnCheckedChangeListener { _, checked -> hideAdvice = checked }
        })
        addFooterCancel()
        addFooterAction(R.string.auto_apps_continue, true) {
            if (hideAdvice) requireContext().getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
                .edit().putBoolean("xiaomi_warning_dismissed", true).apply()
            phase = "picker"
            render()
        }
    }

    private fun renderPicker() {
        val count = TextView(requireContext()).apply {
            text = getString(R.string.auto_apps_count, draft.size)
            setTextColor(0xFFD8C7FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(2), 0, 0, dp(8))
        }
        content.addView(count)
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
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            setOnTouchListener { view, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    focusAndShowKeyboard(view)
                }
                true
            }
            setOnFocusChangeListener { view, focused -> if (focused) focusAndShowKeyboard(view) }
        }
        searchLayout.addView(search)
        content.addView(searchLayout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val status = TextView(requireContext()).apply {
            setText(R.string.auto_apps_loading)
            gravity = Gravity.CENTER
            setTextColor(0xFFBDB8C8.toInt())
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        val progress = ProgressBar(requireContext())
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
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipToPadding = false
        }
        val listSurface = card(0x331E1E1E, R.color.md3_primary_variant)
        listSurface.addView(FrameLayout(requireContext()).apply {
            addView(rows, FrameLayout.LayoutParams(-1, -1))
            addView(statePanel, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        }, ViewGroup.LayoutParams(-1, -1))
        val listHeight = minOf(dp(360), (resources.displayMetrics.heightPixels * 0.40f).toInt())
        content.addView(listSurface, LinearLayout.LayoutParams(-1, listHeight))

        fun filter() {
            val loaded = apps ?: return
            val visible = loaded.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
            progress.visibility = View.GONE
            statePanel.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
            status.text = if (loaded.isEmpty()) getString(R.string.auto_apps_empty) else getString(R.string.auto_apps_no_results)
            adapter.submitList(visible)
        }
        search.doAfterTextChanged { query = it.toString(); filter() }
        addFooterCancel()
        addFooterAction(R.string.auto_apps_save, true) {
            requireContext().getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
                .edit().putStringSet("allowed_apps", draft.toSet()).apply()
            dismiss()
        }

        if (apps != null) {
            filter()
            if (apps!!.isEmpty()) addFooterAction(R.string.auto_apps_retry, false) { apps = null; render() }
        } else if (loadJob?.isActive != true) {
            val context = requireContext().applicationContext
            loadJob = lifecycleScope.launch {
                try {
                    apps = withContext(Dispatchers.IO) {
                        val pm = context.packageManager
                        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                            .filter { it.activityInfo.packageName != context.packageName }
                            .distinctBy { it.activityInfo.packageName }
                            .map {
                                val pkg = it.activityInfo.packageName
                                App(runCatching { it.loadLabel(pm).toString() }.getOrDefault(pkg), pkg, runCatching { it.loadIcon(pm) }.getOrNull())
                            }
                            .sortedWith(compareBy<App> { it.label.lowercase(Locale.ROOT) }.thenBy { it.pkg })
                    }
                    filter()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    progress.visibility = View.GONE
                    status.setText(R.string.auto_apps_load_error)
                } finally { loadJob = null }
            }
        }
    }

    private fun focusAndShowKeyboard(view: View) {
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        val window = dialog?.window
        window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        view.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.restartInput(view)
            val shown = imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            if (!shown) imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }, 180L)
    }

    private fun addFooterCancel() = addFooterAction(R.string.auto_apps_cancel, false) { dismiss() }

    private fun addFooterAction(label: Int, primary: Boolean, action: () -> Unit) {
        val b = Button(requireContext()).apply {
            setText(label)
            isAllCaps = false
            minHeight = dp(48)
            setOnClickListener { action() }
            if (primary) {
                setTextColor(0xFFE5D9FF.toInt())
                backgroundTintList = ColorStateList.valueOf(0xFF49356D.toInt())
            } else setTextColor(0xFFD8C7FF.toInt())
        }
        footer.addView(b, LinearLayout.LayoutParams(0, dp(48), if (primary) 1f else 0f).apply {
            if (!primary) { width = dp(104); marginEnd = dp(8) }
        })
    }

    private fun button(label: Int, primary: Boolean, action: () -> Unit): Button = Button(requireContext()).apply {
        setText(label)
        isAllCaps = false
        minHeight = dp(48)
        setOnClickListener { action() }
        if (primary) {
            setTextColor(0xFFE5D9FF.toInt())
            backgroundTintList = ColorStateList.valueOf(0xFF49356D.toInt())
        }
    }

    private fun card(background: Int, stroke: Int?): MaterialCardView = MaterialCardView(requireContext()).apply {
        setCardBackgroundColor(background)
        radius = dp(14).toFloat()
        strokeWidth = dp(1)
        strokeColor = stroke?.let { resources.getColor(it, null) } ?: 0xFFFFC857.toInt()
    }

    private fun settingsError() = Toast.makeText(requireContext(), R.string.auto_apps_settings_error, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class AppAdapter(private val changed: () -> Unit) : ListAdapter<App, AppHolder>(object : DiffUtil.ItemCallback<App>() {
        override fun areItemsTheSame(old: App, new: App) = old.pkg == new.pkg
        override fun areContentsTheSame(old: App, new: App) = old == new
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppHolder = AppHolder(CheckBox(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
            minHeight = dp(72)
            setPadding(dp(12), dp(10), dp(12), dp(10))
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
                    setSpan(ForegroundColorSpan(0xFFBDB8C8.toInt()), app.label.length + 1, length, 0)
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

    private class AppHolder(val row: CheckBox) : RecyclerView.ViewHolder(row)

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        super.onDestroyView()
    }
}

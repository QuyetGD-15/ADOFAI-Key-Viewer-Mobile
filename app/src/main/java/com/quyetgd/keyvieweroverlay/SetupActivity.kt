package com.quyetgd.keyvieweroverlay

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class SetupActivity : AppCompatActivity() {

    private lateinit var setupFlipper: ViewFlipper
    private lateinit var btnBack: MaterialButton
    private lateinit var btnNext: MaterialButton
    private var currentStep = 0

    private var selectedLanguage: String? = null
    private var selectedInputSource: String? = null
    private var selectedKeyMode: Int = 6

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("SAVED_STEP", currentStep)
        outState.putString("SAVED_LANG", selectedLanguage)
        outState.putString("SAVED_SOURCE", selectedInputSource)
        outState.putInt("SAVED_MODE", selectedKeyMode)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentStep = savedInstanceState.getInt("SAVED_STEP", 0)
        selectedLanguage = savedInstanceState.getString("SAVED_LANG")
        selectedInputSource = savedInstanceState.getString("SAVED_SOURCE")
        selectedKeyMode = savedInstanceState.getInt("SAVED_MODE", 6)
        
        setupFlipper.displayedChild = currentStep
        updateUIForStep()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_setup)

        setupFlipper = findViewById(R.id.setupFlipper)
        btnBack = findViewById(R.id.btnSetupBack)
        btnNext = findViewById(R.id.btnSetupNext)

        setupStepListeners()
        updateUIForStep()
    }

    private fun setupStepListeners() {
        // Step 1: Language
        findViewById<Button>(R.id.btnLangVi).setOnClickListener {
            selectedLanguage = "vi"
            updateLanguage("vi")
            moveToNextStep()
        }
        findViewById<Button>(R.id.btnLangEn).setOnClickListener {
            selectedLanguage = "en"
            updateLanguage("en")
            moveToNextStep()
        }

        // Step 2: Input Source
        findViewById<MaterialCardView>(R.id.cardStepTouch).setOnClickListener {
            selectedInputSource = "touch"
            moveToNextStep()
        }
        findViewById<MaterialCardView>(R.id.cardStepKeyboard).setOnClickListener {
            selectedInputSource = "keyboard"
            moveToNextStep()
        }

        // Step 3: Permission
        findViewById<Button>(R.id.btnStepGrantPermission).setOnClickListener {
            if (selectedInputSource == "touch") {
                if (!Shizuku.pingBinder()) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                    } catch (e: Exception) {
                        Toast.makeText(this, getString(R.string.shizuku_not_found), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Shizuku.requestPermission(100)
                }
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        // Step 4: Key Mode
        findViewById<MaterialButtonToggleGroup>(R.id.toggleStepKeyMode).addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedKeyMode = when (checkedId) {
                    R.id.btnMode4 -> 4
                    R.id.btnMode6 -> 6
                    R.id.btnMode8 -> 8
                    R.id.btnMode10 -> 10
                    else -> 6
                }
            }
        }

        // Step 5: Advanced Settings
        findViewById<Button>(R.id.btnSetupDetails).setOnClickListener {
            if (selectedInputSource == "touch") {
                startActivity(Intent(this, HitboxConfigActivity::class.java))
            } else {
                showKeyMappingDialog()
            }
        }

        // Step 6: UI Customization
        findViewById<Button>(R.id.btnSetupKeyViewer).setOnClickListener {
            startActivity(Intent(this, KeyViewerConfigActivity::class.java))
        }

        // Step 7: Final
        findViewById<Button>(R.id.btnStepSelectApps).setOnClickListener {
            if (!hasUsageStatsPermission()) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } else {
                checkXiaomiAndOpenAppList()
            }
        }

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        findViewById<TextView>(R.id.tvStepSkipApps).setOnClickListener {
            finishSetup()
        }

        // Bottom Bar
        btnBack.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                setupFlipper.setInAnimation(this, android.R.anim.slide_in_left)
                setupFlipper.setOutAnimation(this, android.R.anim.slide_out_right)
                setupFlipper.displayedChild = currentStep
                updateUIForStep()
            }
        }

        btnNext.setOnClickListener {
            handleNextClick()
        }
    }

    private fun handleNextClick() {
        when (currentStep) {
            0 -> {
                if (selectedLanguage == null) {
                    Toast.makeText(this, getString(R.string.setup_toast_select_lang), Toast.LENGTH_SHORT).show()
                } else moveToNextStep()
            }
            1 -> {
                if (selectedInputSource == null) {
                    Toast.makeText(this, getString(R.string.setup_toast_select_source), Toast.LENGTH_SHORT).show()
                } else moveToNextStep()
            }
            2 -> { // Permissions
                val hasOverlay = Settings.canDrawOverlays(this)
                val hasInputPerm = if (selectedInputSource == "touch") {
                    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } else {
                    isAccessibilityServiceEnabled(this, TouchRendererService::class.java)
                }

                if (hasOverlay && hasInputPerm) {
                    moveToNextStep()
                } else {
                    val msg = if (!hasInputPerm && !hasOverlay) getString(R.string.setup_toast_grant_both)
                    else if (!hasInputPerm) (if (selectedInputSource == "touch") getString(R.string.setup_toast_grant_shizuku) else getString(R.string.setup_toast_grant_acc))
                    else getString(R.string.setup_toast_grant_overlay)
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            3 -> {
                val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
                pref.edit().putInt("current_key_mode", selectedKeyMode).apply()
                moveToNextStep()
            }
            4 -> moveToNextStep()
            5 -> moveToNextStep()
            6 -> finishSetup()
        }
    }

    private fun moveToNextStep() {
        if (currentStep < 6) {
            currentStep++
            setupFlipper.setInAnimation(this, R.anim.in_from_right)
            setupFlipper.setOutAnimation(this, R.anim.out_to_left)
            setupFlipper.displayedChild = currentStep
            updateUIForStep()
        }
    }

    private fun updateUIForStep() {
        btnBack.visibility = if (currentStep == 0) View.INVISIBLE else View.VISIBLE
        btnNext.text = if (currentStep == 6) getString(R.string.setup_btn_finish) else getString(R.string.setup_btn_next)

        when (currentStep) {
            2 -> { // Step 3: Permissions
                val tvTitle = findViewById<TextView>(R.id.tvStepPermissionTitle)
                val tvDesc = findViewById<TextView>(R.id.tvStepPermissionDesc)
                val tvStatus = findViewById<TextView>(R.id.tvStepPermissionStatus)
                val btnGrant = findViewById<Button>(R.id.btnStepGrantPermission)
                
                val tvOverlayStatus = findViewById<TextView>(R.id.tvOverlayStatus)

                if (selectedInputSource == "touch") {
                    tvTitle.text = getString(R.string.setup_step3_shizuku_title)
                    tvDesc.text = getString(R.string.setup_step3_shizuku_desc)
                    val isGranted = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    tvStatus.text = if (isGranted) getString(R.string.setup_step3_status_ready) else getString(R.string.setup_step3_status_shizuku_denied)
                    tvStatus.setTextColor(if (isGranted) Color.GREEN else Color.RED)
                    btnGrant.text = getString(R.string.setup_step3_btn_shizuku)
                } else {
                    tvTitle.text = getString(R.string.setup_step3_accessibility_title)
                    tvDesc.text = getString(R.string.setup_step3_accessibility_desc)
                    val isEnabled = isAccessibilityServiceEnabled(this, TouchRendererService::class.java)
                    tvStatus.text = if (isEnabled) getString(R.string.setup_step3_status_ready) else getString(R.string.setup_step3_status_acc_pending)
                    tvStatus.setTextColor(if (isEnabled) Color.GREEN else Color.RED)
                    btnGrant.text = getString(R.string.setup_step3_btn_acc)
                }

                val hasOverlay = Settings.canDrawOverlays(this)
                tvOverlayStatus.text = getString(R.string.setup_step3_overlay_status, if (hasOverlay) getString(R.string.setup_step3_status_granted) else getString(R.string.setup_step3_status_denied))
                tvOverlayStatus.setTextColor(if (hasOverlay) Color.GREEN else Color.RED)
            }
            4 -> { // Step 5: Advanced Settings
                val btnDetails = findViewById<Button>(R.id.btnSetupDetails)
                if (selectedInputSource == "touch") {
                    btnDetails.text = getString(R.string.setup_step5_btn_hitbox)
                } else {
                    btnDetails.text = getString(R.string.setup_step5_btn_mapping)
                }
            }
        }
    }

    private fun updateLanguage(lang: String) {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        pref.edit().putString("app_language", lang).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    private fun finishSetup() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        pref.edit().apply {
            putString("input_source", selectedInputSource)
            putBoolean("is_first_setup_done", true)
        }.apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out android.accessibilityservice.AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        val componentName = android.content.ComponentName(context, service).flattenToString()
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(componentName, ignoreCase = true)) return true
        }
        return false
    }

    private fun showKeyMappingDialog() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val keyMode = selectedKeyMode
        var waitingIndex = -1

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(24).toFloat()
                setColor(Color.parseColor("#1E1E1E"))
            }
        }

        val tvTitle = TextView(this).apply {
            text = getString(R.string.mapping_dialog_title)
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dpToPx(16) }
        }
        root.addView(tvTitle)

        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(container)

        val rowViews = mutableListOf<Pair<TextView, TextView>>()
        for (i in 0 until keyMode) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                isClickable = true
                isFocusable = true
                val outValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(12).toFloat()
                    setColor(Color.parseColor("#2A2A2A"))
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dpToPx(8) }
            }
            val tvLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                text = getString(R.string.mapping_row_key, i + 1); setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val savedKeyName = pref.getString("key_name_${keyMode}_$i", getString(R.string.mapping_status_none))
            val tvKey = TextView(this).apply {
                text = savedKeyName; setTextColor(if (savedKeyName == getString(R.string.mapping_status_none)) Color.GRAY else Color.parseColor("#A78BFA")); textSize = 14f
            }
            row.addView(tvLabel); row.addView(tvKey); container.addView(row)
            rowViews.add(tvLabel to tvKey)
            row.setOnClickListener {
                waitingIndex = i
                rowViews.forEachIndexed { idx, p -> 
                    val kn = pref.getString("key_name_${keyMode}_$idx", getString(R.string.mapping_status_none))
                    p.second.text = if (idx == i) getString(R.string.mapping_status_waiting) else kn
                }
            }
        }

        val btnDone = TextView(this).apply {
            text = getString(R.string.mapping_btn_done); setTextColor(Color.parseColor("#A78BFA")); textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.END; setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = dpToPx(24) }
        }
        root.addView(btnDone)

        val dialog = MaterialAlertDialogBuilder(this).setView(root).create()
        btnDone.setOnClickListener { dialog.dismiss() }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (waitingIndex != -1 && event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                val kn = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "")
                pref.edit().putInt("key_code_${keyMode}_$waitingIndex", keyCode).putString("key_name_${keyMode}_$waitingIndex", kn).apply()
                rowViews[waitingIndex].second.text = kn
                waitingIndex = -1
                true
            } else false
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkXiaomiAndOpenAppList() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        if (manufacturer.contains("xiaomi") || manufacturer.contains("poco") || manufacturer.contains("redmi")) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.xiaomi_dialog_title))
                .setMessage(getString(R.string.xiaomi_dialog_msg))
                .setPositiveButton(getString(R.string.xiaomi_dialog_btn)) { _, _ ->
                    openAppSelectionScreen()
                }
                .setCancelable(false)
                .show()
        } else {
            openAppSelectionScreen()
        }
    }

    private fun openAppSelectionScreen() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                loadAndCategorizeApps()
            }
            showAppSelectionDialog(result.first, result.second)
        }
    }

    private data class AppItem(
        val label: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable?,
        var isChecked: Boolean = false,
        val isHeader: Boolean = false,
        val headerTitle: String = ""
    )

    private inner class AppAdapter(
        val items: List<AppItem>,
        val onCheckedChange: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = if (items[position].isHeader) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val tv = TextView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -2)
                    setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.YELLOW)
                }
                object : RecyclerView.ViewHolder(tv) {}
            } else {
                val layout = LinearLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -2)
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                    isClickable = true; isFocusable = true
                    val outValue = TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                }
                val icon = ImageView(parent.context).apply { layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)) }
                val name = TextView(parent.context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpToPx(12) }
                    setTextColor(Color.WHITE)
                }
                val cb = CheckBox(parent.context).apply { isFocusable = false; isClickable = false }
                layout.addView(icon); layout.addView(name); layout.addView(cb)
                
                layout.setTag(R.id.hitbox1, icon)
                layout.setTag(R.id.hitbox2, name)
                layout.setTag(R.id.hitbox3, cb)
                
                object : RecyclerView.ViewHolder(layout) {}
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (item.isHeader) {
                (holder.itemView as TextView).text = item.headerTitle
            } else {
                val icon = holder.itemView.getTag(R.id.hitbox1) as ImageView
                val name = holder.itemView.getTag(R.id.hitbox2) as TextView
                val cb = holder.itemView.getTag(R.id.hitbox3) as CheckBox
                icon.setImageDrawable(item.icon)
                name.text = item.label
                cb.isChecked = item.isChecked
                holder.itemView.setOnClickListener {
                    item.isChecked = !item.isChecked
                    cb.isChecked = item.isChecked
                    onCheckedChange(position, item.isChecked)
                }
            }
        }
        override fun getItemCount(): Int = items.size
    }

    private fun loadAndCategorizeApps(): Pair<List<AppItem>, List<AppItem>> {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val savedApps = pref.getStringSet("allowed_apps", emptySet()) ?: emptySet()
        val recommendedKeywords = listOf("adofai", "a dance of fire and ice", "geometry dash")
        val allApps = resolvedInfos
            .filter { it.activityInfo.packageName != packageName }
            .map { 
                AppItem(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm),
                    isChecked = savedApps.contains(it.activityInfo.packageName)
                )
            }
        val recommended = allApps.filter { item -> recommendedKeywords.any { kw -> item.label.contains(kw, ignoreCase = true) } }.sortedBy { it.label }
        val others = allApps.filter { item -> !recommendedKeywords.any { kw -> item.label.contains(kw, ignoreCase = true) } }.sortedBy { it.label }
        return recommended to others
    }

    private fun showAppSelectionDialog(recommended: List<AppItem>, others: List<AppItem>) {
        val finalItems = mutableListOf<AppItem>()
        if (recommended.isNotEmpty()) {
            finalItems.add(AppItem("", "", null, isHeader = true, headerTitle = getString(R.string.header_recommended)))
            finalItems.addAll(recommended)
        }
        finalItems.add(AppItem("", "", null, isHeader = true, headerTitle = getString(R.string.header_others)))
        finalItems.addAll(others)

        val dialog = BottomSheetDialog(this, R.style.RoundedBottomSheetDialog)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E1E1E"))
        }
        val title = TextView(this).apply {
            text = getString(R.string.dialog_select_apps_title); textSize = 20f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE); setPadding(0, 0, 0, dpToPx(16))
        }
        val rv = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(this@SetupActivity)
        }
        rv.adapter = AppAdapter(finalItems) { _, _ -> }
        val btnOk = MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dpToPx(56)).apply { topMargin = dpToPx(16) }
            text = getString(android.R.string.ok); cornerRadius = dpToPx(16)
            setOnClickListener {
                val selectedSet = finalItems.filter { !it.isHeader && it.isChecked }.map { it.packageName }.toSet()
                getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE).edit().putStringSet("allowed_apps", selectedSet).apply()
                dialog.dismiss()
            }
        }
        root.addView(title); root.addView(rv); root.addView(btnOk)
        dialog.setContentView(root)
        root.layoutParams.height = (resources.displayMetrics.heightPixels * 0.7).toInt()
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        updateUIForStep()
    }
}

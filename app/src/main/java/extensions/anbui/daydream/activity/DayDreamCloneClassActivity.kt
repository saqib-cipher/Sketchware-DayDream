package extensions.anbui.daydream.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.besome.sketch.design.DesignActivity
import extensions.anbui.daydream.configs.Configs
import extensions.anbui.daydream.file.FileUtils
import extensions.anbui.daydream.project.ProjectDecryptor
import extensions.anbui.daydream.project.ProjectUnsavedData
import extensions.anbui.daydream.settings.DayDreamProjectSettings
import extensions.anbui.daydream.tools.ToolCore
import extensions.anbui.daydream.ui.DialogUtils
import pro.sketchware.R
import pro.sketchware.databinding.ActivityDaydreamCloneClassBinding


class DayDreamCloneClassActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDaydreamCloneClassBinding
    var projectID = ""
    var currentName = ""
    var currentViewData = ""

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.hasExtra("sc_id")) {
            projectID = intent.getStringExtra("sc_id")!!
        } else {
            finish()
            return
        }

        if (intent.hasExtra("current_name")) {
            currentName = intent.getStringExtra("current_name")!!
        } else {
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityDaydreamCloneClassBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initializeNow()
    }

    fun initializeNow() {
        currentViewData =
            ProjectDecryptor.decryptProjectFile(FileUtils.getInternalStorageDir() + Configs.projectUnsavedDataFolderDir + projectID + "/view")

        binding.collapsingToolbarLayout.subtitle = "from $currentName."

        binding.etNewname.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!ToolCore.isXMLNameValid(projectID, s.toString(), currentName, false, currentViewData)) {
                    binding.edNewname.error = "Please choose another name."
                } else {
                    binding.edNewname.error = null
                }
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                //;)
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                //;)
            }
        })

        binding.btnDone.setOnClickListener { _ -> startClone() }

        Handler(Looper.getMainLooper()).postDelayed({
            binding.edNewname.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etNewname, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    fun startClone() {
        if (ToolCore.isXMLNameValid(projectID, binding.etNewname.text.toString(), currentName, false, currentViewData)) {
            if (ProjectUnsavedData.cloneActivity(projectID, currentName, binding.etNewname.text.toString())) {
                saveDayDream(binding.etNewname.text.toString())
                DesignActivity.needReloadProjectData = true
                finish()
            } else {
                DialogUtils.oneDialog(
                    this,
                    "Cannot be cloned",
                    "An error occurred during cloning. Please try again later.",
                    "OK",
                    true,
                    R.drawable.ic_mtrl_warning,
                    true, null, null
                )
            }
        } else {
            DialogUtils.oneDialog(
                this,
                "Cannot be cloned",
                "Please choose a valid new name.",
                "OK",
                true,
                R.drawable.ic_mtrl_warning,
                true, null, null
            )
        }
    }

    fun saveDayDream(xmlName : String) {
        DayDreamProjectSettings.setEnableEdgeToEdge(
            projectID,
            xmlName,
            DayDreamProjectSettings.isEnableEdgeToEdge(Configs.currentProjectID, currentName)
        )
        DayDreamProjectSettings.setEnableWindowInsetsHandling(
            projectID,
            xmlName,
            DayDreamProjectSettings.isEnableWindowInsetsHandling(Configs.currentProjectID, currentName)
        )
        DayDreamProjectSettings.setDisableAutomaticPermissionRequests(
            projectID,
            xmlName,
            DayDreamProjectSettings.isDisableAutomaticPermissionRequests(Configs.currentProjectID, currentName)
        )
        DayDreamProjectSettings.setContentProtection(
            projectID,
            xmlName,
            DayDreamProjectSettings.isContentProtection(Configs.currentProjectID, currentName)
        )
        DayDreamProjectSettings.setImportWorkManager(
            projectID,
            xmlName,
            DayDreamProjectSettings.isImportWorkManager(Configs.currentProjectID, currentName)
        )
        DayDreamProjectSettings.setImportAndroidXMedia3(
            projectID,
            xmlName,
            DayDreamProjectSettings.isImportAndroidXMedia3(Configs.currentProjectID, currentName)
        )
        DayDreamProjectSettings.setImportAndroidXBrowser(
            projectID,
            xmlName,
            DayDreamProjectSettings.isImportAndroidXBrowser(Configs.currentProjectID, currentName)
        )
        DayDreamProjectSettings.setImportAndroidXCredentialManager(
            projectID,
            xmlName,
            DayDreamProjectSettings.isImportAndroidXCredentialManager(Configs.currentProjectID, currentName)
        )
        DayDreamProjectSettings.setImportShizuku(
            projectID,
            xmlName,
            DayDreamProjectSettings.isImportShizuku(Configs.currentProjectID, currentName)
        )
        DayDreamProjectSettings.setImportAndroidBilling(
            projectID,
            xmlName,
            DayDreamProjectSettings.isImportAndroidBilling(Configs.currentProjectID, currentName)
        )
        DayDreamProjectSettings.setActivityType(
            projectID,
            xmlName,
            DayDreamProjectSettings.getActivityType(Configs.currentProjectID, currentName)
        )
    }
}
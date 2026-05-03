package extensions.anbui.daydream.activity.project.git;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Objects;

import extensions.anbui.daydream.configs.Configs;
import extensions.anbui.daydream.ui.DialogUtils;
import extensions.anbui.daydream.file.FileUtils;
import extensions.anbui.daydream.git.DayDreamGitConfigs;
import extensions.anbui.daydream.git.GitApplyUtils;
import extensions.anbui.daydream.git.GitUtils;
import extensions.anbui.daydream.git.ZipImportUtils;
import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;
import pro.sketchware.databinding.ActivityDaydreamGitCloneBinding;

public class GitCloneActivity extends AppCompatActivity {

    private String TAG = Configs.universalTAG + "GitCloneActivity";
    private String projectID;
    private ActivityDaydreamGitCloneBinding binding;

    private final ActivityResultLauncher<Intent> zipPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result == null || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                startZipImport(uri);
            });

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDaydreamGitCloneBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(view -> onBackPressed());
        binding.btnDone.setOnClickListener(v -> startCloneProject());
        if (binding.btnImportZip != null) {
            binding.btnImportZip.setOnClickListener(v -> pickZipForImport());
        }
        if (getIntent() != null && getIntent().hasExtra("sc_id")) {
            projectID = getIntent().getStringExtra("sc_id");
        }
    }

    private void pickZipForImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream"
        });
        zipPicker.launch(intent);
    }

    private void startZipImport(Uri uri) {
        if (projectID == null || projectID.isEmpty()) {
            DialogUtils.oneDialog(this,
                    "The problem has been detected",
                    "Missing project id. Open this screen from a project to import a ZIP.",
                    "OK", true, R.drawable.ic_mtrl_warning, true, null, null);
            return;
        }

        View progressView = LayoutInflater.from(this).inflate(R.layout.progress_msg_box, null);
        LinearLayout linearProgress = progressView.findViewById(R.id.layout_progress);
        linearProgress.setPadding(0, 0, 0, 0);
        TextView progressText = progressView.findViewById(R.id.tv_progress);
        progressText.setText("Importing zip...");
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            boolean ok = ZipImportUtils.importZip(this, projectID, uri);
            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (ok) {
                    startApplyProject();
                } else {
                    DialogUtils.oneDialog(this,
                            "Error",
                            "Could not import the selected ZIP file.",
                            "OK", true, R.drawable.ic_mtrl_warning, true, null, null);
                }
            });
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void startCloneProject() {

        if (Objects.requireNonNull(binding.etRurl.getText()).toString().isEmpty()) {
            DialogUtils.oneDialog(this,
                    "The problem has been detected",
                    "Please enter the URL of the repository to clone.",
                    "OK",
                    true,
                    R.drawable.ic_mtrl_warning,
                    true, null, null);
            return;
        }

        DayDreamGitConfigs.setGitHubToken(projectID, "");
        DayDreamGitConfigs.setRemoteUrl(projectID, Objects.requireNonNull(binding.etRurl.getText()).toString());
        DayDreamGitConfigs.setBranch(projectID, Objects.requireNonNull(binding.etBranch.getText()).toString());

        try {
            FileUtils.deleteRecursive(new File(FileUtils.getInternalStorageDir() + Configs.gitFolderDir + projectID));
        } catch (Exception e) {
            Log.e(TAG, "startCloneProject: " + e.getMessage());
        }

        View progressView = LayoutInflater.from(this).inflate(R.layout.progress_msg_box, null);
        LinearLayout linear_progress = progressView.findViewById(R.id.layout_progress);
        linear_progress.setPadding(0, 0, 0, 0);
        TextView progress_text = progressView.findViewById(R.id.tv_progress);
        progress_text.setText("Cloning...");
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            boolean result = GitUtils.startClone(projectID,
                    Objects.requireNonNull(binding.etRurl.getText()).toString(),
                    "",
                    Objects.requireNonNull(binding.etBranch.getText()).toString());

            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (result) {
                    startApplyProject();
                } else {
                    DialogUtils.oneDialog(this,
                            "Error",
                            "Unable to clone from GitHub repository. Please try again later.",
                            "OK",
                            true,
                            R.drawable.ic_mtrl_warning,
                            true, null, null);
                }
            });
        }).start();
    }

    private void startApplyProject() {
        View progressView = LayoutInflater.from(this).inflate(R.layout.progress_msg_box, null);
        LinearLayout linear_progress = progressView.findViewById(R.id.layout_progress);
        linear_progress.setPadding(0,0,0,0);
        TextView progress_text = progressView.findViewById(R.id.tv_progress);
        progress_text.setText("Creating project...");
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            boolean result = GitApplyUtils.apply(projectID, progress_text);

            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (result) {
                    DialogUtils.oneDialog(this,
                            "Done",
                            "Project created successfully.",
                            "OK",
                            true,
                            R.drawable.ic_mtrl_check,
                            true, null, this::finish);

                    MainActivity.needRefreshProjectList = true;
                } else {
                    DialogUtils.oneDialog(this,
                            "Error",
                            "Unable to create project. That repository might not contain the project files.",
                            "OK",
                            true,
                            R.drawable.ic_mtrl_warning,
                            true, null, null);
                }
                GitUtils.remove(projectID);
            });
        }).start();
    }
}
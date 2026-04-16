package extensions.anbui.daydream.activity.project;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;

import extensions.anbui.daydream.activity.project.git.DayDreamGitActionsActivity;
import extensions.anbui.daydream.library.LibraryUtils;
import extensions.anbui.daydream.project.DRProjectTracker;
import extensions.anbui.daydream.tools.project.DayDreamCleanUpTemporaryFiles;
import extensions.anbui.daydream.tools.project.DayDreamCloneTool;
import pro.sketchware.databinding.ActivityDaydreamProjectToolBinding;

public class DayDreamProjectTool extends AppCompatActivity {

    private ActivityDaydreamProjectToolBinding binding;
    private String projectID;

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
        if (getIntent().hasExtra("sc_id")) {
            projectID = getIntent().getStringExtra("sc_id");
            DRProjectTracker.startNow(projectID);
        } else {
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        binding = ActivityDaydreamProjectToolBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(view -> onBackPressed());
        initialize();
    }

    private void initialize() {
        binding.lnBackup.setOnClickListener(v -> backup());
        binding.lnClone.setOnClickListener(v -> DayDreamCloneTool.showDialogNow(this, projectID));
        binding.lnCleanuptemporaryfiles.setOnClickListener(v -> DayDreamCleanUpTemporaryFiles.showDialogNow(this, projectID));

        if (LibraryUtils.isAllowUseGit()) {
            binding.lnGit.setOnClickListener(v -> {
                Intent intent = new Intent(this, DayDreamGitActionsActivity.class);
                intent.putExtra("sc_id", projectID);
                startActivity(intent);
            });
        } else {
            binding.tvGitnote.setText("Android 13+ required to use this feature.");
            binding.lnGit.setAlpha(0.5f);
            binding.lnGit.setEnabled(false);
        }
    }

    private void backup() {
        Intent intent = new Intent(this, DayDreamBackupTool.class);
        intent.putExtra("sc_id", projectID);
        startActivity(intent);
    }
}
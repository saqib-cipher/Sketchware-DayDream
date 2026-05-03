package extensions.anbui.daydream.activity.project.blocks;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Objects;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import extensions.anbui.daydream.configs.Configs;
import extensions.anbui.daydream.file.FileUtils;
import extensions.anbui.daydream.settings.FilePickerSettings;
import extensions.anbui.daydream.ui.DialogUtils;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityDaydreamAutoBlockGeneratorBinding;

/**
 * UI for the Auto Block Generator: paste/load source code, run pattern
 * detection, preview the generated block JSON, and persist it under
 * {@code .sketchware/resources/block/&lt;palette&gt;/block.json}.
 *
 * <p>Replaces the old per-snippet Code-to-Block flow with one that surfaces
 * reusable templates (setText, setOnClickListener, if, ...) instead of a
 * single ASDB capture of a literal snippet.
 */
public class AutoBlockGeneratorActivity extends AppCompatActivity {

    public static final String TAG = Configs.universalTAG + "AutoBlockGeneratorActivity";

    private ActivityDaydreamAutoBlockGeneratorBinding binding;

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

        binding = ActivityDaydreamAutoBlockGeneratorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(view -> onBackPressed());

        binding.btnPreview.setOnClickListener(v -> previewBlocks());
        binding.btnGenerate.setOnClickListener(v -> generateAndSave());
        binding.btnLoadFile.setOnClickListener(v -> pickCodeFile());
    }

    private String currentSource() {
        return binding.etCode.getText() == null ? "" : binding.etCode.getText().toString();
    }

    private String currentPalette() {
        String p = binding.etPalette.getText() == null ? "" : binding.etPalette.getText().toString().trim();
        return TextUtils.isEmpty(p) ? AutoBlockGenerator.DEFAULT_PALETTE : p;
    }

    private void previewBlocks() {
        String src = currentSource();
        if (TextUtils.isEmpty(src)) {
            Toast.makeText(this, "Paste some code first", Toast.LENGTH_LONG).show();
            return;
        }
        List<AutoBlockGenerator.Block> blocks =
                AutoBlockGenerator.generate(src, currentPalette());
        if (blocks.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("No patterns detected")
                    .setMessage("The parser didn't recognise any of: if, setText, "
                            + "setImageResource, setOnClickListener.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Generated " + blocks.size() + " block(s)")
                .setMessage(AutoBlockGenerator.toPrettyJson(blocks))
                .setPositiveButton("OK", null)
                .show();
    }

    private void generateAndSave() {
        String src = currentSource();
        if (TextUtils.isEmpty(src)) {
            Toast.makeText(this, "Paste some code first", Toast.LENGTH_LONG).show();
            return;
        }
        String palette = currentPalette();
        List<AutoBlockGenerator.Block> blocks = AutoBlockGenerator.generate(src, palette);
        if (blocks.isEmpty()) {
            DialogUtils.oneDialog(this,
                    "No patterns detected",
                    "The parser didn't find anything to convert. Try code that uses "
                            + "if, setText, setImageResource, or setOnClickListener.",
                    "OK", true, R.drawable.ic_mtrl_warning, true, null, null);
            return;
        }
        String path = AutoBlockGenerator.save(blocks, palette);
        if (path == null) {
            DialogUtils.oneDialog(this,
                    "Error",
                    "Could not write block.json. Check storage permissions and try again.",
                    "OK", true, R.drawable.ic_mtrl_warning, true, null, null);
            return;
        }
        binding.tvResult.setText("Saved " + blocks.size() + " block(s) to " + path);
        DialogUtils.oneDialog(this,
                "Done",
                "Generated " + blocks.size() + " block(s) into palette \"" + palette + "\".\n\n"
                        + "Stored at: " + path,
                "OK", true, R.drawable.ic_mtrl_check, true, null, null);
    }

    /**
     * Loads source from a Java/Kotlin/XML file via the standard Sketchware
     * file picker. Mirrors the picker pattern used elsewhere (backup
     * restore, ZIP import) so users get a familiar UX.
     */
    private void pickCodeFile() {
        FilePickerOptions options = new FilePickerOptions();
        options.setMultipleSelection(false);
        options.setExtensions(new String[]{"java", "kt", "xml"});
        options.setTitle("Select a code file to load");
        options.setInitialDirectory(FilePickerSettings.getLastOpenedFolder(this));

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends File> files) {
                if (files.isEmpty()) return;
                File picked = files.get(0);
                File parent = picked.getParentFile();
                if (parent != null) {
                    FilePickerSettings.setLastOpenedFolder(
                            AutoBlockGeneratorActivity.this, parent.getAbsolutePath());
                }
                loadCodeFromFile(picked);
            }
        };
        new FilePickerDialogFragment(options, callback)
                .show(getSupportFragmentManager(), "code_picker");
    }

    private void loadCodeFromFile(File file) {
        try {
            String contents = FileUtils.readTextFile(file.getAbsolutePath());
            if (contents == null) contents = "";
            binding.etCode.setText(contents);
            Toast.makeText(this,
                    "Loaded " + file.getName() + " (" + contents.length() + " chars)",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "loadCodeFromFile: " + e.getMessage());
            DialogUtils.oneDialog(this,
                    "Error",
                    "Could not read the selected file: " + e.getMessage(),
                    "OK", true, R.drawable.ic_mtrl_warning, true, null, null);
        }
    }
}

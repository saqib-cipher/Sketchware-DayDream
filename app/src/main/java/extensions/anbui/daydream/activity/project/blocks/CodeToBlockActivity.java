package extensions.anbui.daydream.activity.project.blocks;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import extensions.anbui.daydream.configs.Configs;
import extensions.anbui.daydream.file.FileUtils;
import extensions.anbui.daydream.settings.FilePickerSettings;
import extensions.anbui.daydream.ui.DialogUtils;
import mod.hey.studios.editor.manage.block.ExtraBlockInfo;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityDaydreamCodeToBlockBinding;

/**
 * Converts arbitrary Java/Kotlin/XML/code snippets into a Sketchware Pro
 * "extra block" (a.k.a. custom block / palette block) JSON entry, and
 * appends it to the active project's {@code custom_blocks} palette file.
 *
 * <p>The on-disk format is the same one already used by Sketchware Pro for
 * custom blocks (see {@code mod.hey.studios.project.custom_blocks
 * .CustomBlocksManager}). Each block becomes an {@link ExtraBlockInfo} entry
 * inside an {@code ArrayList} stored at
 * {@code .sketchware/data/&lt;sc_id&gt;/custom_blocks}. By writing exactly that
 * file, the new block becomes immediately available inside the project's
 * palette without any further integration.
 */
public class CodeToBlockActivity extends AppCompatActivity {

    public static final String TAG = Configs.universalTAG + "CodeToBlockActivity";
    public static final String EXTRA_SC_ID = "sc_id";

    private ActivityDaydreamCodeToBlockBinding binding;
    private String projectID = "";

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

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_SC_ID)) {
            projectID = Objects.toString(intent.getStringExtra(EXTRA_SC_ID), "");
        }

        binding = ActivityDaydreamCodeToBlockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(view -> onBackPressed());

        binding.btnSave.setOnClickListener(v -> saveBlock());
        binding.btnPreview.setOnClickListener(v -> previewBlock());
        binding.btnLoadFile.setOnClickListener(v -> pickCodeFile());
    }

    /**
     * Opens the standard Sketchware file picker, restricted to source-like
     * files, and loads the selected file's contents into the code editor.
     * The block name + spec are auto-suggested from the filename so the user
     * can save immediately without filling anything else in.
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
                            CodeToBlockActivity.this, parent.getAbsolutePath());
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

            // Auto-suggest a block name and spec from the filename when the
            // user hasn't already typed something. We keep existing values to
            // avoid overwriting user input.
            String fileName = file.getName();
            String base = stripExtension(fileName);
            String suggestedName = sanitizeBlockName(base);

            if (binding.etName.getText() == null
                    || TextUtils.isEmpty(binding.etName.getText().toString().trim())) {
                binding.etName.setText(suggestedName);
            }
            if (binding.etSpec.getText() == null
                    || TextUtils.isEmpty(binding.etSpec.getText().toString().trim())) {
                // Use the same "add source directly" pattern as raw-code blocks
                // — the loaded file becomes a single ASDB-style snippet.
                binding.etSpec.setText("add source directly %s.inputOnly");
            }

            Toast.makeText(this,
                    "Loaded " + fileName + " (" + contents.length() + " chars)",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "loadCodeFromFile: " + e.getMessage());
            DialogUtils.oneDialog(this,
                    "Error",
                    "Could not read the selected file: " + e.getMessage(),
                    "OK", true, R.drawable.ic_mtrl_warning, true, null, null);
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * Block names are used as map keys / palette identifiers, so we keep them
     * to a conservative ASCII subset. Anything else collapses to underscore.
     */
    private static String sanitizeBlockName(String input) {
        if (TextUtils.isEmpty(input)) {
            return "block_" + Long.toString(System.currentTimeMillis(), 36);
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private ExtraBlockInfo collectBlock() {
        String name = binding.etName.getText() == null ? "" : binding.etName.getText().toString().trim();
        String spec = binding.etSpec.getText() == null ? "" : binding.etSpec.getText().toString().trim();
        String code = binding.etCode.getText() == null ? "" : binding.etCode.getText().toString();
        String typeText = binding.etType.getText() == null ? "" : binding.etType.getText().toString().trim();
        String paletteColorText = binding.etPaletteColor.getText() == null ? "" : binding.etPaletteColor.getText().toString().trim();
        String blockColorText = binding.etBlockColor.getText() == null ? "" : binding.etBlockColor.getText().toString().trim();

        // The user asked for "no need to fill everything": only the code field
        // is meaningful when converting an arbitrary snippet into a block.
        // Synthesize sensible defaults for everything else so the user can
        // paste code and immediately Save.
        if (TextUtils.isEmpty(code) && TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Paste some code (or set a block name) first",
                    Toast.LENGTH_LONG).show();
            return null;
        }
        if (TextUtils.isEmpty(name)) {
            name = "block_" + Long.toString(System.currentTimeMillis(), 36);
        }
        if (TextUtils.isEmpty(spec)) {
            // For raw-code blocks, mirror Sketchware's built-in "add source
            // directly" pattern so the block is usable right away.
            if (!TextUtils.isEmpty(code)) {
                spec = "add source directly %s.inputOnly";
            } else {
                spec = name;
            }
        }

        ExtraBlockInfo info = new ExtraBlockInfo();
        info.setName(name);
        info.setSpec(spec);
        // Sketchware historically uses both `spec` and `spec2`; we mirror them so
        // the block renders in legacy and new spec2 codepaths.
        info.setSpec2(spec);
        info.setCode(code);
        info.setColor(parseColorOrDefault(blockColorText, 0xff8a55d7));
        info.setPaletteColor(parseColorOrDefault(paletteColorText, 0xff8a55d7));
        return info;
    }

    private int parseColorOrDefault(String text, int fallback) {
        if (TextUtils.isEmpty(text)) return fallback;
        try {
            String t = text.startsWith("#") ? text.substring(1) : text;
            // Allow #RRGGBB and #AARRGGBB.
            long parsed = Long.parseLong(t, 16);
            if (t.length() == 6) {
                parsed |= 0xff000000L;
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void previewBlock() {
        ExtraBlockInfo info = collectBlock();
        if (info == null) return;
        String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(info);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Block JSON preview")
                .setMessage(pretty)
                .setPositiveButton("OK", null)
                .show();
    }

    private void saveBlock() {
        ExtraBlockInfo info = collectBlock();
        if (info == null) return;

        if (TextUtils.isEmpty(projectID)) {
            // No project context: write to a shared palette so the user can copy
            // it later.
            String path = Environment.getExternalStorageDirectory() + Configs.mainDataDir + "daydream_palette/custom_blocks.json";
            persistBlock(path, info);
            DialogUtils.oneDialog(this,
                    "Saved",
                    "No project was selected, so the block was added to the shared palette at " + path,
                    "OK", true, R.drawable.ic_mtrl_check, true, null, this::finish);
            return;
        }

        String path = Environment.getExternalStorageDirectory() + Configs.projectDataFolderDir + projectID + "/custom_blocks";
        boolean ok = persistBlock(path, info);
        if (ok) {
            DialogUtils.oneDialog(this,
                    "Saved",
                    "Block \"" + info.getName() + "\" added to your project's palette.",
                    "OK", true, R.drawable.ic_mtrl_check, true, null, this::finish);
        } else {
            DialogUtils.oneDialog(this,
                    "Error",
                    "Could not write the block JSON to disk. Check storage permissions and try again.",
                    "OK", true, R.drawable.ic_mtrl_warning, true, null, null);
        }
    }

    private boolean persistBlock(String path, ExtraBlockInfo info) {
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.e(TAG, "persistBlock: cannot create parent " + parent);
                return false;
            }

            List<ExtraBlockInfo> blocks;
            if (file.exists()) {
                String existing = FileUtils.readTextFile(path);
                if (TextUtils.isEmpty(existing)) {
                    blocks = new ArrayList<>();
                } else {
                    try {
                        blocks = new Gson().fromJson(existing,
                                new TypeToken<ArrayList<ExtraBlockInfo>>() {}.getType());
                        if (blocks == null) blocks = new ArrayList<>();
                    } catch (Exception e) {
                        Log.w(TAG, "persistBlock: existing file is not valid block JSON, starting fresh: " + e.getMessage());
                        blocks = new ArrayList<>();
                    }
                }
            } else {
                blocks = new ArrayList<>();
            }

            // Replace if a block with the same name already exists.
            boolean replaced = false;
            for (int i = 0; i < blocks.size(); i++) {
                if (info.getName().equals(blocks.get(i).getName())) {
                    blocks.set(i, info);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) blocks.add(info);

            FileUtils.writeTextFile(path, new Gson().toJson(blocks));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "persistBlock: " + e.getMessage());
            return false;
        }
    }
}

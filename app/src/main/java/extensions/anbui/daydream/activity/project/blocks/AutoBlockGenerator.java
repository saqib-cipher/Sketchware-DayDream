package extensions.anbui.daydream.activity.project.blocks;

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import extensions.anbui.daydream.configs.Configs;
import extensions.anbui.daydream.file.FileUtils;

/**
 * Pure logic for the Auto Block Generator: takes Java/Kotlin source text,
 * detects a small set of Android-specific code patterns, and produces a list
 * of Sketchware "extra block" JSON entries.
 *
 * <p>Pipeline (per the implementation guide):
 * <pre>
 *   User Input → Parser (detect patterns) → Block Mapper → JSON Generator → Save to block.json
 * </pre>
 *
 * <p>The on-disk format mirrors what Sketchware Pro's BlocksManager already
 * loads from {@code .sketchware/resources/block/&lt;palette&gt;/block.json},
 * so generated blocks become available to all projects.
 */
public final class AutoBlockGenerator {

    public static final String TAG = Configs.universalTAG + "AutoBlockGenerator";

    /** Default palette folder name where generated blocks are stored. */
    public static final String DEFAULT_PALETTE = "My Block";

    private AutoBlockGenerator() {}

    /**
     * Entry in the generated block.json.
     *
     * <p>The field names match the keys consumed by Sketchware Pro's
     * BlocksManager so the file can be dropped in directly.
     */
    public static class Block {
        public String name;
        public String spec;
        public String code;
        public String palette;
        /** Block type. " " for regular, "c" for control, "b" for boolean, "s" for string. */
        public String type;
        public String typeName = "";
        public int color = 0xff8a55d7;

        public Block(String name, String spec, String code, String palette, String type) {
            this.name = name;
            this.spec = spec;
            this.code = code;
            this.palette = palette;
            this.type = type;
        }
    }

    /**
     * Parses {@code source} and returns blocks matching detected patterns.
     * Always returns a non-null list (possibly empty when nothing was matched).
     */
    public static List<Block> generate(String source, String palette) {
        List<Block> out = new ArrayList<>();
        if (TextUtils.isEmpty(source)) return out;
        if (TextUtils.isEmpty(palette)) palette = DEFAULT_PALETTE;

        // Track which patterns we've already emitted for so we don't produce
        // 50 duplicates of "setText" when the same call appears repeatedly.
        // The auto-generator's job is to surface *patterns*, not to copy
        // every line. Users can fine-tune the resulting blocks.
        Map<String, Block> uniqueByName = new LinkedHashMap<>();

        // Detection rule: if (cond) { body }
        // → control block, code = "if (%1$s) { %2$s }"
        Matcher mIf = PATTERN_IF.matcher(source);
        int idx = 1;
        while (mIf.find()) {
            String name = "if_block_" + idx++;
            Block b = new Block(
                    name,
                    "if %s then",
                    "if (%1$s) {\n    %2$s\n}",
                    palette,
                    "c");
            uniqueByName.putIfAbsent(name, b);
            // Cap to one IF block — they all share the same template.
            break;
        }

        // Detection rule: setText("...") / setText(var)
        // → "%1$s.setText(%2$s);"
        if (PATTERN_SET_TEXT.matcher(source).find()) {
            uniqueByName.putIfAbsent("setText",
                    new Block("setText",
                            "%m.view setText %s",
                            "%1$s.setText(%2$s);",
                            palette,
                            " "));
        }

        // Detection rule: setImageResource(R.drawable.foo)
        // → "%1$s.setImageResource(%2$s);"
        if (PATTERN_SET_IMAGE.matcher(source).find()) {
            uniqueByName.putIfAbsent("setImageResource",
                    new Block("setImageResource",
                            "%m.view setImageResource %s",
                            "%1$s.setImageResource(%2$s);",
                            palette,
                            " "));
        }

        // Detection rule: setOnClickListener(...)
        // → container block whose body is the listener body (substack handled
        //   by Sketchware as an inner block stack, so we leave the body slot
        //   open).
        if (PATTERN_ON_CLICK.matcher(source).find()) {
            uniqueByName.putIfAbsent("setOnClickListener",
                    new Block("setOnClickListener",
                            "%m.view onClick",
                            "%1$s.setOnClickListener(new View.OnClickListener() {\n"
                                    + "    @Override\n"
                                    + "    public void onClick(View _view) {\n"
                                    + "        %2$s\n"
                                    + "    }\n"
                                    + "});",
                            palette,
                            "c"));
        }

        out.addAll(uniqueByName.values());
        return out;
    }

    /**
     * Serializes generated blocks to JSON as a flat array, matching the
     * format BlocksManager expects for {@code block.json}.
     */
    public static String toJson(List<Block> blocks) {
        return new Gson().toJson(blocks);
    }

    public static String toPrettyJson(List<Block> blocks) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(blocks);
    }

    /**
     * Writes the generated blocks to the canonical path:
     * {@code /storage/emulated/0/.sketchware/resources/block/&lt;palette&gt;/block.json}.
     *
     * @return the absolute file path on success, or {@code null} on failure.
     */
    public static String save(List<Block> blocks, String palette) {
        if (blocks == null || blocks.isEmpty()) return null;
        if (TextUtils.isEmpty(palette)) palette = DEFAULT_PALETTE;
        try {
            String dir = android.os.Environment.getExternalStorageDirectory()
                    + Configs.mainDataDir + "resources/block/" + palette;
            File dirFile = new File(dir);
            if (!dirFile.exists() && !dirFile.mkdirs()) {
                Log.e(TAG, "save: cannot create palette dir " + dir);
                return null;
            }
            String path = dir + "/block.json";
            FileUtils.writeTextFile(path, toJson(blocks));
            return path;
        } catch (Exception e) {
            Log.e(TAG, "save: " + e.getMessage());
            return null;
        }
    }

    // --- Detection patterns -------------------------------------------------

    private static final Pattern PATTERN_IF = Pattern.compile(
            "\\bif\\s*\\(",
            Pattern.MULTILINE);

    private static final Pattern PATTERN_SET_TEXT = Pattern.compile(
            "\\.setText\\s*\\(",
            Pattern.MULTILINE);

    private static final Pattern PATTERN_SET_IMAGE = Pattern.compile(
            "\\.setImageResource\\s*\\(",
            Pattern.MULTILINE);

    private static final Pattern PATTERN_ON_CLICK = Pattern.compile(
            "\\.setOnClickListener\\s*\\(",
            Pattern.MULTILINE);
}

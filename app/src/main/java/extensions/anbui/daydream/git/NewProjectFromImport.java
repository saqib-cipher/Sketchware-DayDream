package extensions.anbui.daydream.git;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.GB;
import a.a.a.lC;
import a.a.a.nB;
import a.a.a.oB;
import a.a.a.wq;
import extensions.anbui.daydream.configs.Configs;
import extensions.anbui.daydream.file.FileUtils;
import extensions.anbui.daydream.project.ProjectDecryptor;
import mod.hey.studios.editor.manage.block.ExtraBlockInfo;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.SketchApplication;

/**
 * Imports an arbitrary Android project (one that is NOT in the Sketchware Pro
 * on-disk layout) by allocating a fresh {@code sc_id} via {@link lC#b()},
 * registering it with the Sketchware project listing, and copying the
 * imported sources into the new project's {@code mysc/<sc_id>} tree so it
 * shows up alongside other projects.
 *
 * <p>This implements the user-facing requirement: "if project structure is
 * not same swb project then create new id project wrt project listing then
 * all layout and codes (... otherwise use add source directly block to store
 * code at location)". For now we drop the source tree wholesale — rather
 * than per-method conversion — so the new project participates in the normal
 * Sketchware compile pipeline while preserving the raw imported sources.
 */
public final class NewProjectFromImport {

    public static final String TAG = Configs.universalTAG + "NewProjectFromImport";

    private static final Pattern PKG_PATTERN =
            Pattern.compile("package\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern APP_NAME_RES_PATTERN =
            Pattern.compile("<string\\s+name=\"app_name\">([^<]+)</string>");
    private static final Pattern APP_NAME_ATTR_PATTERN =
            Pattern.compile("android:label\\s*=\\s*\"([^\"@][^\"]*)\"");

    private NewProjectFromImport() {}

    /**
     * Allocates a new project id and imports {@code repoRoot} as that
     * project's {@code mysc/<sc_id>} tree. Returns the new sc_id, or {@code
     * null} on failure.
     */
    public static String createAndImport(String repoRoot, TextView statusTextView) {
        try {
            Context ctx = resolveContext(statusTextView);
            if (ctx == null) {
                Log.e(TAG, "createAndImport: no context available");
                return null;
            }

            updateStatus(statusTextView, "Allocating new project id...");
            String scId = lC.b();

            Imported meta = readImportedMetadata(repoRoot);
            String wsName = meta.wsName != null ? meta.wsName : lC.c();
            String pkgName = meta.pkgName != null
                    ? meta.pkgName
                    : "com.my." + wsName.toLowerCase(Locale.ROOT);
            String appName = meta.appName != null ? meta.appName : wsName;

            updateStatus(statusTextView, "Registering project " + scId + " (" + wsName + ")...");

            HashMap<String, Object> data = new HashMap<>();
            data.put("sc_id", scId);
            data.put("my_sc_pkg_name", pkgName);
            data.put("my_ws_name", wsName);
            data.put("my_app_name", appName);
            data.put("my_sc_reg_dt", new nB().a("yyyyMMddHHmmss"));
            data.put("custom_icon", false);
            data.put("isIconAdaptive", false);
            data.put("sc_ver_code", "1");
            data.put("sc_ver_name", "1.0");
            data.put("sketchware_ver", GB.d(ctx));
            // Sensible Material defaults — match the new-project flow.
            data.put("color_accent", 0xFF008DCD);
            data.put("color_primary", 0xFF008DCD);
            data.put("color_primary_dark", 0xFF0084C2);
            data.put("color_control_highlight", 0x44008DCD);
            data.put("color_control_normal", 0xFF57BEEF);

            lC.a(scId, data);
            wq.a(ctx, scId);
            new oB().b(wq.b(scId));

            ProjectSettings projectSettings = new ProjectSettings(scId);
            projectSettings.setValue(ProjectSettings.SETTING_NEW_XML_COMMAND,
                    ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
            projectSettings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING,
                    ProjectSettings.SETTING_GENERIC_VALUE_TRUE);

            updateStatus(statusTextView, "Copying imported sources to mysc/" + scId + "...");
            String dest = FileUtils.getInternalStorageDir() + Configs.projectMySourceFolderDir + scId;
            FileUtils.createDirectory(dest);
            FileUtils.copyDirectory(repoRoot, dest);

            // Generic imports don't ship a Sketchware data tree, but the rest
            // of the editor (logic editor, view editor, build pipeline) needs
            // those files to be present for the project to be usable. Lay
            // down the same scaffolding the new-project flow does.
            ensureSketchwareDataScaffolding(scId, appName, pkgName);

            // If the imported project ships layout XMLs, surface them in the new
            // project's resource folder so the user can see and edit them
            // through Sketchware's resource manager.
            mirrorLayoutsIfPresent(repoRoot, scId, statusTextView);

            // Best-effort: if the import has a recognizable MainActivity.java
            // we drop its onCreate body into a Sketchware "add source directly"
            // block in the new project's MainActivity, so the user lands on
            // editable blocks instead of an empty activity.
            tryAddSourceDirectlyFromMainActivity(repoRoot, scId, statusTextView);

            updateStatus(statusTextView, "Imported as project " + scId);
            return scId;
        } catch (Exception e) {
            Log.e(TAG, "createAndImport: " + Objects.toString(e.getMessage(), "unknown error"), e);
            return null;
        }
    }

    private static void mirrorLayoutsIfPresent(String repoRoot, String scId, TextView statusTextView) {
        try {
            // The user expects all layouts and resources to come along, even
            // when they're scattered across non-standard folders. Walk the
            // repo and copy *every* res/layout we find, giving precedence to
            // app/src/main while still picking up flavour-specific or modular
            // layout dirs (src/debug/res/layout, feature/src/main/res/layout,
            // etc.).
            List<File> layoutDirs = new ArrayList<>();
            collectLayoutDirs(new File(repoRoot), layoutDirs, 6);
            if (layoutDirs.isEmpty()) {
                Log.i(TAG, "mirrorLayoutsIfPresent: no layout/ folder found in import");
                return;
            }
            String destLayouts = wq.b(scId) + "/files/resource/layout";
            FileUtils.createDirectory(destLayouts);
            updateStatus(statusTextView, "Copying layout XMLs...");
            for (File srcLayouts : layoutDirs) {
                try {
                    FileUtils.copyDirectory(srcLayouts.getAbsolutePath(), destLayouts);
                } catch (Exception inner) {
                    Log.w(TAG, "mirrorLayoutsIfPresent: failed to copy "
                            + srcLayouts + ": " + inner.getMessage());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "mirrorLayoutsIfPresent: " + e.getMessage());
        }
    }

    private static void collectLayoutDirs(File dir, List<File> out, int depthLimit) {
        if (dir == null || depthLimit < 0 || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String name = child.getName();
            // Skip noise that can't possibly contain user layouts.
            if (".git".equals(name) || "build".equals(name) || ".gradle".equals(name)
                    || ".idea".equals(name) || "node_modules".equals(name)) continue;
            if ("layout".equals(name) || name.startsWith("layout-")) {
                // Only treat this as a layout dir if its parent is "res" — keeps
                // us from picking up unrelated folders called "layout".
                File parent = child.getParentFile();
                if (parent != null && "res".equals(parent.getName())) {
                    out.add(child);
                    continue;
                }
            }
            collectLayoutDirs(child, out, depthLimit - 1);
        }
    }

    /**
     * Lays down the minimum set of files Sketchware Pro expects under
     * {@code .sketchware/data/<sc_id>/} and {@code .sketchware/mysc/list/<sc_id>/}
     * so that the imported project participates in the editor without crashing
     * on missing files. Mirrors what
     * {@link extensions.anbui.daydream.project.ProjectData#setDataForFirstTimeProjectCreation}
     * does for newly created projects.
     */
    private static void ensureSketchwareDataScaffolding(String scId, String appName, String pkgName) {
        try {
            String dataDir = FileUtils.getInternalStorageDir() + Configs.projectDataFolderDir + scId;
            FileUtils.createDirectory(dataDir);

            // project_config — read by ProjectConfigs and the build pipeline.
            String projectConfigPath = dataDir + "/project_config";
            if (!FileUtils.isFileExist(projectConfigPath)) {
                FileUtils.writeTextFile(projectConfigPath,
                        "{\"min_sdk\":\"23\","
                                + "\"enable_viewbinding\":\"true\","
                                + "\"xml_command\":\"true\"}");
            }

            // The four core encrypted files. Empty content is fine — Sketchware
            // treats them as no-data and the editor populates them as the user
            // adds activities, blocks, files, and libraries.
            for (String f : new String[]{"logic", "view", "file", "library", "resource"}) {
                String p = dataDir + "/" + f;
                if (!FileUtils.isFileExist(p)) {
                    ProjectDecryptor.saveEncryptedFile(p, "");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "ensureSketchwareDataScaffolding: " + e.getMessage());
        }
    }

    /**
     * Looks for a {@code MainActivity.java} (or {@code Main.kt}) in the
     * imported project, extracts the body of its {@code onCreate} method and
     * writes a single Sketchware "add source directly" (ASDB) block into the
     * new project's logic file at
     * {@code @MainActivity.java_main}. This gives the user a starting point
     * they can edit visually instead of an empty activity.
     */
    private static void tryAddSourceDirectlyFromMainActivity(String repoRoot, String scId,
                                                              TextView statusTextView) {
        try {
            File mainActivity = findMainActivitySource(new File(repoRoot), 8);
            if (mainActivity == null) {
                Log.i(TAG, "tryAddSourceDirectlyFromMainActivity: no MainActivity source found");
                return;
            }
            String src = FileUtils.readTextFile(mainActivity.getAbsolutePath());
            String body = extractOnCreateBody(src);
            if (body == null || body.trim().isEmpty()) {
                Log.i(TAG, "tryAddSourceDirectlyFromMainActivity: onCreate body not found in "
                        + mainActivity.getName());
                return;
            }
            updateStatus(statusTextView, "Importing onCreate as add-source-directly block...");

            String logicPath = FileUtils.getInternalStorageDir()
                    + Configs.projectDataFolderDir + scId + "/logic";

            HashMap<String, Object> block = new HashMap<>();
            block.put("color", 0xff8a55d7);
            block.put("id", "10");
            block.put("nextBlock", -1);
            block.put("opCode", "addSourceDirectly");
            ArrayList<String> params = new ArrayList<>();
            params.add(body);
            block.put("parameters", params);
            block.put("spec", "add source directly %s.inputOnly");
            block.put("subStack1", -1);
            block.put("subStack2", -1);
            block.put("type", " ");
            block.put("typeName", "");

            ArrayList<HashMap<String, Object>> blockList = new ArrayList<>();
            blockList.add(block);

            String logicSection = "@MainActivity.java_main\n" + new Gson().toJson(blockList);
            ProjectDecryptor.saveEncryptedFile(logicPath, logicSection);

            // Also surface the same code as a reusable Custom Block in the
            // project palette, so the user can re-use the snippet anywhere.
            try {
                ExtraBlockInfo extra = new ExtraBlockInfo();
                extra.setName("imported_main_oncreate");
                extra.setSpec("imported MainActivity.onCreate body");
                extra.setSpec2("imported MainActivity.onCreate body");
                extra.setCode(body);
                extra.setColor(0xff8a55d7);
                extra.setPaletteColor(0xff8a55d7);
                ArrayList<ExtraBlockInfo> list = new ArrayList<>();
                list.add(extra);
                String customBlocksPath = FileUtils.getInternalStorageDir()
                        + Configs.projectDataFolderDir + scId + "/custom_blocks";
                FileUtils.writeTextFile(customBlocksPath, new Gson().toJson(list));
            } catch (Exception inner) {
                Log.w(TAG, "tryAddSourceDirectlyFromMainActivity: custom_blocks: "
                        + inner.getMessage());
            }
        } catch (Exception e) {
            Log.w(TAG, "tryAddSourceDirectlyFromMainActivity: " + e.getMessage());
        }
    }

    private static File findMainActivitySource(File root, int depthLimit) {
        if (root == null || depthLimit < 0 || !root.isDirectory()) return null;
        File[] children = root.listFiles();
        if (children == null) return null;
        File fallback = null;
        for (File child : children) {
            if (child.isFile()) {
                String name = child.getName();
                if ("MainActivity.java".equals(name) || "MainActivity.kt".equals(name)) {
                    return child;
                }
                if (fallback == null && (name.endsWith("Activity.java") || name.endsWith("Activity.kt"))) {
                    fallback = child;
                }
            } else if (child.isDirectory()) {
                String dn = child.getName();
                if (".git".equals(dn) || "build".equals(dn) || ".gradle".equals(dn)
                        || ".idea".equals(dn)) continue;
                File found = findMainActivitySource(child, depthLimit - 1);
                if (found != null) {
                    String n = found.getName();
                    if ("MainActivity.java".equals(n) || "MainActivity.kt".equals(n)) {
                        return found;
                    }
                    if (fallback == null) fallback = found;
                }
            }
        }
        return fallback;
    }

    /**
     * Pulls the body of the first {@code onCreate(...)} method out of a
     * Java source. Returns {@code null} if none is found. The body is the
     * raw text between the opening and closing braces, with a leading and
     * trailing newline trimmed but inner indentation preserved.
     */
    static String extractOnCreateBody(String src) {
        if (src == null || src.isEmpty()) return null;
        Matcher m = Pattern.compile("onCreate\\s*\\([^)]*\\)\\s*\\{").matcher(src);
        if (!m.find()) return null;
        int braceStart = m.end() - 1; // position of '{'
        int depth = 0;
        int bodyStart = -1;
        int bodyEnd = -1;
        for (int i = braceStart; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') {
                if (depth == 0) bodyStart = i + 1;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    bodyEnd = i;
                    break;
                }
            }
        }
        if (bodyStart < 0 || bodyEnd < 0 || bodyEnd <= bodyStart) return null;
        String body = src.substring(bodyStart, bodyEnd);
        // Drop the typical "super.onCreate(...)" and "setContentView(...)" the
        // Sketchware compiler will already emit, to avoid duplicates.
        body = body.replaceAll("(?m)^\\s*super\\.onCreate\\s*\\([^;]*;\\s*\\n?", "");
        body = body.replaceAll("(?m)^\\s*setContentView\\s*\\([^;]*;\\s*\\n?", "");
        return body.trim();
    }

    private static Imported readImportedMetadata(String repoRoot) {
        Imported out = new Imported();
        File manifest = firstExisting(repoRoot,
                "app/src/main/AndroidManifest.xml",
                "src/main/AndroidManifest.xml",
                "AndroidManifest.xml");
        if (manifest != null) {
            String text = FileUtils.readTextFile(manifest.getAbsolutePath());
            Matcher m = PKG_PATTERN.matcher(text);
            if (m.find()) out.pkgName = m.group(1);
            Matcher a = APP_NAME_ATTR_PATTERN.matcher(text);
            if (a.find()) out.appName = a.group(1);
        }
        File strings = firstExisting(repoRoot,
                "app/src/main/res/values/strings.xml",
                "src/main/res/values/strings.xml",
                "res/values/strings.xml");
        if (strings != null) {
            String text = FileUtils.readTextFile(strings.getAbsolutePath());
            Matcher s = APP_NAME_RES_PATTERN.matcher(text);
            if (s.find()) out.appName = s.group(1);
        }
        if (out.pkgName != null) {
            // derive a workspace name from the last package segment
            int dot = out.pkgName.lastIndexOf('.');
            String tail = dot >= 0 ? out.pkgName.substring(dot + 1) : out.pkgName;
            if (!tail.isEmpty()) {
                out.wsName = capitalize(tail);
            }
        }
        if (out.wsName == null && out.appName != null) {
            out.wsName = sanitizeWorkspaceName(out.appName);
        }
        return out;
    }

    private static File firstExisting(String root, String... rels) {
        for (String rel : rels) {
            File f = new File(root, rel);
            if (f.isFile()) return f;
        }
        return null;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String sanitizeWorkspaceName(String appName) {
        StringBuilder sb = new StringBuilder();
        for (char c : appName.toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static Context resolveContext(TextView statusTextView) {
        if (statusTextView != null && statusTextView.getContext() != null) {
            return statusTextView.getContext();
        }
        try {
            return SketchApplication.getContext();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void updateStatus(TextView statusTextView, String msg) {
        Log.i(TAG, msg);
        if (statusTextView == null || statusTextView.getContext() == null) return;
        ((Activity) statusTextView.getContext()).runOnUiThread(() -> statusTextView.setText(msg));
    }

    private static final class Imported {
        String pkgName;
        String appName;
        String wsName;
    }
}

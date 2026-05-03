package extensions.anbui.daydream.git;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
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

            // If the imported project ships layout XMLs, surface them in the new
            // project's resource folder so the user can see and edit them
            // through Sketchware's resource manager.
            mirrorLayoutsIfPresent(repoRoot, scId, statusTextView);

            updateStatus(statusTextView, "Imported as project " + scId);
            return scId;
        } catch (Exception e) {
            Log.e(TAG, "createAndImport: " + Objects.toString(e.getMessage(), "unknown error"), e);
            return null;
        }
    }

    private static void mirrorLayoutsIfPresent(String repoRoot, String scId, TextView statusTextView) {
        try {
            File srcLayouts = findLayoutDir(new File(repoRoot));
            if (srcLayouts == null) {
                Log.i(TAG, "mirrorLayoutsIfPresent: no layout/ folder found in import");
                return;
            }
            String destLayouts = wq.b(scId) + "/files/resource/layout";
            FileUtils.createDirectory(destLayouts);
            updateStatus(statusTextView, "Copying layout XMLs...");
            FileUtils.copyDirectory(srcLayouts.getAbsolutePath(), destLayouts);
        } catch (Exception e) {
            Log.w(TAG, "mirrorLayoutsIfPresent: " + e.getMessage());
        }
    }

    private static File findLayoutDir(File root) {
        // Look for app/src/main/res/layout, src/main/res/layout, or res/layout.
        String[] candidates = {
                "app/src/main/res/layout",
                "src/main/res/layout",
                "res/layout"
        };
        for (String c : candidates) {
            File f = new File(root, c);
            if (f.isDirectory()) return f;
        }
        return null;
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

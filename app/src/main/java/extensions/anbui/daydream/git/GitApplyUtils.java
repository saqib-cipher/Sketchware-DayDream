package extensions.anbui.daydream.git;

import android.app.Activity;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.util.Objects;

import extensions.anbui.daydream.configs.Configs;
import extensions.anbui.daydream.file.FileUtils;
import extensions.anbui.daydream.tools.ToolCore;
import extensions.anbui.daydream.tools.project.ProjectFileManager;

public class GitApplyUtils {
    public static String TAG = Configs.universalTAG + "GitApplyUtils";

    /**
     * Detected repo layouts. Earlier versions of this code only supported
     * {@link #SKETCHWARE_FULL} (a repo that contained the {@code /project} and
     * {@code /source} folders pushed by Sketchware Pro). Cloning any other
     * Android project failed because none of those folders existed.
     */
    public enum RepoLayout {
        /** Standard Sketchware Pro layout: /project, /source, /local_library. */
        SKETCHWARE_FULL,
        /** Sketchware-style /project folder is at repo root (no /project wrapper). */
        SKETCHWARE_AT_ROOT,
        /** Looks like a normal Android project (has /app/src/main/...). */
        ANDROID_PROJECT,
        /** Nothing recognised; treat as a generic file dump. */
        UNKNOWN
    }

    public static boolean apply(String projectID, TextView statusTextView) {
        Log.i(TAG, "apply: " + projectID);
        try {
            String repoRoot = FileUtils.getInternalStorageDir() + Configs.gitFolderDir + projectID;
            RepoLayout layout = detectLayout(repoRoot);
            Log.i(TAG, "apply: detected layout " + layout + " for repo at " + repoRoot);
            updateStatus(statusTextView, "Detected layout: " + layout);

            switch (layout) {
                case SKETCHWARE_FULL:
                    return applySketchware(projectID, repoRoot + Configs.gitProjectFolderName,
                            repoRoot + Configs.gitLocalLibraryFolderName, statusTextView);
                case SKETCHWARE_AT_ROOT:
                    // /project lives at repo root, /local_library may or may not be there.
                    return applySketchware(projectID, repoRoot,
                            repoRoot + Configs.gitLocalLibraryFolderName, statusTextView);
                case ANDROID_PROJECT:
                    return applyAndroidProject(projectID, repoRoot, statusTextView);
                case UNKNOWN:
                default:
                    Log.w(TAG, "apply: Unknown repo layout. Nothing to import.");
                    updateStatus(statusTextView, "Unrecognised repository layout.");
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "apply: " + Objects.toString(e.getMessage(), "unknown error"));
            return false;
        }
    }

    /**
     * Detects what kind of project the repo at {@code repoRoot} contains.
     *
     * <p>Detection is intentionally permissive: anything that even loosely
     * resembles an Android project (a manifest, a Gradle script, a {@code
     * src/main} tree, or even just bare {@code .java}/{@code .kt} sources or
     * an Android-style {@code res} folder) is treated as
     * {@link RepoLayout#ANDROID_PROJECT}. {@link #applyAndroidProject} can
     * always allocate a fresh sc_id and import what it finds, so we'd rather
     * try-and-mostly-succeed than reject the repo with "unknown layout".
     */
    public static RepoLayout detectLayout(String repoRoot) {
        // Sketchware-pushed layout: /project/data + /project/resources (optional)
        if (FileUtils.isFileExist(repoRoot + Configs.gitProjectFolderName + "/data")
                || FileUtils.isFileExist(repoRoot + Configs.gitProjectFolderName + "/project")) {
            return RepoLayout.SKETCHWARE_FULL;
        }

        // /project lives at repo root (e.g. someone uploaded just the project folder content)
        if (FileUtils.isFileExist(repoRoot + "/data/logic")
                || FileUtils.isFileExist(repoRoot + "/data/view")) {
            return RepoLayout.SKETCHWARE_AT_ROOT;
        }

        // Standard Android Studio / Gradle project, or any subset of one.
        // We accept a broad set of "this looks Android-ish" signals so that
        // partial / unconventional repos can still be imported.
        String[] androidSignals = {
                "/app/src/main/AndroidManifest.xml",
                "/src/main/AndroidManifest.xml",
                "/AndroidManifest.xml",
                "/build.gradle",
                "/build.gradle.kts",
                "/settings.gradle",
                "/settings.gradle.kts",
                "/app/build.gradle",
                "/app/build.gradle.kts",
                "/app/src/main/java",
                "/app/src/main/kotlin",
                "/src/main/java",
                "/app/src/main/res",
                "/src/main/res",
                "/res"
        };
        for (String s : androidSignals) {
            if (FileUtils.isFileExist(repoRoot + s)) {
                return RepoLayout.ANDROID_PROJECT;
            }
        }

        // Last-resort heuristic: if the repo root contains *any* Java/Kotlin
        // source we still want to import it as a new project rather than
        // bailing. NewProjectFromImport will copy what's there and leave the
        // user with a fresh project they can edit.
        if (containsJavaOrKotlin(new File(repoRoot), 4)) {
            return RepoLayout.ANDROID_PROJECT;
        }

        return RepoLayout.UNKNOWN;
    }

    private static boolean containsJavaOrKotlin(File dir, int depthLimit) {
        if (dir == null || depthLimit < 0 || !dir.isDirectory()) return false;
        File[] children = dir.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isFile()) {
                String name = child.getName();
                if (name.endsWith(".java") || name.endsWith(".kt")) return true;
            } else if (child.isDirectory()) {
                // Skip VCS / build artefacts to keep this cheap.
                String dn = child.getName();
                if (".git".equals(dn) || "build".equals(dn) || ".gradle".equals(dn)
                        || ".idea".equals(dn) || "node_modules".equals(dn)) continue;
                if (containsJavaOrKotlin(child, depthLimit - 1)) return true;
            }
        }
        return false;
    }

    private static boolean applySketchware(String projectID, String projectFolder,
                                           String localLibraryFolder, TextView statusTextView) {
        try {
            cleanUpProject(projectID);

            if (FileUtils.isFileExist(projectFolder + "/data")) {
                ProjectFileManager.copyData(projectID,
                        projectFolder + "/data",
                        FileUtils.getInternalStorageDir() + Configs.projectDataFolderDir + projectID,
                        statusTextView,
                        true,
                        false);
            } else {
                Log.w(TAG, "applySketchware: missing /data folder, skipping data copy");
            }

            copyResourceIfExists(projectID, projectFolder + "/resources/fonts",
                    FileUtils.getInternalStorageDir() + Configs.resFontsFolderDir + projectID,
                    statusTextView, "Copying fonts...");
            copyResourceIfExists(projectID, projectFolder + "/resources/icons",
                    FileUtils.getInternalStorageDir() + Configs.resIconsFolderDir + projectID,
                    statusTextView, "Copying icons...");
            copyResourceIfExists(projectID, projectFolder + "/resources/images",
                    FileUtils.getInternalStorageDir() + Configs.resImagesFolderDir + projectID,
                    statusTextView, "Copying images...");
            copyResourceIfExists(projectID, projectFolder + "/resources/sounds",
                    FileUtils.getInternalStorageDir() + Configs.resSoundsFolderDir + projectID,
                    statusTextView, "Copying sounds...");

            if (FileUtils.isFileExist(localLibraryFolder)) {
                updateStatus(statusTextView, "Copying local libraries (skipping existing)...");
                ProjectFileManager.copyLocalLibrary(projectID,
                        localLibraryFolder,
                        FileUtils.getInternalStorageDir() + Configs.projectLocalLibFolderDir,
                        true,
                        statusTextView);
            } else {
                Log.i(TAG, "applySketchware: no /local_library folder found, skipping");
            }

            String propsSrc = FileUtils.isFileExist(projectFolder + "/project")
                    ? projectFolder + "/project"
                    : projectFolder + "/info";
            if (FileUtils.isFileExist(propsSrc)) {
                updateStatus(statusTextView, "Copying project info...");
                ProjectFileManager.copyProperties(projectID,
                        propsSrc,
                        FileUtils.getInternalStorageDir() + Configs.projectInfoFolderDir + projectID,
                        null);
            } else {
                Log.w(TAG, "applySketchware: no project info folder, skipping");
            }

            ToolCore.fixID(projectID);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "applySketchware: " + Objects.toString(e.getMessage(), "unknown error"));
            return false;
        }
    }

    /**
     * For a generic Android project we cannot drop the import into the
     * existing {@code projectID}'s data folder (Sketchware's data layout
     * would conflict). Instead we allocate a brand new {@code sc_id} via
     * {@link NewProjectFromImport}, register it with the project listing,
     * and copy the imported source tree into the new project's
     * {@code mysc/<sc_id>} folder so it appears alongside the user's other
     * projects.
     */
    private static boolean applyAndroidProject(String projectID, String repoRoot, TextView statusTextView) {
        updateStatus(statusTextView, "Importing as a new project...");
        String newId = NewProjectFromImport.createAndImport(repoRoot, statusTextView);
        if (newId == null) {
            Log.e(TAG, "applyAndroidProject: NewProjectFromImport returned null");
            return false;
        }
        Log.i(TAG, "applyAndroidProject: imported into new project id " + newId
                + " (original staging id was " + projectID + ")");
        return true;
    }

    private static void copyResourceIfExists(String projectID, String src, String dest,
                                             TextView statusTextView, String label) {
        if (!FileUtils.isFileExist(src)) {
            Log.i(TAG, "copyResourceIfExists: skipping missing " + src);
            return;
        }
        updateStatus(statusTextView, label);
        ProjectFileManager.copyResources(projectID, src, dest, null);
    }

    public static void cleanUpProject(String projectID) {
        Log.i(TAG, "cleanUpProject: " + projectID);
        try {
            FileUtils.deleteRecursive(new File(FileUtils.getInternalStorageDir() + Configs.projectDataFolderDir + projectID + "/files/"));

            FileUtils.deleteRecursive(new File(FileUtils.getInternalStorageDir() + Configs.resFontsFolderDir + projectID + "/"));
            FileUtils.deleteRecursive(new File(FileUtils.getInternalStorageDir() + Configs.resIconsFolderDir + projectID + "/"));
            FileUtils.deleteRecursive(new File(FileUtils.getInternalStorageDir() + Configs.resImagesFolderDir + projectID + "/"));
            FileUtils.deleteRecursive(new File(FileUtils.getInternalStorageDir() + Configs.resSoundsFolderDir + projectID + "/"));
        } catch (Exception e) {
            Log.e(TAG, Objects.toString(e.getMessage(), "unknown error"));
        }
    }

    private static void updateStatus(TextView statusTextView, String msg) {
        Log.i(TAG, "updateStatus: " + msg);
        if (statusTextView == null || statusTextView.getContext() == null) return;
        ((Activity) statusTextView.getContext()).runOnUiThread(() -> statusTextView.setText(msg));
    }
}

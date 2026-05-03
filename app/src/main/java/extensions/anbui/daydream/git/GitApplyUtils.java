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

        // Standard Android Studio / Gradle project
        if (FileUtils.isFileExist(repoRoot + "/app/src/main/AndroidManifest.xml")
                || FileUtils.isFileExist(repoRoot + "/build.gradle")
                || FileUtils.isFileExist(repoRoot + "/build.gradle.kts")
                || FileUtils.isFileExist(repoRoot + "/settings.gradle")
                || FileUtils.isFileExist(repoRoot + "/settings.gradle.kts")) {
            return RepoLayout.ANDROID_PROJECT;
        }

        return RepoLayout.UNKNOWN;
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
     * For a generic Android project we cannot import it as a Sketchware project
     * (Sketchware doesn't store raw Java sources). Instead we copy the project
     * tree into the user's mysc/source folder so that it's available for review
     * via the file explorer, and surface a helpful status message.
     */
    private static boolean applyAndroidProject(String projectID, String repoRoot, TextView statusTextView) {
        try {
            updateStatus(statusTextView, "Importing Android project sources...");
            String dest = FileUtils.getInternalStorageDir() + Configs.projectMySourceFolderDir + projectID;
            FileUtils.createDirectory(dest);
            FileUtils.copyDirectory(repoRoot, dest);
            updateStatus(statusTextView, "Imported sources to mysc/" + projectID);
            // Don't run fixID — there's no Sketchware data to fix.
            return true;
        } catch (Exception e) {
            Log.e(TAG, "applyAndroidProject: " + Objects.toString(e.getMessage(), "unknown error"));
            return false;
        }
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

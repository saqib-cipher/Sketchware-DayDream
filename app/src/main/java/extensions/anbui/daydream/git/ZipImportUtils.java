package extensions.anbui.daydream.git;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import extensions.anbui.daydream.configs.Configs;
import extensions.anbui.daydream.file.FileUtils;

/**
 * Imports a project from a local ZIP file (chosen via SAF) into the
 * {@link Configs#gitFolderDir} workspace. After extraction the same
 * {@link GitApplyUtils#apply(String, android.widget.TextView)} pipeline can be
 * used to import the project into Sketchware Pro.
 *
 * <p>This complements the GitHub clone flow so that users can import projects
 * shared as ZIP archives without needing a Git repository.
 */
public final class ZipImportUtils {
    public static final String TAG = Configs.universalTAG + "ZipImportUtils";

    private ZipImportUtils() {}

    /**
     * Extracts the ZIP referenced by {@code uri} into the staging folder for
     * {@code projectID}. If the ZIP contains a single top-level folder
     * (a common case when zipping a project directory), that folder is used
     * directly so the same apply logic that handles Git clones works.
     *
     * @return {@code true} on success, {@code false} otherwise.
     */
    public static boolean importZip(Context context, String projectID, Uri uri) {
        if (context == null || uri == null) return false;

        String destPath = FileUtils.getInternalStorageDir() + Configs.gitFolderDir + projectID;
        File destDir = new File(destPath);

        try {
            // Clean previous staging area.
            if (destDir.exists()) {
                FileUtils.deleteRecursive(destDir);
            }
        } catch (Exception e) {
            Log.w(TAG, "importZip: failed to clean destination: " + e.getMessage());
        }

        if (!FileUtils.createDirectory(destPath)) {
            Log.e(TAG, "importZip: failed to create dest " + destPath);
            return false;
        }

        ContentResolver resolver = context.getContentResolver();
        try (InputStream raw = resolver.openInputStream(uri)) {
            if (raw == null) {
                Log.e(TAG, "importZip: openInputStream returned null");
                return false;
            }

            try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name == null || name.isEmpty()) continue;
                    if (isUnsafeEntryName(name)) {
                        Log.w(TAG, "importZip: skipping unsafe zip entry " + name);
                        continue;
                    }

                    File outFile = new File(destDir, name);
                    // Guard against zip-slip: ensure the resolved path is inside destDir.
                    String destCanonical = destDir.getCanonicalPath();
                    String outCanonical = outFile.getCanonicalPath();
                    if (!outCanonical.startsWith(destCanonical + File.separator)
                            && !outCanonical.equals(destCanonical)) {
                        Log.w(TAG, "importZip: skipping zip-slip entry " + name);
                        continue;
                    }

                    if (entry.isDirectory()) {
                        if (!outFile.isDirectory() && !outFile.mkdirs()) {
                            Log.w(TAG, "importZip: failed to mkdirs " + outFile);
                        }
                        continue;
                    }

                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        Log.w(TAG, "importZip: failed to mkdirs parent " + parent);
                    }

                    try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zin.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "importZip: " + e.getMessage());
            return false;
        }

        // If the ZIP contained a single top-level folder, hoist its contents up
        // so that GitApplyUtils.detectLayout sees the project layout directly.
        flattenIfSingleTopLevel(destDir);
        return true;
    }

    private static boolean isUnsafeEntryName(String name) {
        if (name.contains("..")) return true;
        if (name.startsWith("/")) return true;
        return name.contains("\\..\\");
    }

    private static void flattenIfSingleTopLevel(File destDir) {
        File[] children = destDir.listFiles();
        if (children == null || children.length != 1) return;
        File only = children[0];
        if (!only.isDirectory()) return;

        File[] grand = only.listFiles();
        if (grand == null) return;
        for (File g : grand) {
            File target = new File(destDir, g.getName());
            if (!g.renameTo(target)) {
                Log.w(TAG, "flattenIfSingleTopLevel: rename failed for " + g);
            }
        }
        if (!only.delete()) {
            Log.w(TAG, "flattenIfSingleTopLevel: failed to delete " + only);
        }
    }
}

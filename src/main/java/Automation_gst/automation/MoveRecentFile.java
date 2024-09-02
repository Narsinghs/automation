package Automation_gst.automation;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class MoveRecentFile {

    private static final int STABILITY_CHECK_MS = 60000; // 1 minute to check file stability
    private static final int RETRY_ATTEMPTS = 3; // Number of retry attempts for file stability check
    private static final int MONITOR_INTERVAL_MS = 60000; // 1 minute interval for monitoring

    public static void main(String[] args) {
        // Define source and destination directories
        String downloadsDir = System.getProperty("user.home") + "/Downloads";
        String targetDir = System.getProperty("user.home") + "/TargetFolder";

        File downloadsFolder = new File(downloadsDir);
        File targetFolder = new File(targetDir);

        // Ensure the target directory exists
        if (!targetFolder.exists()) {
            if (targetFolder.mkdirs()) {
                System.out.println("Target folder created: " + targetDir);
            } else {
                System.out.println("Failed to create target folder.");
                return;
            }
        }

        // Move existing files in Downloads folder to target folder
        moveExistingFiles(downloadsFolder, targetFolder);

        // Monitor directory at regular intervals
        while (true) {
            try {
                // Move new files from Downloads folder to target folder
                moveExistingFiles(downloadsFolder, targetFolder);

                // Sleep for the specified monitoring interval
                TimeUnit.MILLISECONDS.sleep(MONITOR_INTERVAL_MS);
            } catch (InterruptedException e) {
                System.out.println("Monitoring interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Method to move a file to the target directory
    private static void moveFileToTarget(File file, File targetFolder) {
        Path sourcePath = file.toPath();
        Path targetPath = targetFolder.toPath().resolve(file.getName());

        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("File moved successfully: " + file.getName());
        } catch (IOException e) {
            System.out.println("Error moving file: " + e.getMessage());
        }
    }

    // Check if the file is complete (e.g., not a temporary file and size is stable)
    private static boolean isFileComplete(File file) {
        String fileName = file.getName().toLowerCase();
        // Skip files that are likely incomplete
        if (fileName.endsWith(".tmp") || fileName.endsWith(".crdownload")) {
            return false;
        }

        // Check if file size is stable with retry mechanism
        for (int attempt = 0; attempt < RETRY_ATTEMPTS; attempt++) {
            long initialSize = file.length();
            try {
                TimeUnit.MILLISECONDS.sleep(STABILITY_CHECK_MS);
            } catch (InterruptedException e) {
                System.out.println("Stability check interrupted");
                Thread.currentThread().interrupt();
                return false;
            }
            if (file.length() == initialSize) {
                if (!isFileBeingUsed(file)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Additional method to check if the file is being used by another process
    private static boolean isFileBeingUsed(File file) {
        Path path = file.toPath();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            // Try to lock the file for reading
            FileLock lock = channel.tryLock();
            if (lock != null) {
                lock.release();
                return false;
            }
        } catch (IOException e) {
            // If IOException occurs, the file might be in use
            return true;
        }
        return false;
    }

    // Method to move all existing files from the source folder to the target folder
    private static void moveExistingFiles(File sourceFolder, File targetFolder) {
        File[] files = sourceFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && isFileComplete(file)) {
                    System.out.println("Moving existing file: " + file.getName());
                    moveFileToTarget(file, targetFolder);
                } else if (file.isFile()) {
                    System.out.println("Skipping file (likely incomplete or in use): " + file.getName());
                }
            }
        }
    }
}

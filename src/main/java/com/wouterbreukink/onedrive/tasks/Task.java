package com.wouterbreukink.onedrive.tasks;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.util.Preconditions;
import com.wouterbreukink.onedrive.TaskQueue;
import com.wouterbreukink.onedrive.client.OneDriveItem;
import com.wouterbreukink.onedrive.client.OneDriveProvider;
import com.wouterbreukink.onedrive.filesystem.FileSystemProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.file.FileUtil;
import utils.file.path.PathPatternMatcherGroup;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wouterbreukink.onedrive.CommandLineOpts.getCommandLineOpts;

public abstract class Task implements Runnable, Comparable<Task> {
    private static final Logger log = LoggerFactory.getLogger(Task.class);
    private static AtomicInteger taskIdCounter = new AtomicInteger(1);
    protected final TaskQueue queue;
    protected final OneDriveProvider api;
    protected final FileSystemProvider fileSystem;
    protected final TaskReporter reporter;

    private final int id;
    private int attempt;

    protected Task(TaskOptions options) {
        this.queue = Preconditions.checkNotNull(options.getQueue());
        this.api = Preconditions.checkNotNull(options.getApi());
        this.fileSystem = Preconditions.checkNotNull(options.getFileSystem());
        this.reporter = Preconditions.checkNotNull(options.getReporter());
        this.id = taskIdCounter.getAndIncrement();
        this.attempt = 0;
    }

    protected static boolean isSizeInvalid(File localFile) {
        return isSizeInvalid(localFile.getPath(), localFile.length());
    }

    protected static boolean isSizeInvalid(OneDriveItem remoteFile) {
        return isSizeInvalid(remoteFile.getFullName(), remoteFile.getSize());
    }

    private static boolean isSizeInvalid(String filename, long size) {
        int maxSizeKb = getCommandLineOpts().getMaxSizeKb();
        if (maxSizeKb > 0 && size > maxSizeKb * 1024) {
            log.debug(
                "Skipping file {} - size is {}KB (bigger than maximum of {}KB)",
                filename,
                size / 1024,
                maxSizeKb
            );
            return true;
        }

        return false;
    }

    protected static boolean isIgnored(OneDriveItem rootFile, OneDriveItem remoteFile) {
        String rootFullPath = rootFile.getFullName(), fileFullPath = remoteFile.getFullName();

        if (fileFullPath.equals(rootFullPath)) {
            // root should not be ignored.
            return false;
        } else {
            if (fileFullPath.startsWith(rootFullPath)) {
                String relativePath = fileFullPath.substring(rootFullPath.length());

                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }

                if (remoteFile.isDirectory() && !relativePath.endsWith("/")) {
                    relativePath += "/";
                }

                boolean ignored = isIgnored(relativePath);

                if (ignored) {
                    log.debug("Skipping ignored remote file {}", relativePath);
                }

                return ignored;
            } else {
                // not expecting this, something wrong here
                log.debug("rootFullPath: {}, fileFullPath: {}", rootFullPath);
                return false;
            }
        }
    }

    protected static boolean isIgnored(File rootFile, File localFile) {
        String relativePath = FileUtil.getRelativePath(rootFile, localFile);

        if ("".equals(relativePath)) {
            // Root file cannot be ignored!
            return false;
        } else {
            boolean ignored = isIgnored(relativePath);

            if (ignored) {
                log.debug("Skipping ignored local file {}", relativePath);
            }

            return ignored;
        }
    }

    private static boolean isIgnored(String name) {
        PathPatternMatcherGroup ignoredMatcherGroup = getCommandLineOpts().getIgnoredMatcherGroup();

        return ignoredMatcherGroup != null && ignoredMatcherGroup.match(name);
    }

    protected TaskOptions getTaskOptions() {
        return new TaskOptions(queue, api, fileSystem, reporter);
    }

    protected abstract int priority();

    protected abstract void taskBody() throws IOException;

    protected String getId() {
        return this.id + ":" + this.attempt;
    }

    public void run() {
        attempt++;
        try {
            log.debug("Starting task {}:{} - {}", id, attempt, this.toString());
            taskBody();
            return;
        } catch (HttpResponseException ex) {
            switch (ex.getStatusCode()) {
                case 401:
                    log.warn("Task {}: [{}] encountered {}", getId(), this.toString(), ex.getMessage(), this.toString());
                    break;
                case 500:
                case 502:
                case 503:
                case 504:
                    log.warn(
                        "Task {}: [{}] encountered {} - sleeping 10 seconds",
                        getId(), this.toString(), ex.getMessage()
                    );
                    queue.suspend(10);
                    break;
                case 429:
                case 509:
                    log.warn(
                        "Task {}: [{}] encountered {} - sleeping 60 seconds",
                        getId(), this.toString(), ex.getMessage()
                    );
                    queue.suspend(60);
                    break;
                default:
                    log.warn("Task {}: [{}] encountered {}", getId(), this.toString(), ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("Task {}: [{}] encountered exception", getId(), this.toString(), ex);
            queue.suspend(1);
        }

        if (attempt < getCommandLineOpts().getTries()) {
            queue.add(this);
        } else {
            reporter.error();
            log.error("Task {} did not complete - {}", id, this.toString());
        }
    }

    @SuppressWarnings("NullableProblems")
    public int compareTo(Task o) {
        return o.priority() - priority();
    }

    public static class TaskOptions {
        private final TaskQueue queue;
        private final OneDriveProvider api;
        private final FileSystemProvider fileSystem;
        private final TaskReporter reporter;

        public TaskOptions(TaskQueue queue, OneDriveProvider api, FileSystemProvider fileSystem, TaskReporter reporter) {
            this.queue = queue;
            this.api = api;
            this.fileSystem = fileSystem;
            this.reporter = reporter;
        }

        public TaskQueue getQueue() {
            return queue;
        }

        public OneDriveProvider getApi() {
            return api;
        }

        public FileSystemProvider getFileSystem() {
            return fileSystem;
        }

        public TaskReporter getReporter() {
            return reporter;
        }
    }
}

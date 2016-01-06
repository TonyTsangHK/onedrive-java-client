package com.wouterbreukink.onedrive.tasks;

import com.google.api.client.util.Preconditions;
import com.wouterbreukink.onedrive.client.OneDriveItem;
import com.wouterbreukink.onedrive.client.OneDriveUploadSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.string.FormatUtils;

import java.io.File;
import java.io.IOException;

import static com.wouterbreukink.onedrive.CommandLineOpts.getCommandLineOpts;
import static com.wouterbreukink.onedrive.LogUtils.readableFileSize;
import static com.wouterbreukink.onedrive.LogUtils.readableTime;

public class UploadTask extends Task {
    private static final Logger log = LoggerFactory.getLogger(UploadTask.class);

    private final OneDriveItem parent;
    private final File localFile, localRoot;
    private final boolean replace;

    public UploadTask(TaskOptions options, OneDriveItem parent, File localRoot, File localFile, boolean replace) {

        super(options);

        this.parent = Preconditions.checkNotNull(parent);
        this.localFile = Preconditions.checkNotNull(localFile);
        this.localRoot = Preconditions.checkNotNull(localRoot);
        this.replace = replace;

        if (!parent.isDirectory()) {
            throw new IllegalArgumentException("Specified parent is not a folder");
        }
    }

    public int priority() {
        return 50;
    }

    @Override
    public String toString() {
        return "Upload " + parent.getFullName() + localFile.getName();
    }

    @Override
    protected void taskBody() throws IOException {
        if (isIgnored(localRoot, localFile)) {
            reporter.skipped();
            return;
        }

        if (localFile.isDirectory()) {
            OneDriveItem newParent = api.createFolder(parent, localFile.getName());

            //noinspection ConstantConditions
            for (File f : localFile.listFiles()) {
                queue.add(new UploadTask(getTaskOptions(), newParent, localRoot, f, false));
            }
        } else {

            if (isSizeInvalid(localFile)) {
                reporter.skipped();
                return;
            }

            long startTime = System.currentTimeMillis();

            OneDriveItem response;
            if (localFile.length() > getCommandLineOpts().getSplitAfter() * 1024 * 1024) {
                int tryCount = 0;
                OneDriveUploadSession session = api.startUploadSession(parent, localFile);

                while (!session.isComplete()) {
                    long startTimeInner = System.currentTimeMillis();

                    try {
                        // We don't want to keep retrying infinitely
                        if (tryCount == getCommandLineOpts().getTries()) {
                            break;
                        }

                        api.uploadChunk(session);

                        long elapsedTimeInner = System.currentTimeMillis() - startTimeInner;

                        log.info(
                            "Uploaded chunk (progress {}) of {} ({}/s) for file {}",
                            FormatUtils.formatNumber(((double) session.getTotalUploaded() / session.getFile().length()) * 100, 1),
                            readableFileSize(session.getLastUploaded()),
                            elapsedTimeInner > 0 ? readableFileSize(session.getLastUploaded() / (elapsedTimeInner / 1000d)) : 0,
                            parent.getFullName() + localFile.getName()
                        );

                        // After a successful upload we'll reset the tryCount
                        tryCount = 0;

                    } catch (IOException ex) {
                        log.warn(
                            "Encountered '{}' while uploading chunk of {} for file {}",
                            ex.getMessage(),
                            readableFileSize(session.getLastUploaded()),
                            parent.getFullName() + localFile.getName()
                        );

                        tryCount++;
                    }
                }

                if (!session.isComplete()) {
                    throw new IOException(String.format("Gave up on multi-part upload after %s retries", getCommandLineOpts().getTries()));
                }

                response = session.getItem();
            } else {
                response = replace ? api.replaceFile(parent, localFile) : api.uploadFile(parent, localFile);
            }

            long elapsedTime = System.currentTimeMillis() - startTime;

            log.info(
                "Uploaded {} in {} ({}/s) to {} file {}",
                readableFileSize(localFile.length()),
                readableTime(elapsedTime),
                elapsedTime > 0 ? readableFileSize(localFile.length() / (elapsedTime / 1000d)) : 0,
                replace ? "replace" : "new",
                response.getFullName()
            );

            reporter.fileUploaded(replace, localFile.length());
        }
    }
}


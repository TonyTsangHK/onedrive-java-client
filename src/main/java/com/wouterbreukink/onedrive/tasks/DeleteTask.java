package com.wouterbreukink.onedrive.tasks;

import com.google.api.client.util.Preconditions;
import com.wouterbreukink.onedrive.client.OneDriveItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class DeleteTask extends Task {
    public static final int PRIORITY = 100;
    
    private static final Logger log = LoggerFactory.getLogger(DeleteTask.class.getName());
    private final OneDriveItem remoteFile;
    private final File localFile;

    public DeleteTask(TaskOptions options, OneDriveItem remoteFile) {
        super(options);

        this.remoteFile = Preconditions.checkNotNull(remoteFile);
        this.localFile = null;
    }

    public DeleteTask(TaskOptions options, File localFile) {

        super(options);

        this.localFile = Preconditions.checkNotNull(localFile);
        this.remoteFile = null;
    }

    public int priority() {
        return PRIORITY;
    }

    @Override
    public String toString() {
        if (localFile != null) {
            return "Delete local file " + localFile.getPath();
        } else {
            return "Delete remote file " + remoteFile.getFullName();
        }
    }

    @Override
    protected void taskBody() throws IOException {
        if (localFile != null) {
            fileSystem.delete(localFile);
            reporter.localDeleted();
            log.info("Deleted local file {}", localFile.getPath());
        } else {
            api.delete(remoteFile);
            reporter.remoteDeleted();
            log.info("Deleted remote file {}", remoteFile.getFullName());
        }
    }
}

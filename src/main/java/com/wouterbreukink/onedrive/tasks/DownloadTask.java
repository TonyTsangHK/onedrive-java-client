package com.wouterbreukink.onedrive.tasks;

import com.google.api.client.util.Preconditions;
import com.wouterbreukink.onedrive.client.OneDriveItem;
import utils.hash.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.wouterbreukink.onedrive.LogUtils.readableFileSize;
import static com.wouterbreukink.onedrive.LogUtils.readableTime;

public class DownloadTask extends Task {

    private static final Logger log = LoggerFactory.getLogger(DownloadTask.class);
    private final File parent;
    private final OneDriveItem remoteFile;
    private final boolean replace;

    public DownloadTask(TaskOptions options, File parent, OneDriveItem remoteFile, boolean replace) {
        super(options);

        this.parent = Preconditions.checkNotNull(parent);
        this.remoteFile = Preconditions.checkNotNull(remoteFile);
        this.replace = Preconditions.checkNotNull(replace);

        if (!parent.isDirectory()) {
            throw new IllegalArgumentException("Specified parent is not a folder");
        }
    }

    public int priority() {
        return 50;
    }

    @Override
    public String toString() {
        return "Download " + remoteFile.getFullName();
    }

    // Check the remote file can be downloaded or not.
    private boolean canDownload(OneDriveItem remoteFile) {
        // checking file size & has hashes, if the file size is not empty but has no hashes, it is most likely a onenote notebook.
        return remoteFile.getSize() > 0 && remoteFile.hasHashes();
    }

    @Override
    protected void taskBody() throws IOException {
        if (isIgnored(remoteFile)) {
            reporter.skipped();
            return;
        }

        if (remoteFile.isDirectory()) {
            File newParent = fileSystem.createFolder(parent, remoteFile.getName());

            for (OneDriveItem item : api.getChildren(remoteFile)) {
                queue.add(new DownloadTask(getTaskOptions(), newParent, item, false));
            }
        } else {
            if (isSizeInvalid(remoteFile)) {
                reporter.skipped();
                return;
            }

            long startTime = System.currentTimeMillis();

            File downloadFile = null;

            try {
                if (remoteFile.getSize() == 0) {
                    // Remote file is empty, create a corresponding empty local file.
                    downloadFile = fileSystem.createFile(parent, remoteFile.getName());

                    fileSystem.setAttributes(
                        downloadFile,
                        remoteFile.getCreatedDateTime(),
                        remoteFile.getLastModifiedDateTime()
                    );

                    reporter.fileDownloaded(replace, remoteFile.getSize());
                } else if (canDownload(remoteFile)) {
                    log.info(
                        "Downloading file {}, size: {} ... ",
                        readableFileSize(remoteFile.getSize()),
                        remoteFile.getFullName()
                    );
                    downloadFile = fileSystem.createFile(parent, remoteFile.getName() + ".tmp");

                    api.download(remoteFile, downloadFile);

                    long elapsedTime = System.currentTimeMillis() - startTime;

                    log.info("Downloaded {} in {} ({}/s) to {} file {}",
                        readableFileSize(remoteFile.getSize()),
                        readableTime(elapsedTime),
                        elapsedTime > 0 ? readableFileSize(remoteFile.getSize() / (elapsedTime / 1000d)) : 0,
                        replace ? "replace" : "new",
                        remoteFile.getFullName()
                    );

                    // Do a CRC check on the downloaded file if available, otherwise check the sha1sum
                    if (remoteFile.getHashes().hasCrc32Hash()) {
                        if (!fileSystem.verifyCrc(downloadFile, remoteFile.getCrc32())) {
                            throw new IOException(String.format("Download of file '%s' failed", remoteFile.getFullName()));
                        }
                    } else if (remoteFile.getHashes().hasSha1Hash()) {
                        if (!fileSystem.verifySha1Hash(downloadFile, remoteFile.getHashes().getSha1Hash())) {
                            throw new IOException(String.format("Download of file '%s' failed, hash mismatch, remote hash: %s, localHash: %s", remoteFile.getFullName(), remoteFile.getHashes().getSha1Hash(), HashUtil.getFileSha1Hash(downloadFile)));
                        }
                    } else {
                        throw new IOException(String.format("Download of file '%s' failed, no hash or crc available of remote file!", remoteFile.getFullName()));
                    }

                    fileSystem.setAttributes(
                            downloadFile,
                            remoteFile.getCreatedDateTime(),
                            remoteFile.getLastModifiedDateTime());

                    fileSystem.replaceFile(new File(parent, remoteFile.getName()), downloadFile);
                    reporter.fileDownloaded(replace, remoteFile.getSize());
                } else {
                    // Skip downloading the remote file
                    // Considering create a web link file for this kind of files, if webUrl exists.
                    log.info("Skipping undownloadable file: {}", remoteFile.getFullName());
                    reporter.skipped();
                }
            } catch (IOException e) {
                if (downloadFile != null) {
                    if (!downloadFile.delete()) {
                        log.warn("Unable to remove temporary file {}", downloadFile.getPath());
                    }
                }

                throw e;
            }
        }
    }
}


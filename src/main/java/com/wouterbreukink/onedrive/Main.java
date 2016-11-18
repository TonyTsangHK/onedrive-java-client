package com.wouterbreukink.onedrive;

import com.wouterbreukink.onedrive.client.OneDriveAPIException;
import com.wouterbreukink.onedrive.client.OneDriveItem;
import com.wouterbreukink.onedrive.client.OneDriveProvider;
import com.wouterbreukink.onedrive.client.authoriser.AuthorisationProvider;
import com.wouterbreukink.onedrive.client.resources.Drive;
import com.wouterbreukink.onedrive.filesystem.FileSystemProvider;
import com.wouterbreukink.onedrive.tasks.CheckTask;
import com.wouterbreukink.onedrive.tasks.Task;
import com.wouterbreukink.onedrive.tasks.TaskReporter;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.data.ImmutableMap;
import utils.file.FileUtil;
import utils.json.parser.JsonParser;
import utils.string.FormatUtils;
import utils.string.StringUtil;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.wouterbreukink.onedrive.CommandLineOpts.getCommandLineOpts;
import static com.wouterbreukink.onedrive.LogUtils.readableFileSize;

/***
 * OneDrive Java Client
 * Copyright (C) 2015 Wouter Breukink
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // Program version, lazy loading
    private static String version = null;

    private static ImmutableMap<String, Object> APP_CONFIG_MAP = null;

    public static void main(String[] args) throws Exception {
        // Parse command line args
        try {
            CommandLineOpts.initialise(args);
        } catch (ParseException ex) {
            log.error("Unable to parse command line arguments - {}", ex.getMessage());
            CommandLineOpts.printHelp();
            return;
        }

        // Initialize app configs
        File appFile = getCommandLineOpts().getAppFile().toFile();


        Map<String, Object> map = JsonParser.getInstance().parseMap(FileUtil.getFileContent(appFile));

        if (map != null) {
            APP_CONFIG_MAP = new ImmutableMap<>(map);
        } else {
            APP_CONFIG_MAP = new ImmutableMap<>();
        }

        if (getCommandLineOpts().help()) {
            CommandLineOpts.printHelp();
            return;
        }

        if (getCommandLineOpts().version()) {
            log.info("onedrive-java-client version {}", getVersion());
            return;
        }

        // Initialise a log file (if set)
        if (getCommandLineOpts().getLogFile() != null) {
            String logFileName = LogUtils.addFileLogger(getCommandLineOpts().getLogFile());
            log.info("Writing log output to {}", logFileName);
        }

        if (getCommandLineOpts().isAuthorise()) {
            AuthorisationProvider.FACTORY.printAuthInstructions();
            return;
        }

        if (
            getCommandLineOpts().getLocalPath() == null || 
            getCommandLineOpts().getRemotePath() == null || 
            getCommandLineOpts().getDirection() == null
        ) {
            log.error("Must specify --local, --remote and --direction");
            CommandLineOpts.printHelp();
            return;
        }
        
        // Initialise the OneDrive authorisation
        AuthorisationProvider authoriser;
        try {
            authoriser = AuthorisationProvider.FACTORY.create(getCommandLineOpts().getKeyFile());
            authoriser.getAccessToken();
        } catch (OneDriveAPIException ex) {
            log.error("Unable to authorise client: {}", ex.getMessage());
            log.error("Re-run the application with --authorise");
            return;
        }

        // Initialise the providers
        OneDriveProvider api;
        FileSystemProvider fileSystem;
        if (getCommandLineOpts().isDryRun()) {
            log.warn("This is a dry run - no changes will be made");
            api = OneDriveProvider.FACTORY.readOnlyApi(authoriser);
            fileSystem = FileSystemProvider.FACTORY.readOnlyProvider();
        } else {
            api = OneDriveProvider.FACTORY.readWriteApi(authoriser);
            fileSystem = FileSystemProvider.FACTORY.readWriteProvider();
        }

        // Report on progress
        TaskReporter reporter = new TaskReporter();

        // Get the primary drive
        Drive primary = api.getDefaultDrive();

        // Report quotas
        log.info("Using drive with id '{}' ({}). Usage {} of {} ({}%)",
                primary.getId(),
                primary.getDriveType(),
                readableFileSize(primary.getQuota().getUsed()),
                readableFileSize(primary.getQuota().getTotal()),
                FormatUtils.formatNumber(((double) primary.getQuota().getUsed() / primary.getQuota().getTotal()) * 100)
        );

        // Check the given root folder
        OneDriveItem rootFolder;
        try {
            rootFolder = api.getPath(getCommandLineOpts().getRemotePath());
        } catch (OneDriveAPIException e) {
            if (e.getCode() == 404) {
                log.error("Specified remote folder '{}' does not exist", getCommandLineOpts().getRemotePath());
            } else {
                log.error("Unable to locate remote folder '{}' - {}", getCommandLineOpts().getRemotePath(), e.getMessage());
            }
            return;
        }

        if (rootFolder == null || !rootFolder.isDirectory()) {
            log.error("Specified root '{}' is not a folder", getCommandLineOpts().getRemotePath());
            return;
        }

        // Check the target folder
        File localFolder = new File(getCommandLineOpts().getLocalPath());

        if (!localFolder.exists() || !localFolder.isDirectory()) {
            log.error("Specified local path '{}' is not a valid folder", getCommandLineOpts().getLocalPath());
            return;
        }

        log.info("Starting at root folder '{}'", rootFolder.getFullName());

        // Start synchronisation operation at the root
        final TaskQueue queue = new TaskQueue();

        File localFile = new File(getCommandLineOpts().getLocalPath());

        queue.add(
            new CheckTask(
                new Task.TaskOptions(queue, api, fileSystem, reporter),
                rootFolder, rootFolder, localFile, localFile
            )
        );

        // Get a bunch of threads going
        ExecutorService executorService = Executors.newFixedThreadPool(getCommandLineOpts().getThreads());

        for (int i = 0; i < getCommandLineOpts().getThreads(); i++) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        //noinspection InfiniteLoopStatement
                        while (true) {
                            Task taskToRun = null;
                            try {
                                taskToRun = queue.take();
                                taskToRun.run();
                            } finally {
                                if (taskToRun != null) {
                                    queue.done(taskToRun);
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        queue.waitForCompletion();
        log.info("Synchronisation complete");
        reporter.report();

        System.exit(0);
    }

    public static String getVersion() {
        if (version == null) {
            version = FileUtil.getFileContent(Main.class.getResourceAsStream("/version"));
            if (StringUtil.isEmptyString(version)) {
                version = "UNKNOWN";
            }
        }

        return version;
    }

    public static Object getAppConfig(String key) {
        return APP_CONFIG_MAP.get(key);
    }
}

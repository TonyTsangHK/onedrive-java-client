package com.wouterbreukink.onedrive.filesystem;

import com.wouterbreukink.onedrive.client.facets.HashesFacet;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public interface FileSystemProvider {
    void delete(File file) throws IOException;

    File createFolder(File file, String name) throws IOException;

    File createFile(File file, String name) throws IOException;

    void replaceFile(File original, File replacement) throws IOException;

    boolean setAttributes(File downloadFile, Date created, Date lastModified) throws IOException;

    boolean verifyCrc(File file, long crc) throws IOException;

    boolean verifySha1Hash(File file, String sha1Hash) throws IOException;

    FileMatch verifyMatch(File file, HashesFacet hashesFacet, long fileSize, Date created, Date lastModified) throws IOException;

    FileMatch verifyMatch(File file, Date created, Date lastModified) throws IOException;

    /**
     * Get the CRC32 Checksum for a file
     *
     * @param file The file to check
     * @return The CRC32 checksum of the file
     * @throws IOException
     */
    long getChecksum(File file) throws IOException;

    enum FileMatch {
        YES,
        CRC,
        NO
    }

    class FACTORY {
        public static FileSystemProvider readOnlyProvider() {
            return new ROFileSystemProvider();
        }

        public static FileSystemProvider readWriteProvider() {
            return new RWFileSystemProvider();
        }
    }

}

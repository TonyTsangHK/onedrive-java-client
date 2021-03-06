package com.wouterbreukink.onedrive.client;

import com.google.api.client.util.Throwables;
import com.wouterbreukink.onedrive.client.facets.HashesFacet;
import com.wouterbreukink.onedrive.client.resources.Item;
import com.wouterbreukink.onedrive.client.resources.ItemReference;
import com.wouterbreukink.onedrive.client.serialization.JsonDateSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Date;

public interface OneDriveItem {
    Logger log = LoggerFactory.getLogger(OneDriveItem.class);

    String getId();

    boolean isDirectory();

    String getName();

    String getFullName();

    HashesFacet getHashes();

    boolean hasHashes();

    long getCrc32();

    long getSize();

    Date getCreatedDateTime();

    Date getLastModifiedDateTime();

    OneDriveItem getParent();

    class FACTORY {
        public static OneDriveItem create(final OneDriveItem parent, final String name, final boolean isDirectory) {
            return new OneDriveItem() {
                public String getId() {
                    return null;
                }

                public boolean isDirectory() {
                    return isDirectory;
                }

                public String getName() {
                    return name;
                }

                public String getFullName() {
                    if (parent == null) {
                        return "/";
                    } else {
                        return parent.getFullName() + name + (isDirectory ? "/" : "");
                    }
                }

                @Override
                public HashesFacet getHashes() {
                    return null;
                }

                @Override
                public boolean hasHashes() {
                    return false;
                }

                @Override
                public long getCrc32() {
                    return 0;
                }

                @Override
                public long getSize() {
                    return 0;
                }

                @Override
                public Date getCreatedDateTime() {
                    return null;
                }

                @Override
                public Date getLastModifiedDateTime() {
                    return null;
                }

                @Override
                public OneDriveItem getParent() {
                    return parent;
                }
            };
        }

        public static OneDriveItem create(final Item item) {
            return new OneDriveItem() {
                // If parent reference is null set parent to null.
                private OneDriveItem parent = (item.getParentReference() == null)? null : create(item.getParentReference());

                @Override
                public String getId() {
                    return item.getId();
                }

                @Override
                public boolean isDirectory() {
                    return item.getFolder() != null;
                }

                @Override
                public String getName() {
                    return item.getName();
                }

                @Override
                public String getFullName() {
                    if (parent == null) {
                        return "/";
                    } else {
                        return parent.getFullName() + item.getName() + (isDirectory() ? "/" : "");
                    }
                }

                @Override
                public HashesFacet getHashes() {
                    return item.getFile().getHashes();
                }

                @Override
                public boolean hasHashes() {
                    return item.getFile() != null && item.getFile().getHashes() != null;
                }

                @Override
                public long getCrc32() {
                    return item.getFile().getHashes().getCrc32();
                }

                @Override
                public long getSize() {
                    return item.getSize();
                }

                @Override
                public Date getCreatedDateTime() {
                    return JsonDateSerializer.INSTANCE.deserialize(item.getFileSystemInfo().getCreatedDateTime());
                }

                @Override
                public Date getLastModifiedDateTime() {
                    return JsonDateSerializer.INSTANCE.deserialize(item.getFileSystemInfo().getLastModifiedDateTime());
                }

                @Override
                public OneDriveItem getParent() {
                    return parent;
                }
            };
        }

        public static OneDriveItem create(final ItemReference parent) {
            return new OneDriveItem() {
                @Override
                public String getId() {
                    return parent.getId();
                }

                @Override
                public boolean isDirectory() {
                    return true;
                }

                @Override
                public String getName() {
                    return null;
                }

                public String getFullName() {
                    if (parent == null) {
                        return "/";
                    } else {
                        if (parent.getPath() == null) {
                            return null;
                        }

                        int index = parent.getPath().indexOf(':');

                        try {
                            return URLDecoder.decode(index > 0 ? parent.getPath().substring(index + 1) : parent.getPath(), "UTF-8") + "/";
                        } catch (UnsupportedEncodingException e) {
                            throw Throwables.propagate(e);
                        }
                    }
                }

                @Override
                public HashesFacet getHashes() {
                    return null;
                }

                @Override
                public boolean hasHashes() {
                    return false;
                }

                @Override
                public long getCrc32() {
                    return 0;
                }

                @Override
                public long getSize() {
                    return 0;
                }

                @Override
                public Date getCreatedDateTime() {
                    return null;
                }

                @Override
                public Date getLastModifiedDateTime() {
                    return null;
                }

                @Override
                public OneDriveItem getParent() {
                    return null;
                }
            };
        }
    }
}

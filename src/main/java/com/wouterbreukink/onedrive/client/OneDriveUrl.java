package com.wouterbreukink.onedrive.client;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.util.Key;
import com.wouterbreukink.onedrive.Main;
import utils.data.DataManipulator;
import utils.string.StringUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

class OneDriveUrl extends GenericUrl {
    private static final String rootUrl;
    @Key("$skiptoken")
    private String token;

    static {
        String apiUrl = DataManipulator.extractString(Main.getAppConfig("apiUrl"), ""),
            apiVersion = DataManipulator.extractString(Main.getAppConfig("apiVersion"), "");

        rootUrl = apiUrl + apiVersion;
    }

    public OneDriveUrl(String encodedUrl) {
        super(encodedUrl);
    }

    public static OneDriveUrl defaultDrive() {
        return new OneDriveUrl(rootUrl + "/drive");
    }

    public static OneDriveUrl driveRoot() {
        return new OneDriveUrl(rootUrl + "/drive/root");
    }
    
    public static OneDriveUrl children(String id) {
        return new OneDriveUrl(rootUrl + "/drive/items/" + id + "/children");
    }

    public static OneDriveUrl putContent(String id, String name) {
        return new OneDriveUrl(rootUrl + "/drive/items/" + id + ":/" + encode(name) + ":/content");
    }

    public static OneDriveUrl postMultiPart(String id) {
        return new OneDriveUrl(rootUrl + "/drive/items/" + id + "/children");
    }

    public static OneDriveUrl createUploadSession(String id, String name) {
        return new OneDriveUrl(rootUrl + "/drive/items/" + id + ":/" + encode(name) + ":/upload.createSession");
    }

    public static OneDriveUrl delta(String id) {
        return new OneDriveUrl(rootUrl + "/drive/items/" + id + "/view.delta");
    }

    public static OneDriveUrl getPath(String path) {
        // Add :root for remote root reference, since / may resolve to other path at MINGW console.
        if (StringUtil.stringMatchOnce(path, "/", ":root")) {
            // Allow root path to be synced
            return new OneDriveUrl(rootUrl + "/drive/root:/");
        } else {
            return new OneDriveUrl(rootUrl + "/drive/root:/" + encode(path).replace("%5C", "/"));
        }
    }

    public static GenericUrl item(String id) {
        return new OneDriveUrl(rootUrl + "/drive/items/" + id);
    }

    public static GenericUrl content(String id) {
        return new OneDriveUrl(rootUrl + "/drive/items/" + id + "/content");
    }

    private static String encode(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    public void setToken(String token) {
        this.token = token;
    }
}


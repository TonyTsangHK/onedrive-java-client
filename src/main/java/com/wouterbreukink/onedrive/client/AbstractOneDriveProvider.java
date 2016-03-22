package com.wouterbreukink.onedrive.client;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: Tony Tsang
 * Date: 2016-03-22
 * Time: 10:09
 */
public abstract class AbstractOneDriveProvider implements OneDriveProvider {
    public <T> T executeAndParseRequest(HttpRequest req, Class<T> dataClass) throws IOException {
        HttpResponse resp = req.execute();
        T result = resp.parseAs(dataClass);
        resp.disconnect();
        return result;
    }
}

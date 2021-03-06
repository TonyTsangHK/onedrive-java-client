package com.wouterbreukink.onedrive.client.authoriser;

import com.google.api.client.http.*;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Preconditions;
import com.wouterbreukink.onedrive.Main;
import com.wouterbreukink.onedrive.client.OneDriveAPIException;
import com.wouterbreukink.onedrive.client.resources.Authorisation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.data.DataManipulator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class OneDriveAuthorisationProvider implements AuthorisationProvider {
    static final HttpTransport HTTP_TRANSPORT = new ApacheHttpTransport();
    static final JsonFactory JSON_FACTORY = new GsonFactory();
    private static final Logger log = LoggerFactory.getLogger(OneDriveAuthorisationProvider.class);
    private static final String clientId, clientSecret, oauthRedirectUrl, oauthAuthorizeUrl, oauthRedeemUrl;
    private Path keyFile;
    private Authorisation authorisation;
    private Date lastFetched;

    static {
        clientId = DataManipulator.extractString(Main.getAppConfig("id"), "");
        clientSecret = DataManipulator.extractString(Main.getAppConfig("secret"), "");
        oauthRedirectUrl = DataManipulator.extractString(Main.getAppConfig("oauthRedirectUrl"), "");
        oauthAuthorizeUrl = DataManipulator.extractString(Main.getAppConfig("oauthAuthorizeUrl"), "");
        oauthRedeemUrl = DataManipulator.extractString(Main.getAppConfig("oauthRedeemUrl"), "");
    }

    public OneDriveAuthorisationProvider(Path keyFile) throws IOException {
        this.keyFile = Preconditions.checkNotNull(keyFile);

        if (!Files.exists(keyFile) || !Files.isRegularFile(keyFile)) {
            throw new OneDriveAPIException(401, String.format("Specified key file '%s' cannot be found.", keyFile));
        }

        String[] keyFileContents = readToken();

        switch (keyFileContents.length) {
            case 0:
                throw new OneDriveAPIException(401, String.format("Key file '%s' is empty.", keyFile));
            case 1:
                String authCode = keyFileContents[0];

                // If the user has pasted the entire URL then parse it
                Pattern url = Pattern.compile(oauthRedirectUrl + ".*code=(.*)&.*");
                Matcher m = url.matcher(authCode);

                if (m.matches()) {
                    authCode = m.group(1);
                }

                getTokenFromCode(authCode);
                break;
            case 2:
                if (keyFileContents[0].equals(clientId)) {
                    getTokenFromRefreshToken(keyFileContents[1]);
                } else {
                    throw new OneDriveAPIException(401, "Key file does not match this application version.");
                }
                break;
            default:
                throw new OneDriveAPIException(401, "Expected key file with code and/or refresh token");
        }
    }

    public static void printAuthInstructions() {
        String authString =
                String.format(
                    "%s?client_id=%s&response_type=code&scope=wl.signin%%20wl.offline_access%%20onedrive.readwrite&client_secret=%s&redirect_uri=%s",
                    oauthAuthorizeUrl,
                    clientId,
                    clientSecret,
                    oauthRedirectUrl
                );

        log.info(
            "Authorisation instructions:\n" +
            "To authorise this application you must generate an authorisation token\n" +
            "Open the following in a browser, sign on, wait until you are redirected to a blank page and then store the url in the address bar in your key file.\n" +
            "Authorisation URL: {}", 
            authString
        );
    }

    @Override
    public String getAccessToken() throws IOException {
        if (authorisation != null) {
            // Refresh if we know it is needed
            if (lastFetched.after(new Date(lastFetched.getTime() + authorisation.getExpiresIn() * 1000))) {
                log.info("Authorisation token has expired - refreshing");
                getTokenFromRefreshToken(authorisation.getRefreshToken());
                saveToken();
            }

            return authorisation.getAccessToken();
        } else {
            throw new IllegalStateException("Authoriser has not been initialised");
        }
    }

    public void refresh() throws IOException {
        getTokenFromRefreshToken(authorisation.getRefreshToken());
        saveToken();
    }

    private void getTokenFromCode(final String code) throws IOException {
        log.debug("Fetching authorisation token using authorisation code");
        
        HttpRequest request =
                HTTP_TRANSPORT.createRequestFactory().buildPostRequest(
                    new GenericUrl(oauthRedeemUrl), 
                    new UrlEncodedContent(
                        DataManipulator.createMap(
                            Arrays.asList("client_id", "code", "grant_type", "redirect_uri"),
                            Arrays.asList(clientId, code, "authorization_code", oauthRedirectUrl)
                        )
                    )
                );

        request.setParser(new JsonObjectParser(JSON_FACTORY));

        processResponse(request.execute());
    }

    private void getTokenFromRefreshToken(final String refreshToken) throws IOException {
        log.debug("Fetching authorisation token using refresh token");

        HttpRequest request =
                HTTP_TRANSPORT.createRequestFactory().buildPostRequest(
                    new GenericUrl(oauthRedeemUrl), 
                    new UrlEncodedContent(
                        DataManipulator.createMap(
                            Arrays.asList("client_id", "refresh_token", "grant_type", "redirect_uri"),
                            Arrays.asList(clientId, refreshToken, "refresh_token", oauthRedirectUrl)
                        )
                    )
                );

        request.setParser(new JsonObjectParser(JSON_FACTORY));

        processResponse(request.execute());
    }

    private void processResponse(HttpResponse response) throws IOException {
        authorisation = response.parseAs(Authorisation.class);

        int statusCode = response.getStatusCode();

        response.disconnect();

        // Check for failures
        if (statusCode != 200 || authorisation.getError() != null) {
            throw new OneDriveAPIException(
                statusCode,
                String.format(
                    "Error code %d - %s (%s)",
                    response.getStatusCode(),
                    authorisation.getError(),
                    authorisation.getErrorDescription()
                )
            );
        }

        log.info("Fetched new authorisation token and refresh token for user {}", authorisation.getUserId());
        saveToken();
        lastFetched = new Date();
    }

    private String[] readToken() {
        try {
            return Files.readAllLines(keyFile, Charset.defaultCharset()).toArray(new String[1]);
        } catch (IOException e) {
            log.error("Unable to read key file", e);
        }

        return new String[0];
    }

    private void saveToken() throws OneDriveAPIException {
        try {
            String[] content = new String[]{clientId, authorisation.getRefreshToken()};
            Files.write(keyFile, Arrays.asList(content), Charset.defaultCharset());
        } catch (IOException e) {
            log.error("Unable to write to key file ", e);
        }
    }
}

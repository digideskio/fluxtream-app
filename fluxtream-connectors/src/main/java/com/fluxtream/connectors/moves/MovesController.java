package com.fluxtream.connectors.moves;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import com.fluxtream.Configuration;
import com.fluxtream.auth.AuthHelper;
import com.fluxtream.connectors.Connector;
import com.fluxtream.connectors.controllers.ControllerSupport;
import com.fluxtream.domain.ApiKey;
import com.fluxtream.domain.Guest;
import com.fluxtream.domain.Notification;
import com.fluxtream.services.GuestService;
import com.fluxtream.services.NotificationsService;
import com.fluxtream.utils.HttpUtils;
import net.sf.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.apache.log4j.Logger;


/**
 * User: candide
 * Date: 17/06/13
 * Time: 16:49
 */
@Controller
@RequestMapping(value = "/moves/oauth2")
public class MovesController {

    @Autowired
    Configuration env;

    @Autowired
    NotificationsService notificationsService;

    @Autowired
    GuestService guestService;

    static final Logger logger = Logger.getLogger(MovesController.class);


    @RequestMapping(value = "/token")
    public String getToken(HttpServletRequest request) throws IOException, ServletException {

        String redirectUri = getRedirectUri();

        // Check that the redirectUri is going to work
        final String validRedirectUrl = env.get("moves.validRedirectURL");
        if (!validRedirectUrl.startsWith(ControllerSupport.getLocationBase(request))) {
            final long guestId = AuthHelper.getGuestId();
            final String validRedirectBase = getBaseURL(validRedirectUrl);
            notificationsService.addNotification(guestId, Notification.Type.WARNING, "Adding a Moves connector only works when logged in through " + validRedirectBase +
            ".  You are logged in through " + ControllerSupport.getLocationBase(request) + ".<br>Please re-login via the supported URL or inform your Fluxtream administrator that the moves.validRedirectURL setting does not match your needs.");
            return "redirect:/app";
        }

        // Here we know that the redirectUri will work
        String approvalPageUrl = String.format("https://api.moves-app.com/oauth/v1/authorize?" +
                                               "redirect_uri=%s&" +
                                               "response_type=code&client_id=%s&" +
                                               "scope=activity location",
                                               redirectUri, env.get("moves.client.id"));

        return "redirect:" + approvalPageUrl;
    }

    public static String getBaseURL(String url) {
        try {
            URI uri = new URI(url);
            StringBuilder rootURI = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
            if(uri.getPort()!=-1) {
                rootURI.append(":" + uri.getPort());
            }
            return (rootURI.toString());
        }
        catch (URISyntaxException e) {
            return null;
        }
    }

    private String getRedirectUri() {
        // TODO: This should be checked against the moves.validRedirectURL property to make
        // sure that it will work.  Moves only accepts the specific redirect URI's which matches the one
        // configured for this key.
        return env.get("homeBaseUrl") + "moves/oauth2/swapToken";
    }

    @RequestMapping(value="swapToken")
    public String swapToken(HttpServletRequest request) throws IOException {
        final String errorMessage = request.getParameter("error");
        final Guest guest = AuthHelper.getGuest();
        if (errorMessage!=null) {
            notificationsService.addNotification(guest.getId(),
                                                 Notification.Type.ERROR,
                                                 errorMessage);
            return "redirect:/app";
        }
        final String code = request.getParameter("code");

        Map<String,String> parameters = new HashMap<String,String>();
        parameters.put("grant_type", "authorization_code");
        parameters.put("code", code);
        parameters.put("client_id", env.get("moves.client.id"));
        parameters.put("client_secret", env.get("moves.client.secret"));
        parameters.put("redirect_uri", getRedirectUri());
        final String json = HttpUtils.fetch("https://api.moves-app.com/oauth/v1/access_token", parameters);

        JSONObject token = JSONObject.fromObject(json);

        if (token.has("error")) {
            String errorCode = token.getString("error");
            notificationsService.addNotification(guest.getId(),
                                                 Notification.Type.ERROR,
                                                 errorCode);
            return "redirect:/app";
        }

        final String refresh_token = token.getString("refresh_token");

        // Create the entry for this new apiKey in the apiKey table and populate
        // ApiKeyAttributes with all of the keys fro oauth.properties needed for
        // subsequent update of this connector instance.
        ApiKey apiKey = guestService.createApiKey(guest.getId(), Connector.getConnector("moves"));

        guestService.setApiKeyAttribute(apiKey,
                                        "accessToken", token.getString("access_token"));
        guestService.setApiKeyAttribute(apiKey,
                                        "tokenExpires", String.valueOf(System.currentTimeMillis() + (token.getLong("expires_in")*1000)));
        guestService.setApiKeyAttribute(apiKey,
                                        "refreshToken", refresh_token);

        return "redirect:/app/from/moves";
    }

    String getAccessToken(final ApiKey apiKey) throws IOException {
        final String expiresString = guestService.getApiKeyAttribute(apiKey, "tokenExpires");
        long expires = Long.valueOf(expiresString);
        if (expires<System.currentTimeMillis())
            refreshToken(apiKey);
        return guestService.getApiKeyAttribute(apiKey, "accessToken");
    }

    private void refreshToken(final ApiKey apiKey) throws IOException {
        // Check to see if we are running on a mirrored test instance
        // and should therefore refrain from swapping tokens lest we
        // invalidate an existing token instance
        String disableTokenSwap = env.get("disableTokenSwap");
        if(disableTokenSwap!=null && disableTokenSwap.equals("true")) {
            String msg = "**** Skipping refreshToken for moves connector instance because disableTokenSwap is set on this server";
                                            ;
            StringBuilder sb2 = new StringBuilder("module=MovesController component=MovesController action=refreshToken apiKeyId=" + apiKey.getId())
            			    .append(" message=\"").append(msg).append("\"");
            logger.info(sb2.toString());
            System.out.println(msg);
            return;
        }

        // We're not on a mirrored test server.  Try to swap the expired
        // access token for a fresh one.  Typically moves access tokens are good for
        // 180 days from time of issue.
        String swapTokenUrl = "https://api.moves-app.com/oauth/v1/access_token";

        final String refreshToken = guestService.getApiKeyAttribute(apiKey, "refreshToken");
        Map<String,String> params = new HashMap<String,String>();
        params.put("refresh_token", refreshToken);
        params.put("client_id", guestService.getApiKeyAttribute(apiKey, "moves.client.id"));
        params.put("client_secret", guestService.getApiKeyAttribute(apiKey, "moves.client.secret"));
        params.put("grant_type", "refresh_token");

        String fetched;
        try {
            fetched = HttpUtils.fetch(swapTokenUrl, params);
        } catch (IOException e) {
            throw e;
        }

        JSONObject token = JSONObject.fromObject(fetched);
        final long expiresIn = token.getLong("expires_in");
        final String access_token = token.getString("access_token");

        final long now = System.currentTimeMillis();
        long tokenExpires = now + (expiresIn*1000);

        guestService.setApiKeyAttribute(apiKey,
                                        "accessToken", access_token);
        guestService.setApiKeyAttribute(apiKey,
                                        "tokenExpires", String.valueOf(tokenExpires));

    }


}

package com.netflix.conductor.auth;

import com.netflix.conductor.core.config.Configuration;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.oauth.client.OAuthClientFilter;
import com.sun.jersey.oauth.signature.OAuthParameters;
import com.sun.jersey.oauth.signature.OAuthSecrets;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Oleksiy Lysak
 */
@Singleton
public class SundogOAuth1Manager implements OAuth1Manager {
    private final static String PARAM_AUTH_URL = "conductor.oauth1.sundog.url";
    private final static String PARAM_CONSUMER_KEY = "conductor.oauth1.sundog.consumer.key";
    private final static String PARAM_CONSUMER_SECRET = "conductor.oauth1.sundog.consumer.secret";
    private final String authUrl;
    private final String consumerKey;
    private final String consumerSecret;

    @Inject
    public SundogOAuth1Manager(Configuration config) {
        authUrl = config.getProperty(PARAM_AUTH_URL, null);
        if (StringUtils.isEmpty(authUrl))
            throw new RuntimeException("No " + PARAM_AUTH_URL + " parameter defined");

        consumerKey = config.getProperty(PARAM_CONSUMER_KEY, null);
        if (StringUtils.isEmpty(consumerKey))
            throw new RuntimeException("No " + PARAM_CONSUMER_KEY + " parameter defined");

        consumerSecret = config.getProperty(PARAM_CONSUMER_SECRET, null);
        if (StringUtils.isEmpty(consumerSecret))
            throw new RuntimeException("No " + PARAM_CONSUMER_SECRET + " parameter defined");
    }

    @Override
    public Pair<OAuthParameters, OAuthSecrets> getAccessToken() {
        Pair<String, String> requestToken = getTokenInternal(authUrl + "/request_token", null, null);
        System.out.println("requestToken = " + requestToken);

        Pair<String, String> accessToken = getTokenInternal(authUrl + "/access_token", requestToken.getLeft(), requestToken.getRight());
        System.out.println("accessToken = " + accessToken);

        return getParamsAndSecrets(accessToken.getLeft(), accessToken.getRight());
    }

    private Pair<String, String> getTokenInternal(String url, String token, String secret) {
        Pair<OAuthParameters, OAuthSecrets> pair = getParamsAndSecrets(token, secret);

        ApacheHttpClient4 client = ApacheHttpClient4.create();
        client.addFilter(new OAuthClientFilter(client.getProviders(), pair.getLeft(), pair.getRight()));
        WebResource.Builder builder = client.resource(url).getRequestBuilder();

        ClientResponse cr = builder.method("GET", ClientResponse.class);
        if (cr.getStatus() != 200)
            cr = builder.method("GET", ClientResponse.class);

        String data = cr.getEntity(String.class);
        Map<String, String> map = parseResponse(data);
        String oauth_token = map.get("oauth_token");
        String oauth_token_secret = map.get("oauth_token_secret");
        return Pair.of(oauth_token, oauth_token_secret);
    }

    private Pair<OAuthParameters, OAuthSecrets> getParamsAndSecrets(String token, String secret) {
        OAuthParameters params = new OAuthParameters()
            .consumerKey(consumerKey)
            .signatureMethod("HMAC-SHA1")
            .token(token)
            .version("1.0");

        OAuthSecrets secrets = new OAuthSecrets()
            .consumerSecret(consumerSecret)
            .tokenSecret(secret);

        return Pair.of(params, secrets);
    }

    private Map<String, String> parseResponse(String stringEntity) {
        return Arrays.stream(stringEntity.split("&"))
            .map(s -> s.split("=", 2))
            .collect(Collectors.toMap(a -> a[0], a -> a.length > 1 ? a[1] : ""));
    }

//    public static void main(String[] args) {
//        SundogOAuth1Manager manager = new SundogOAuth1Manager(new ConductorConfig());
//        Pair<OAuthParameters, OAuthSecrets> pair = manager.getAccessToken();
//        ApacheHttpClient4 client = ApacheHttpClient4.create();
//        client.addFilter(new OAuthClientFilter(client.getProviders(), pair.getLeft(), pair.getRight()));
//        WebResource.Builder builder = client.resource("https://cadmium.sundogmediatoolkit.com/v1/assets").getRequestBuilder();
//        ClientResponse cr = builder.method("GET", ClientResponse.class);
//        System.out.println("status = " + cr.getStatus());
//        String assets = cr.getEntity(String.class);
//        System.out.println("assets = " + assets);
//    }
}

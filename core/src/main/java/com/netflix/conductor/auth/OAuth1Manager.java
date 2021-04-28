package com.netflix.conductor.auth;

import com.sun.jersey.oauth.signature.OAuthParameters;
import com.sun.jersey.oauth.signature.OAuthSecrets;
import org.apache.commons.lang3.tuple.Pair;

public interface OAuth1Manager {
    public Pair<OAuthParameters, OAuthSecrets> getAccessToken();
}

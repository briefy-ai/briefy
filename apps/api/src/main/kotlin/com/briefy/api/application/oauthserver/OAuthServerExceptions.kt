package com.briefy.api.application.oauthserver

class OAuthClientNotFoundException(clientId: String) :
    RuntimeException("OAuth client not found: $clientId")

class OAuthInvalidRedirectUriException(redirectUri: String) :
    RuntimeException("Redirect URI not allowed: $redirectUri")

class OAuthInvalidScopeException(scope: String) :
    RuntimeException("Scope not allowed: $scope")

class OAuthInvalidGrantException(message: String = "Invalid or expired authorization code") :
    RuntimeException(message)

class OAuthInvalidTokenException(message: String = "Invalid or expired token") :
    RuntimeException(message)

class OAuthUnsupportedGrantTypeException(grantType: String) :
    RuntimeException("Unsupported grant_type: $grantType")

class OAuthPkceRequiredException :
    RuntimeException("PKCE is required for this client")

class OAuthPkceVerificationException :
    RuntimeException("PKCE code_verifier does not match code_challenge")

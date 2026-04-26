package com.briefy.api.api.oauth

import com.briefy.api.application.oauthserver.OAuthClientNotFoundException
import com.briefy.api.application.oauthserver.OAuthInvalidRedirectUriException
import com.briefy.api.application.oauthserver.OAuthServerService
import com.briefy.api.infrastructure.security.AuthenticatedUser
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/oauth/authorize")
class OAuthAuthorizeController(
    private val oauthServerService: OAuthServerService,
    @param:Value("\${oauth.server.web-base-url:http://localhost:3000}") private val webBaseUrl: String
) {
    private val logger = LoggerFactory.getLogger(OAuthAuthorizeController::class.java)

    @GetMapping
    fun showConsent(
        @RequestParam("client_id") clientId: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("scope") scope: String,
        @RequestParam("response_type") responseType: String,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge") codeChallenge: String,
        @RequestParam("code_challenge_method") codeChallengeMethod: String,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        if (responseType != "code") {
            return errorPage("unsupported_response_type", "Only response_type=code is supported")
        }

        val principal = currentUser()
        if (principal == null) {
            val loginUrl = buildLoginUrl(request)
            return ResponseEntity.status(302).location(URI.create(loginUrl)).build()
        }

        val context = try {
            oauthServerService.validateAuthorizationRequest(clientId, redirectUri, scope, codeChallenge, codeChallengeMethod)
        } catch (e: OAuthClientNotFoundException) {
            return errorPage("invalid_client", "Unknown client")
        } catch (e: OAuthInvalidRedirectUriException) {
            return errorPage("invalid_request", "Redirect URI not allowed")
        } catch (e: Exception) {
            return errorPage("invalid_request", e.message ?: "Invalid request")
        }

        val html = buildConsentHtml(
            clientName = context.client.name,
            scopes = context.scopes,
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            state = state,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod
        )
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html)
    }

    @PostMapping
    fun handleConsent(
        @RequestParam("client_id") clientId: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("scope") scope: String,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge") codeChallenge: String,
        @RequestParam("code_challenge_method") codeChallengeMethod: String,
        @RequestParam("approved", defaultValue = "false") approved: Boolean
    ): ResponseEntity<*> {
        val principal = currentUser()
            ?: return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(webLoginUrl())).build<Void>()

        val context = try {
            oauthServerService.validateAuthorizationRequest(clientId, redirectUri, scope, codeChallenge, codeChallengeMethod)
        } catch (e: OAuthClientNotFoundException) {
            return errorPage("invalid_client", "Unknown client")
        } catch (e: OAuthInvalidRedirectUriException) {
            return errorPage("invalid_request", "Redirect URI not allowed")
        } catch (e: Exception) {
            val errorUri = buildRedirectUri(redirectUri, null, "invalid_request", e.message ?: "Invalid request", state)
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(errorUri)).build<Void>()
        }

        if (!approved) {
            val deniedUri = buildRedirectUri(context.redirectUri, null, "access_denied", "User denied access", state)
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(deniedUri)).build<Void>()
        }

        val code = oauthServerService.issueAuthorizationCode(
            clientId = clientId,
            userId = principal.id,
            redirectUri = context.redirectUri,
            scopes = context.scopes,
            codeChallenge = context.codeChallenge
        )

        logger.info("[oauth] Consent granted clientId={} userId={}", clientId, principal.id)
        val successUri = buildRedirectUri(context.redirectUri, code, null, null, state)
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(successUri)).build<Void>()
    }

    private fun currentUser(): AuthenticatedUser? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        return auth.principal as? AuthenticatedUser
    }

    private fun buildRedirectUri(
        base: String,
        code: String?,
        error: String?,
        errorDescription: String?,
        state: String?
    ): String {
        val params = mutableListOf<String>()
        if (code != null) params.add("code=${encode(code)}")
        if (error != null) params.add("error=${encode(error)}")
        if (errorDescription != null) params.add("error_description=${encode(errorDescription)}")
        if (state != null) params.add("state=${encode(state)}")
        val separator = if (base.contains("?")) "&" else "?"
        return if (params.isEmpty()) base else "$base$separator${params.joinToString("&")}"
    }

    private fun errorPage(error: String, description: String): ResponseEntity<String> {
        val safeDescription = escapeHtml(description)
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><title>Authorization Error</title>
            <style>body{font-family:system-ui,sans-serif;max-width:480px;margin:80px auto;padding:0 24px;color:#1a1a1a}
            .card{background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:32px}
            h1{font-size:1.25rem;margin:0 0 12px}p{color:#6b7280;margin:0}</style></head>
            <body><div class="card">
            <h1>Authorization Error</h1>
            <p>$safeDescription (${escapeHtml(error)})</p>
            </div></body></html>
        """.trimIndent()
        return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML).body(html)
    }

    private fun buildConsentHtml(
        clientName: String,
        scopes: List<String>,
        clientId: String,
        redirectUri: String,
        scope: String,
        state: String?,
        codeChallenge: String,
        codeChallengeMethod: String
    ): String {
        val scopeLabels = scopes.joinToString(", ") { scopeLabel(it) }
        val stateField = if (state != null) """<input type="hidden" name="state" value="${escapeHtml(state)}">""" else ""
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>Connect ${escapeHtml(clientName)} — Briefy</title>
              <style>
                *, *::before, *::after { box-sizing: border-box; }
                body { font-family: system-ui, -apple-system, sans-serif; background: #f9fafb;
                       display: flex; align-items: center; justify-content: center;
                       min-height: 100vh; margin: 0; padding: 24px; color: #111827; }
                .card { background: #fff; border: 1px solid #e5e7eb; border-radius: 16px;
                        padding: 40px 36px; max-width: 420px; width: 100%; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
                .logo { font-weight: 700; font-size: 1.1rem; color: #111827; margin-bottom: 28px; }
                h1 { font-size: 1.2rem; font-weight: 600; margin: 0 0 8px; }
                .subtitle { color: #6b7280; font-size: 0.9rem; margin: 0 0 24px; }
                .permissions { background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px;
                               padding: 16px; margin-bottom: 28px; }
                .permissions p { font-size: 0.85rem; color: #6b7280; margin: 0 0 8px; font-weight: 500;
                                 text-transform: uppercase; letter-spacing: .04em; }
                .permissions ul { margin: 0; padding: 0 0 0 20px; }
                .permissions li { font-size: 0.95rem; color: #374151; line-height: 1.6; }
                .actions { display: flex; gap: 12px; }
                button { flex: 1; padding: 10px 16px; border-radius: 8px; font-size: 0.95rem;
                         font-weight: 500; cursor: pointer; border: none; transition: opacity .15s; }
                button:hover { opacity: .88; }
                .btn-approve { background: #111827; color: #fff; }
                .btn-deny { background: #f3f4f6; color: #374151; border: 1px solid #e5e7eb; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="logo">Briefy</div>
                <h1>${escapeHtml(clientName)} wants access</h1>
                <p class="subtitle">This will allow ${escapeHtml(clientName)} to access your Briefy account.</p>
                <div class="permissions">
                  <p>Permissions requested</p>
                  <ul><li>$scopeLabels</li></ul>
                </div>
                <form method="POST" action="/oauth/authorize">
                  <input type="hidden" name="client_id" value="${escapeHtml(clientId)}">
                  <input type="hidden" name="redirect_uri" value="${escapeHtml(redirectUri)}">
                  <input type="hidden" name="scope" value="${escapeHtml(scope)}">
                  <input type="hidden" name="code_challenge" value="${escapeHtml(codeChallenge)}">
                  <input type="hidden" name="code_challenge_method" value="${escapeHtml(codeChallengeMethod)}">
                  $stateField
                  <div class="actions">
                    <button type="submit" name="approved" value="true" class="btn-approve">Authorize</button>
                    <button type="submit" name="approved" value="false" class="btn-deny">Deny</button>
                  </div>
                </form>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun scopeLabel(scope: String): String = when (scope) {
        "mcp:read" -> "Read your Sources, Briefings, Takeaways, and Topics"
        else -> escapeHtml(scope)
    }

    private fun buildLoginUrl(request: HttpServletRequest): String {
        val query = request.queryString?.let { "?$it" } ?: ""
        val authorizeUrl = "${request.requestURI}$query"
        return "${webLoginUrl()}?next=${encode(authorizeUrl)}"
    }

    private fun webLoginUrl(): String = "${webBaseUrl.trimEnd('/')}/login"

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
}

package com.simbridge.core.crypto

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

/** A device's long-lived TLS identity. On Android the private key never leaves the Keystore. */
interface TlsIdentity {
    val certificate: X509Certificate
    val privateKey: PrivateKey
    val fingerprint: String get() = Fingerprints.of(certificate)
}

class IdentityKeyManager(private val identity: TlsIdentity) : X509ExtendedKeyManager() {
    private fun matches(keyType: String?) = keyType == null || keyType.startsWith(identity.privateKey.algorithm) ||
        (identity.privateKey.algorithm == "EC" && keyType.contains("EC"))

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) =
        if (matches(keyType)) arrayOf(ALIAS) else null

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) =
        if (keyType == null || keyType.any(::matches)) ALIAS else null

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) =
        if (matches(keyType)) arrayOf(ALIAS) else null

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) =
        if (matches(keyType)) ALIAS else null

    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) =
        chooseClientAlias(keyType, issuers, null)

    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?) =
        chooseServerAlias(keyType, issuers, null)

    override fun getCertificateChain(alias: String?) = if (alias == ALIAS) arrayOf(identity.certificate) else null

    override fun getPrivateKey(alias: String?) = if (alias == ALIAS) identity.privateKey else null

    private companion object {
        const val ALIAS = "simbridge"
    }
}

/**
 * Trusts exactly the certificates whose SHA-256 fingerprint is in [allowed]; there is no CA.
 * With [allowAnyPeer] the handshake succeeds for any certificate and authorization is left to the
 * application layer (the Host uses this so unpaired Clients can present a pairing token).
 */
class PinnedTrustManager(
    private val allowAnyPeer: Boolean = false,
    private val allowed: () -> Set<String> = { emptySet() },
) : X509ExtendedTrustManager() {

    private fun check(chain: Array<out X509Certificate>?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Peer presented no certificate")
        if (allowAnyPeer) return
        val fp = Fingerprints.of(leaf)
        if (allowed().none { Fingerprints.matches(it, fp) }) {
            throw CertificateException("Peer certificate is not pinned")
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = check(chain)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = check(chain)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = check(chain)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = check(chain)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = check(chain)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = check(chain)
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

object TlsContexts {
    const val PROTOCOL = "TLSv1.3"

    fun create(identity: TlsIdentity, trustManager: PinnedTrustManager): SSLContext =
        SSLContext.getInstance("TLS").apply {
            init(arrayOf(IdentityKeyManager(identity)), arrayOf(trustManager), SecureRandom())
        }
}

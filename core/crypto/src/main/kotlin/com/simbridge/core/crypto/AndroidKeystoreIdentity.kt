package com.simbridge.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.security.auth.x500.X500Principal

private class KeystoreTlsIdentity(
    override val certificate: X509Certificate,
    override val privateKey: PrivateKey,
) : TlsIdentity

/**
 * Per-device EC P-256 identity whose private key is generated inside and never exported from
 * the Android Keystore. [rotate] replaces the key, which invalidates every existing pairing.
 */
class AndroidKeystoreIdentityStore(
    private val alias: String,
    private val commonName: String,
) {
    @Synchronized
    fun getOrCreate(): TlsIdentity {
        val keyStore = load()
        if (!keyStore.containsAlias(alias)) generate()
        val entry = load().getEntry(alias, null) as KeyStore.PrivateKeyEntry
        return KeystoreTlsIdentity(entry.certificate as X509Certificate, entry.privateKey)
    }

    @Synchronized
    fun rotate(): TlsIdentity {
        delete()
        return getOrCreate()
    }

    @Synchronized
    fun delete() {
        val keyStore = load()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun load(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun generate() {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            // TLS stacks sign pre-hashed data (NONE) as well as SHA-2 digests.
            .setDigests(
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512,
            )
            .setCertificateSubject(X500Principal("CN=$commonName"))
            .setCertificateSerialNumber(BigInteger(63, SecureRandom()).add(BigInteger.ONE))
            .setCertificateNotBefore(Date(now - DAY_MS))
            .setCertificateNotAfter(Date(now + 20 * 365 * DAY_MS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

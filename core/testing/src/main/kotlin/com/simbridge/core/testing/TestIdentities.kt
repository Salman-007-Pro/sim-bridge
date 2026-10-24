package com.simbridge.core.testing

import com.simbridge.core.crypto.TlsIdentity
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/** Software EC P-256 identities for JVM tests, mirroring what the Android Keystore generates on device. */
object TestIdentities {
    fun create(commonName: String): TlsIdentity {
        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=$commonName")
        val holder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(63, SecureRandom()).add(BigInteger.ONE),
            Date(now - 60_000),
            Date(now + 365L * 24 * 60 * 60 * 1000),
            subject,
            keyPair.public,
        ).build(JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)
        return object : TlsIdentity {
            override val certificate: X509Certificate = cert
            override val privateKey: PrivateKey = keyPair.private
        }
    }
}

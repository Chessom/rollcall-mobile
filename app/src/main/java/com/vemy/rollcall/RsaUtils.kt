package com.vemy.rollcall

import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import javax.crypto.Cipher
import org.bouncycastle.util.encoders.Hex

fun rsaEncrypt(passwd: String, eHex: String, nHex: String): String {
    val n = BigInteger(nHex, 16)
    val e = BigInteger(eHex, 16)

    val publicKeySpec = java.security.spec.RSAPublicKeySpec(n, e)
    val keyFactory = KeyFactory.getInstance("RSA")
    val publicKey: PublicKey = keyFactory.generatePublic(publicKeySpec)

    val cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)

    val bytes = passwd.toByteArray()
    val encryptedBytes = cipher.doFinal(bytes)
    return Hex.toHexString(encryptedBytes)
}

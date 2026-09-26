package com.invictus.xcode.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.invictus.xcode.core.git.GitCredential
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Token vault (plan section 7): AES/GCM master key in Android Keystore (alias
 * `app_git_credential_master_key`), encrypted JSON `git_credentials.enc` in
 * filesDir. Record key = normalized lowercase host. Tokens never hit logs or
 * plain storage. A corrupt/undecryptable file degrades to "no credentials"
 * rather than crashing the app.
 */
class GitCredentialStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)

    @Synchronized
    fun get(host: String): GitCredential? =
        readAll()[host.lowercase()]?.let { GitCredential(host.lowercase(), it.first, it.second) }

    @Synchronized
    fun put(credential: GitCredential) {
        val all = readAll().toMutableMap()
        all[credential.host.lowercase()] = credential.username to credential.token
        writeAll(all)
    }

    @Synchronized
    fun remove(host: String) {
        val all = readAll().toMutableMap()
        if (all.remove(host.lowercase()) != null) writeAll(all)
    }

    @Synchronized
    fun hosts(): List<String> = readAll().keys.sorted()

    private fun readAll(): Map<String, Pair<String, String>> =
        runCatching { readAllUnsafe() }.getOrDefault(emptyMap())

    private fun readAllUnsafe(): Map<String, Pair<String, String>> {
        if (!file.exists() || file.length() == 0L) return emptyMap()
        val blob = file.readBytes()
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = String(
            cipher.doFinal(blob.copyOfRange(GCM_IV_BYTES, blob.size)),
            Charsets.UTF_8,
        )
        val json = JSONObject(plain)
        val out = LinkedHashMap<String, Pair<String, String>>()
        for (key in json.keys()) {
            val rec = json.getJSONObject(key)
            out[key] = rec.getString("username") to rec.getString("token")
        }
        return out
    }

    private fun writeAll(all: Map<String, Pair<String, String>>) {
        val json = JSONObject()
        all.forEach { (host, cred) ->
            json.put(host, JSONObject().put("username", cred.first).put("token", cred.second))
        }
        val iv = ByteArray(GCM_IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(iv + encrypted)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val ALIAS = "app_git_credential_master_key"
        private const val FILE_NAME = "git_credentials.enc"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}

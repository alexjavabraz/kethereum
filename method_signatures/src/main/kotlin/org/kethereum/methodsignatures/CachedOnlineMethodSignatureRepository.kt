package org.kethereum.methodsignatures

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.kethereum.methodsignatures.model.TextMethodSignature
import org.kethereum.model.Transaction
import org.walleth.khex.toNoPrefixHexString
import java.io.File
import java.io.IOException

interface CachedOnlineMethodSignatureRepository {
    fun getSignaturesFor(tx: Transaction): Iterable<TextMethodSignature>
}

class CachedOnlineMethodSignatureRepositoryImpl(
        private val okHttpClient: OkHttpClient,
        storeDir: File,
        private val baseURL: String = "https://raw.githubusercontent.com/ethereum-lists/4bytes/master/signatures/"
) : CachedOnlineMethodSignatureRepository {

    private val signatureStore = FileBackedMethodSignatureStore(storeDir)


    override fun getSignaturesFor(tx: Transaction): Iterable<TextMethodSignature> {
        if (tx.input.size<4) {
            return emptyList()
        }

        val hex = tx.input.slice(0 until 4).toNoPrefixHexString()
        return try {
            signatureStore.get(hex)
        } catch (exception: IOException) {
            fetchAndStore(hex)
        }
    }

    private fun fetchAndStore(hex: String): Iterable<TextMethodSignature> {
        val signatures = ArrayList<TextMethodSignature>()
        val cleanHex = hex.replace("0x", "")
        val url = Request.Builder().url("$baseURL$cleanHex").build()
        val newCall: Call = okHttpClient.newCall(url)

        try {
            val executedCall = newCall.execute()
            if (executedCall.code() == 200) {
                val resultString = executedCall.body().use { it?.string() }
                resultString?.split(";")?.forEach {
                    signatures.add(TextMethodSignature(it))
                    signatureStore.upsert(hex, it)
                }
            }

        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }

        return signatures
    }

}
package com.gos.speed.firebase

import android.util.Base64
import com.google.firebase.database.*
import com.gos.speed.uwb.UwbControllerParams
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseSessionManager {

    private val sessionsRef: DatabaseReference
        get() = FirebaseDatabase.getInstance().getReference("uwb_sessions")

    fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[(Math.random() * chars.length).toInt()] }.joinToString("")
    }

    fun hostSession(code: String, params: UwbControllerParams): Flow<ByteArray> = callbackFlow {
        val sessionRef = sessionsRef.child(code)
        val data = mapOf(
            "host_address" to Base64.encodeToString(params.localAddress, Base64.NO_WRAP),
            "channel" to params.channel,
            "preamble_index" to params.preambleIndex,
            "session_id" to params.sessionId,
            "session_key" to Base64.encodeToString(params.sessionKey, Base64.NO_WRAP),
            "created_at" to ServerValue.TIMESTAMP
        )
        sessionRef.setValue(data).addOnFailureListener { e -> close(e) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val peerAddr = snapshot.child("peer_address").getValue(String::class.java)
                if (peerAddr != null) {
                    trySend(Base64.decode(peerAddr, Base64.NO_WRAP))
                }
            }
            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        sessionRef.addValueEventListener(listener)

        awaitClose {
            sessionRef.removeEventListener(listener)
            sessionRef.removeValue()
        }
    }

    suspend fun joinSession(code: String, myAddress: ByteArray): Result<UwbControllerParams> =
        suspendCancellableCoroutine { cont ->
            sessionsRef.child(code).get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        cont.resume(Result.failure(Exception("Código \"$code\" não encontrado. Verifique se o Host está ativo.")))
                        return@addOnSuccessListener
                    }
                    try {
                        val hostAddr = Base64.decode(
                            snapshot.child("host_address").getValue(String::class.java)
                                ?: error("host_address ausente"), Base64.NO_WRAP
                        )
                        val channel = (snapshot.child("channel").getValue(Long::class.java) ?: 0L).toInt()
                        val preamble = (snapshot.child("preamble_index").getValue(Long::class.java) ?: 0L).toInt()
                        val sessionId = (snapshot.child("session_id").getValue(Long::class.java) ?: 0L).toInt()
                        val sessionKey = Base64.decode(
                            snapshot.child("session_key").getValue(String::class.java)
                                ?: error("session_key ausente"), Base64.NO_WRAP
                        )
                        sessionsRef.child(code).child("peer_address")
                            .setValue(Base64.encodeToString(myAddress, Base64.NO_WRAP))
                        cont.resume(Result.success(UwbControllerParams(hostAddr, channel, preamble, sessionId, sessionKey)))
                    } catch (e: Exception) {
                        cont.resume(Result.failure(e))
                    }
                }
                .addOnFailureListener { e ->
                    cont.resume(Result.failure(
                        Exception("Erro ao acessar Firebase. Verifique a internet e a configuração.")
                    ))
                }
        }

    // Resume: host updates its address + channel (they can change), clears peer_address,
    // then waits for the guest to write their new address.
    fun resumeHostSession(
        code: String,
        newAddress: ByteArray,
        channel: Int,
        preambleIndex: Int,
        sessionId: Int,
        sessionKey: ByteArray
    ): Flow<ByteArray> = callbackFlow {
        val sessionRef = sessionsRef.child(code)

        sessionRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                close(Exception("Sessão \"$code\" não encontrada no Firebase. Ela pode ter sido encerrada pelo outro dispositivo."))
                return@addOnSuccessListener
            }
            val updates = mapOf(
                "host_address" to Base64.encodeToString(newAddress, Base64.NO_WRAP),
                "channel" to channel,
                "preamble_index" to preambleIndex,
                "session_id" to sessionId,
                "session_key" to Base64.encodeToString(sessionKey, Base64.NO_WRAP),
                "peer_address" to null  // reset so guest knows to reconnect
            )
            sessionRef.updateChildren(updates).addOnFailureListener { e -> close(e) }
        }.addOnFailureListener { e -> close(e) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val peerAddr = snapshot.child("peer_address").getValue(String::class.java)
                if (peerAddr != null) trySend(Base64.decode(peerAddr, Base64.NO_WRAP))
            }
            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        sessionRef.addValueEventListener(listener)
        awaitClose { sessionRef.removeEventListener(listener) }
    }

    // Resume: guest reads fresh host params from Firebase, writes its new address.
    suspend fun resumeGuestSession(code: String, myNewAddress: ByteArray): Result<UwbControllerParams> =
        suspendCancellableCoroutine { cont ->
            sessionsRef.child(code).get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        cont.resume(Result.failure(Exception("Sessão \"$code\" não encontrada. O Host pode ter encerrado a sessão.")))
                        return@addOnSuccessListener
                    }
                    try {
                        val hostAddr = Base64.decode(
                            snapshot.child("host_address").getValue(String::class.java)
                                ?: error("Host ainda não atualizou o endereço. Peça ao Host para reabrir o app primeiro."),
                            Base64.NO_WRAP
                        )
                        val channel = (snapshot.child("channel").getValue(Long::class.java) ?: 0L).toInt()
                        val preamble = (snapshot.child("preamble_index").getValue(Long::class.java) ?: 0L).toInt()
                        val sessionId = (snapshot.child("session_id").getValue(Long::class.java) ?: 0L).toInt()
                        val sessionKey = Base64.decode(
                            snapshot.child("session_key").getValue(String::class.java)
                                ?: error("session_key ausente"), Base64.NO_WRAP
                        )
                        sessionsRef.child(code).child("peer_address")
                            .setValue(Base64.encodeToString(myNewAddress, Base64.NO_WRAP))
                        cont.resume(Result.success(UwbControllerParams(hostAddr, channel, preamble, sessionId, sessionKey)))
                    } catch (e: Exception) {
                        cont.resume(Result.failure(e))
                    }
                }
                .addOnFailureListener {
                    cont.resume(Result.failure(Exception("Erro ao acessar Firebase. Verifique a internet.")))
                }
        }

    fun cleanupSession(code: String) {
        if (code.isNotEmpty()) sessionsRef.child(code).removeValue()
    }
}

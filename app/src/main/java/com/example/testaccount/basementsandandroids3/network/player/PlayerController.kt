package com.bna.game.network.player

import android.util.Log
import com.example.testaccount.basementsandandroids3.network.json
import com.example.testaccount.basementsandandroids3.network.player.*
import io.socket.client.Socket
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withTimeout
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.suspendCoroutine

class PlayerController(private val socket: Socket, private val callback: (PlayerGameStateChange) -> Unit) {
    init {
        configSocketEvents()
    }

    var isTurn: Boolean = false
        private set

    private fun configSocketEvents(): Unit = with(socket) {
        on(Socket.EVENT_CONNECT) {
            Log.d("asdf", "Connected")
        }
        on("PlayerConnectedChange") {
            val json = it[0] as JSONObject
            Log.d("asdf", "Player Connected, ID: ${json.getInt("id")}")
            callback(OtherPlayerConnectionChange(true, json.getInt("id")))
        }
        on("PlayerDisconnectedChange") {
            val json = it[0] as JSONObject
            Log.d("asdf", "Player Disconnected, ID: ${json.getInt("id")}")
            callback(OtherPlayerConnectionChange(false, json.getInt("id")))
        }
        on("SocketID") {
            val json = it[0] as JSONObject
            Log.d("asdf", "My ID: ${json.getInt("id")}")
        }
        //custom events
        on("PlayerGameStateUpdate") {
            val json = it[0] as JSONObject
            callback(PlayerGameStateUpdate(json.getJSONObject("gameState")))
        }
        on("TurnChangeSelf") {
            isTurn = true
            val json = it[0] as JSONObject
            callback(TurnChangeSelf(json.getJSONObject("gameState")))
        }
        on("TurnChangeOther") {
            isTurn = false
            val json = it[0] as JSONObject
            callback(TurnChangeOther(json.getJSONObject("gameState"), json.getInt("x"), json.getInt("y")))
        }
        on("StartGame") {
            val json = it[0] as JSONObject
            callback(StartGame(json.getJSONObject("gameState")))
        }
        on("GameEnd") {
            val json = it[0] as JSONObject
            callback(PlayerGameEnd(json.getJSONObject("gameState")))
        }
    }

    suspend fun updateGameState(gameState: JSONObject) = suspendCoroutine<Boolean>{ continuation ->
        if (!isTurn) continuation.resumeWithException(IllegalStateException("Incorrect Turn"))
        launch(continuation.context) {
            withTimeout(15, TimeUnit.SECONDS) { continuation.resume(false) }
        }
        socket.once("Response") {
            val json = it[0] as JSONObject
            continuation.resume(json.getBoolean("changeApproved"))
        }
        socket.emit("UpdateGameState", json("gameState" to gameState.toString()))
    }

    suspend fun updatePosition(x: Int, y: Int)= suspendCoroutine<Boolean>{ continuation ->
        if (!isTurn) continuation.resumeWithException(IllegalStateException("Incorrect Turn"))
        launch(continuation.context) {
            withTimeout(15, TimeUnit.SECONDS) { continuation.resume(false) }
        }
        socket.once("Response") {
            val json = it[0] as JSONObject
            continuation.resume(json.getBoolean("changeApproved"))
        }
        socket.emit("UpdateSelfPosition", json("newX" to x, "newY" to y))
    }

    fun endTurn() {
        require(isTurn) { "Incorrect Turn" }
        socket.emit("EndTurn")
    }
}
package com.yourstudy.app

import android.content.Context
import android.net.Uri
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PairPhase { UNPAIRED, ADVERTISING, SCANNING, HANDSHAKE_VERIFYING, PAIRED_LOCAL }

data class PairUi(
    val phase: PairPhase = PairPhase.UNPAIRED,
    val connected: Boolean = false,
    val qr: String? = null,
    val message: String = "",
    val peerName: String? = null,
)

/**
 * Serverless pairing + sync over Nearby Connections. Child advertises and shows a QR; parent scans it,
 * connects, and a one-time token handshake exchanges a shared secret. Focus/break timers never depend on this link.
 */
object Pairing {
    private const val SERVICE_ID = "com.yourstudy.app.nearby.pair"
    private lateinit var app: Context
    private val client get() = Nearby.getConnectionsClient(app)

    private val _ui = MutableStateFlow(PairUi())
    val ui: StateFlow<PairUi> = _ui.asStateFlow()

    private var endpoint: String? = null
    private var authed = false
    private var expectedEp: String? = null          // parent: endpoint name to look for
    private var parentTok: String? = null           // parent: token from QR (null = reconnect)
    private var parentSid: String? = null
    private var pairTok: String? = null             // child: one-time token (null = reconnect only)
    private var pairSid: String? = null
    private var pairExp = 0L

    fun init(ctx: Context) {
        app = ctx.applicationContext
        _ui.value = pairedUi("")
    }

    private fun pairedUi(msg: String, connected: Boolean = false) =
        if (Store.peerId != null) PairUi(PairPhase.PAIRED_LOCAL, connected, message = msg, peerName = Store.peerName)
        else PairUi(message = msg)

    private fun epName() = "YS-C:" + Store.deviceId.take(8)
    private fun stopAll() {
        runCatching { client.stopAdvertising(); client.stopDiscovery(); client.stopAllEndpoints() }
        endpoint = null; authed = false
    }

    fun cancel() { stopAll(); pairTok = null; parentTok = null; _ui.value = pairedUi("") }
    private fun fail(msg: String) { stopAll(); pairTok = null; parentTok = null; _ui.value = pairedUi(msg) }

    // ---------- child ----------
    fun startChildPairing() {
        stopAll()
        pairSid = UUID.randomUUID().toString()
        pairTok = (100000..999999).random().toString()
        pairExp = System.currentTimeMillis() + 5 * 60_000L
        val qr = "yourstudy://pair?sid=$pairSid&ep=${Uri.encode(epName())}&tok=$pairTok&exp=$pairExp"
        _ui.value = PairUi(PairPhase.ADVERTISING, qr = qr, message = "Ask your parent to scan this code (valid 5 min).")
        advertise()
    }

    fun startChildReconnect() {
        stopAll(); pairTok = null
        _ui.value = pairedUi("Waiting for parent to connect…")
        advertise()
    }

    private fun advertise() {
        client.startAdvertising(
            epName(), SERVICE_ID, connCb,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        ).addOnFailureListener { fail("Could not start advertising: ${it.message}") }
    }

    // ---------- parent ----------
    fun startParentScan(qr: String) {
        val u = runCatching { Uri.parse(qr) }.getOrNull()
        val ep = u?.getQueryParameter("ep"); val tok = u?.getQueryParameter("tok")
        val sid = u?.getQueryParameter("sid"); val exp = u?.getQueryParameter("exp")?.toLongOrNull()
        if (u?.scheme != "yourstudy" || ep == null || tok == null || sid == null || exp == null) {
            fail("That is not a Your Study pairing code."); return
        }
        if (System.currentTimeMillis() > exp) { fail("That code has expired. Generate a new one on the child phone."); return }
        stopAll(); expectedEp = ep; parentTok = tok; parentSid = sid
        _ui.value = PairUi(PairPhase.SCANNING, message = "Looking for the child phone…")
        discover()
    }

    fun startParentReconnect() {
        val id = Store.peerId ?: return
        stopAll(); expectedEp = "YS-C:" + id.take(8); parentTok = null
        _ui.value = pairedUi("Looking for the child phone…")
        discover()
    }

    private fun discover() {
        client.startDiscovery(
            SERVICE_ID, discCb, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        ).addOnFailureListener { fail("Could not start scanning: ${it.message}") }
    }

    private val discCb = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            if (info.endpointName != expectedEp) return
            client.stopDiscovery()
            client.requestConnection(Build.MODEL, id, connCb)
                .addOnFailureListener { fail("Could not connect: ${it.message}") }
        }
        override fun onEndpointLost(id: String) {}
    }

    private val connCb = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            if (Store.role == Role.PARENT && info.endpointName != expectedEp) { client.rejectConnection(id); return }
            endpoint = id
            if (_ui.value.phase != PairPhase.PAIRED_LOCAL)
                _ui.value = _ui.value.copy(phase = PairPhase.HANDSHAKE_VERIFYING, message = "Verifying…")
            client.acceptConnection(id, payCb)
        }

        override fun onConnectionResult(id: String, r: ConnectionResolution) {
            if (!r.status.isSuccess) { fail("Connection failed. Try again."); return }
            runCatching { client.stopAdvertising(); client.stopDiscovery() }
            if (Store.role == Role.PARENT) {
                if (parentTok != null) send(JSONObject().put("t", "PAIR_REQ").put("sid", parentSid)
                    .put("tok", parentTok).put("name", Build.MODEL).put("id", Store.deviceId))
                else send(JSONObject().put("t", "AUTH").put("secret", Store.secret).put("id", Store.deviceId))
            }
        }

        override fun onDisconnected(id: String) {
            endpoint = null; authed = false
            _ui.value = pairedUi("Offline. Active timers are unaffected.")
        }
    }

    private val payCb = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, p: Payload) {
            val bytes = p.asBytes() ?: return
            runCatching { handle(JSONObject(String(bytes))) }
        }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }

    private fun connectedUi(msg: String) {
        _ui.value = PairUi(PairPhase.PAIRED_LOCAL, true, message = msg, peerName = Store.peerName)
    }

    private fun handle(o: JSONObject) {
        when (o.getString("t")) {
            "PAIR_REQ" -> { // child
                val ok = pairTok != null && o.optString("tok") == pairTok &&
                    o.optString("sid") == pairSid && System.currentTimeMillis() < pairExp
                if (!ok) { send(JSONObject().put("t", "PAIR_FAIL")); fail("Pairing rejected."); return }
                val secret = UUID.randomUUID().toString()
                Store.peerId = o.getString("id"); Store.peerName = o.optString("name", "Parent"); Store.secret = secret
                pairTok = null; authed = true
                send(JSONObject().put("t", "PAIR_OK").put("secret", secret).put("id", Store.deviceId).put("name", Build.MODEL))
                connectedUi("Connected to ${Store.peerName}"); flush()
            }
            "PAIR_OK" -> { // parent
                Store.secret = o.getString("secret"); Store.peerId = o.getString("id")
                Store.peerName = o.optString("name", "Child"); parentTok = null; authed = true
                connectedUi("Connected to ${Store.peerName}")
            }
            "PAIR_FAIL" -> fail("Pairing rejected. The code may have expired.")
            "AUTH" -> { // child
                if (o.optString("secret") == Store.secret && o.optString("id") == Store.peerId) {
                    authed = true; send(JSONObject().put("t", "AUTH_OK")); connectedUi("Connected to ${Store.peerName}"); flush()
                } else { send(JSONObject().put("t", "AUTH_FAIL")); fail("Unknown device tried to connect.") }
            }
            "AUTH_OK" -> { authed = true; connectedUi("Connected to ${Store.peerName}") }
            "AUTH_FAIL" -> fail("This pairing is no longer valid. Unpair and pair again.")
            else -> if (authed) handleAuthed(o)
        }
    }

    private fun handleAuthed(o: JSONObject) {
        when (o.getString("t")) {
            "START_SESSION" -> { // child
                val m = o.optInt("minutes", Store.minutes).coerceIn(1, 240)
                Store.minutes = m
                if (Store.role == Role.CHILD && Session.ui.value.phase == Phase.IDLE &&
                    Perms.overlay(app) && Perms.accessibility(app)
                ) Session.start(app, m, Store.essentials)
            }
            "UPDATE_RULES" -> Store.minutes = o.optInt("minutes", Store.minutes).coerceIn(1, 240)
            "SESSION_LOG_SYNC" -> { // parent
                val a = o.getJSONArray("entries")
                Store.addPeerLogs((0 until a.length()).map { logFromJson(a.getJSONObject(it)) })
            }
            "UNPAIR" -> { Store.unpair(); stopAll(); _ui.value = PairUi(message = "The other phone unpaired.") }
        }
    }

    private fun send(o: JSONObject) {
        endpoint?.let { client.sendPayload(it, Payload.fromBytes(o.toString().toByteArray())) }
    }

    // ---------- commands & sync ----------
    fun sendStart(minutes: Int) { if (authed) send(JSONObject().put("t", "START_SESSION").put("minutes", minutes)) }
    fun sendRules(minutes: Int) { if (authed) send(JSONObject().put("t", "UPDATE_RULES").put("minutes", minutes)) }

    /** Called when a session ends on this phone. Queued locally; sent when the link is up. */
    fun onLog(e: LogEntry) {
        if (!::app.isInitialized || Store.role != Role.CHILD || Store.peerId == null) return
        Store.queueLog(e); flush()
    }

    private fun flush() {
        if (!authed || endpoint == null) return
        val pending = Store.pendingLogs()
        if (pending.isEmpty()) return
        val arr = JSONArray().also { a -> pending.forEach { a.put(it.toJson()) } }
        send(JSONObject().put("t", "SESSION_LOG_SYNC").put("entries", arr))
        Store.clearPending()
    }

    fun unpair() {
        if (authed) send(JSONObject().put("t", "UNPAIR"))
        stopAll(); Store.unpair(); pairTok = null; parentTok = null
        _ui.value = PairUi(message = "Unpaired.")
    }
}

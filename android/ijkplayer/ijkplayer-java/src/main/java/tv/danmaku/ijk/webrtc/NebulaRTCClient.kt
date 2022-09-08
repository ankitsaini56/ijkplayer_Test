package tv.danmaku.ijk.media.example.webrtc

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceServer
import org.webrtc.SessionDescription
import tv.danmaku.ijk.webrtc.AppRTCClient
import tv.danmaku.ijk.webrtc.AppRTCClient.*
import tv.danmaku.ijk.webrtc.NebulaInterface
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NebulaRTCClient : AppRTCClient {
    companion object {
        private var gStartWebRTCResponse = ""
        private var gLastResponseTimestamp = 0L
        private var gTtl = 0
    }

    private val TAG = "NebulaRTCClient"
    private val TIMEOUT_IN_MS = 30000
    private var mConnectionParameters: RoomConnectionParameters? = null
    private var mEvents: SignalingEvents? = null
    private var mOfferSdp: SessionDescription? = null
    private val mIceServers: ArrayList<IceServer> = ArrayList()
    private var mCandidate: IceCandidate? = null
    private var mIsGatheringCandidateDone = false
    private val mExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mIceGatheringTimerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var mIceGatheringStarted = false
    private var mIceGatheringTimerCount = 20
    private var mIceGatheringTimerAbort = false
    private var mAnswerSdp: SessionDescription? = null
    private var mOfferCandidates: ArrayList<IceCandidate> = ArrayList()
    private var mAnswerCandidates: ArrayList<IceCandidate> = ArrayList()
    private var mNebulaAPIs: NebulaInterface? = null
    private var mRtcId = 0
    private var mIceGatheringState: PeerConnection.IceGatheringState = PeerConnection.IceGatheringState.NEW;
    private var mChannelsResponse: JSONArray? = null
    private val DEBUG = true


    constructor(events: SignalingEvents, nebulaAPIs: NebulaInterface) {
        mEvents = events
        mNebulaAPIs = nebulaAPIs
    }

    private fun parseAnswerCandidate(sdp: String) {
        var lines = sdp.split("\r\n")
        var candidateList: ArrayList<String> = ArrayList()
        var sdpmid = ""
        for (line in lines) {
            if (line.startsWith("a=mid:")) {
                var sdpmid = line.split(":")[1]
                for (candidate in candidateList) {
                    //mOfferCandidates.add(IceCandidate(sdpmid, 0, candidate))
                    mEvents?.onRemoteIceCandidate(IceCandidate(sdpmid, 0, candidate))
                }
                candidateList.clear()
            } else if (line.startsWith("a=candidate")) {
                var candidates = line.split("=")
                candidateList.add(candidates[1])
            }
        }
    }

    override fun connectToRoom(connectionParameters: RoomConnectionParameters) {
        mConnectionParameters = connectionParameters
        mExecutor.execute {
            val ret = startClient()
            if (ret < 0) {
                mEvents?.onChannelError("start client failed")
            }
        }
    }

    val ICE_SERVERS = """{
      "lifetimeDuration": "86400s",
      "iceServers": [
        {
          "urls": [
            "stun:64.233.188.127:19302",
            "stun:[2404:6800:4008:c06::7f]:19302"
          ]
        },
        {
          "urls": [
            "turn:172.253.117.127:19305?transport=udp",
            "turn:[2607:f8b0:400e:c0a::7f]:19305?transport=udp",
            "turn:172.253.117.127:19305?transport=tcp",
            "turn:[2607:f8b0:400e:c0a::7f]:19305?transport=tcp"
          ],
          "username": "CJmukfQFEgaF6vEDwuIYzc/s6OMTIICjBQ",
          "credential": "XE+YlZDCoTHYxinn+yZhntLs3SM=",
          "maxRateKbps": "8000"
        }
      ],
      "blockStatus": "NOT_BLOCKED",
      "iceTransportPolicy": "all"
    }"""

    fun buildIceServer() {
        val json = JSONObject(ICE_SERVERS)
        val iceServers: JSONArray = json.getJSONArray("iceServers")
        for (i in 0 until iceServers.length()) {
            val server = iceServers.getJSONObject(i)
            val turnUrls = server.getJSONArray("urls")
            val username = if (server.has("username")) server.getString("username") else ""
            val credential = if (server.has("credential")) server.getString("credential") else ""
            var urls: MutableList<String> = mutableListOf()
            for (j in 0 until turnUrls.length()) {
                urls.add(turnUrls.getString(j))
                //val turnUrl = turnUrls.getString(j)
            }
            val turnServer = IceServer.builder(urls)
                    .setUsername(username)
                    .setPassword(credential)
                    .createIceServer()
            mIceServers.add(turnServer)
        }
    }

    private fun buildIceServer(json: JSONObject): Int {
        /**
         *  {
        "rtcId": <RTC_ID>,
        "username": "<USERNAME>",
        "password": "<PASSWORD>",
        "ttl": <TIME_TO_LIVE_SEC>,
        "uris": [
        "<TURN_URI>"
        ]
        }
         */
        Log.e(TAG, "json=$json")
        var urls: MutableList<String> = mutableListOf()
        val username = json.optString("username")
        val password = json.optString("password")
        val uris = json.optJSONArray("uris")
        if (uris == null || uris.length() == 0) {
            return -1
        }

        Log.e(TAG, "username=$username, password=$password")
        Log.e(TAG, "uris=$uris")
        Log.e(TAG, "length=${uris.length()}")

        for (i in 0 until uris.length()) {
            Log.e(TAG, "url=${uris.optString(i)}")
            urls.add(uris.optString(i))
        }
        val turnServer = IceServer.builder(urls)
                .setUsername(username)
                .setPassword(password)
                .createIceServer()
        mIceServers.add(turnServer)
        return 0
    }

    private fun createOffer(candidates: List<IceCandidate>?) {
        val parameters = SignalingParameters( // Ice servers are not needed for direct connections.
                mIceServers,
                true,  // Server side acts as the initiator on direct connections.
                null,  // clientId
                null,  // wssUrl
                null,  // wwsPostUrl
                null,  // offerSdp
                candidates // iceCandidates
        )
        mEvents?.onConnectedToRoom(parameters)
        mIsGatheringCandidateDone = false;
    }

    private fun logLongString(str: String) {
        if (str.length > 4000) {
            Log.v(TAG, "sb.length = " + str.length)
            val chunkCount: Int = str.length / 4000 // integer division
            for (i in 0..chunkCount) {
                val max = 4000 * (i + 1)
                if (max >= str.length) {
                    Log.v(TAG, "chunk " + i + " of " + chunkCount + ":" + str.substring(4000 * i))
                } else {
                    Log.v(TAG, "chunk " + i + " of " + chunkCount + ":" + str.substring(4000 * i, max))
                }
            }
        } else {
            Log.v(TAG, str)
        }
    }

    private fun genStartWebRtcExJson(): JSONObject {
        val json = JSONObject()
        val args = JSONObject()
        val channels = JSONArray()
        json.put("func", "startWebRtcEx")
        args.putOpt("amToken", mConnectionParameters?.nebulaParameters?.dmToken)
        args.putOpt("realm", mConnectionParameters?.nebulaParameters?.realm)
        args.put("disableAuthTurn", false)
        args.putOpt("info", mConnectionParameters?.nebulaParameters?.info)
        val channel = JSONObject()
        channel.putOpt("channelId", mConnectionParameters?.nebulaParameters?.channelId)
        channel.putOpt("streamType", mConnectionParameters?.nebulaParameters?.streamType)
        channel.put("autoPlay", true)
        channels.put(channel)

        args.put("channels", channels)
        json.put("args", args)
        return json
    }

    private fun genStartWebRtcJson(): JSONObject {
        val json = JSONObject()
        val args = JSONObject()
        val channels = JSONArray()
        json.put("func", "startWebRtc")
        args.put("amToken", mConnectionParameters?.nebulaParameters?.dmToken)
        args.put("realm", mConnectionParameters?.nebulaParameters?.realm)
        args.put("disableAuthTurn", false)
        args.put("info", mConnectionParameters?.nebulaParameters?.info)
        mChannelsResponse?.let {
            for (i in 0 until it.length()) {
                val channel = JSONObject()
                if(it.getJSONObject(i).has("channelId")) {
                    channel.put("channelId", it.getJSONObject(i).getInt("channelId"))
                }else { //playback won't response channel id
                    channel.put("channelId", mConnectionParameters?.nebulaParameters?.channelId)
                }
                val url = it.getJSONObject(i).optString("url")
                val streamIds = JSONArray()
                streamIds.put(url?.substring(url?.lastIndexOf("/") + 1))
                channel.put("streamId", streamIds)
                channels.put(channel)
            }
        } ?: run {
            Log.e(TAG, "no channels response from startLiveStreamEx")
        }
        args.put("channels", channels)
        json.put("args", args)
        return json
    }

    private fun genStartPlaybackJson(): JSONObject {
        val json = JSONObject()
        val args = JSONObject()
        val preferProtocol = JSONArray()

        json.put("func", "startPlayback");
        preferProtocol.put("webrtc")
        args.put("preferProtocol", preferProtocol)
        val channelId = mConnectionParameters?.nebulaParameters?.channelId
        val streamType = mConnectionParameters?.nebulaParameters?.streamType
        if(channelId != null) {
            args.putOpt("channel", channelId)
        }
        streamType?.let {
            args.putOpt("streamType", streamType)
        }?: run {
            args.putOpt("streamType", "audioAndVideo")
        }
        if(mConnectionParameters?.nebulaParameters?.playbackStartTime != null) {
            args.putOpt("startTime", mConnectionParameters?.nebulaParameters?.playbackStartTime)
        }
        if(mConnectionParameters?.nebulaParameters?.playbackFileName != null) {
            args.putOpt("fileName", mConnectionParameters?.nebulaParameters?.playbackFileName)
        }

        json.put("args", args)
        return json
    }

    private fun genStartLiveStreamExJson(): JSONObject {
        val json = JSONObject()
        val args = JSONObject()
        val preferProtocol = JSONArray()
        val channels = JSONArray()

        json.put("func", "startLiveStreamEx");
        preferProtocol.put("webrtc")
        args.put("preferProtocol", preferProtocol)
        val channelId = mConnectionParameters?.nebulaParameters?.channelId
        val streamType = mConnectionParameters?.nebulaParameters?.streamType
        val channel = JSONObject()
        if(channelId != null) {
            channel.putOpt("channelId", channelId)
        }
        streamType?.let {
            channel.putOpt("streamType", streamType)
        }?: run {
            channel.putOpt("streamType", "audioAndVideo")
        }
        channels.put(channel)
        args.put("channels", channels)
        json.put("args", args)
        return json
    }

    private fun genStopWebRtcJson(): JSONObject {
        val json = JSONObject()
        val args = JSONObject()
        json.put("func", "stopWebRtc")
        args.put("rtcId", mRtcId)
        json.put("args", args)
        return json
    }

    private fun genStartWebRtcStreams(): JSONObject {
        val json = JSONObject()
        val args = JSONObject()
        val streamIds = JSONArray()
        json.put("func", "startWebRtcStreams");
        args.put("rtcId", mRtcId)
        mChannelsResponse?.let {
            for (i in 0 until it.length()) {
                val channel = it.getJSONObject(i)
                val url = channel.optString("url")
                val streamId = url?.substring(url.lastIndexOf("/") + 1)
                streamIds.put(streamId)
            }
            args.put("streamIds", streamIds)
        }
        json.put("args", args)
        return json
    }

    private fun startClient(): Int {
        var response:String? = null

        if(mConnectionParameters?.nebulaParameters?.playbackStartTime != null ||
                mConnectionParameters?.nebulaParameters?.playbackFileName != null) {
            Log.e(TAG, "playback")
            //send startPlayback
            clientSend(genStartPlaybackJson().toString())?.let {
                val startPlaybackResp = JSONObject(it)
                Log.d(TAG, "${startPlaybackResp.toString()}")
                val statusCode = startPlaybackResp.getInt("statusCode")
                if (statusCode != 200) {
                    Log.e(TAG, "failed to startPlayback: code=$statusCode, msg=${startPlaybackResp.getString("statusMsg")}")
                    return -1
                } else {
                    val jContent = startPlaybackResp.getJSONObject("content")
                    mChannelsResponse = JSONArray()
                    mChannelsResponse?.put(jContent)
                    Log.d(TAG, "channel response=${mChannelsResponse.toString()}")
                }
            } ?: run {
                Log.e(TAG, "failed to startPlayback")
                return -1
            }
        }else {
            //send startLiveStreamEx
            if(mConnectionParameters?.nebulaParameters?.isQuickConnect != true) {
                clientSend(genStartLiveStreamExJson().toString())?.let {
                    val startLiveStreamExResp = JSONObject(it)
                    Log.d(TAG, "${startLiveStreamExResp.toString()}")
                    val statusCode = startLiveStreamExResp.getInt("statusCode")
                    if (statusCode != 200) {
                        Log.e(TAG, "failed to startLiveStreamEx: code=$statusCode, msg=${startLiveStreamExResp.getString("statusMsg")}")
                        return -1
                    } else {
                        mChannelsResponse = startLiveStreamExResp.getJSONObject("content").optJSONArray("channels")
                        Log.d(TAG, "channel response=${mChannelsResponse.toString()}")
                    }
                } ?: run {
                    Log.e(TAG, "failed to startLiveStreamEx")
                    return -1
                }
            }
        }

        var useTurnInfoCache = false
        //send startWebRtc
        if(mConnectionParameters?.nebulaParameters?.isQuickConnect != true) {
            if (mConnectionParameters?.nebulaParameters?.dmToken != null) {
                response = clientSend(genStartWebRtcJson().toString()) ?: return -1
                Log.d(TAG, "start webrtc response=$response")
                var startResJson = JSONObject(response)
                if (startResJson.optInt("statusCode") != 200) {
                    Log.e(TAG, "startWebRTC failed")
                    return -1
                } else {
                    val content = startResJson.optJSONObject("content")
                    mRtcId = content.optInt("rtcId")
                    if (DEBUG) {
                        Log.e(TAG, "startWebRTC success rtcid: $mRtcId")
                    }
                    val ret = buildIceServer(content)
                    if (ret < 0) {
                        return -1
                    }
                }
            }
        }else {
            if (mConnectionParameters?.nebulaParameters?.dmToken != null) {
                val ttl = gTtl / 2
                if (System.currentTimeMillis() - gLastResponseTimestamp < ttl * 1000) {
                    useTurnInfoCache = true
                    var json = JSONObject(gStartWebRTCResponse)
                    val content = json.optJSONObject("content")
                    buildIceServer(content)
                    createOffer(null)
                }

                response = clientSend(genStartWebRtcExJson().toString()) ?: return -1
                Log.d(TAG, "startwebrtcex response=$response")
                var startResJson = JSONObject(response)
                if (startResJson.optInt("statusCode") != 200) {
                    Log.e(TAG, "startWebRTC failed")
                    return -1
                } else {
                    val content = startResJson.optJSONObject("content")
                    mRtcId = content.optInt("rtcId")
                    if (DEBUG) {
                        Log.e(TAG, "startWebRTC success rtcid: $mRtcId")
                    }
                    if (!useTurnInfoCache) {
                        buildIceServer(content)
                    }
                    mChannelsResponse = content.optJSONArray("channels")
                    if (System.currentTimeMillis() - gLastResponseTimestamp >= ttl * 1000) {
                        gStartWebRTCResponse = response
                        gTtl = content.optInt("ttl")
                        gLastResponseTimestamp = System.currentTimeMillis()
                    }
                }
            }
        }
        //send exchangeSdp
        if (!useTurnInfoCache) {
            createOffer(null)
        }
        return 0
    }

    override fun sendOfferSdp(sdp: SessionDescription?) {
        mOfferSdp = sdp
        var response:String? = null
        Executors.newSingleThreadExecutor().execute {
            while(!mIsGatheringCandidateDone || mRtcId == 0) {
                sleep(20)
            }
            val json = buildOfferSDPResponseJson(mOfferSdp)
            response = clientSend(json.toString())
            if (DEBUG) {
                Log.e(TAG, "response = $response")
            }
            if (response != null) {
                val resJson = JSONObject(response)
                if (resJson.optInt("statusCode") != 200) {
                    clientSend(genStopWebRtcJson().toString())
                    return@execute
                }
                var content = resJson.optJSONObject("content")
                if (content != null) {
                    val type = content.optString("type")
                    val sdp = content.optString("sdp").trim()
                    if (DEBUG) {
                        logLongString("answer = $sdp")
                    }
                    val s = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type), "$sdp\r\n")
                    mEvents?.onRemoteDescription(s)
                    parseAnswerCandidate(s.description)
                }
            }
            if(mConnectionParameters?.nebulaParameters?.isQuickConnect == false ||
                mConnectionParameters?.nebulaParameters?.isQuickConnect == null) {
                //send startWebRtcStreams
                response = clientSend(genStartWebRtcStreams().toString())
                Log.d(TAG, "after startWebRtcStreams: ${response.toString()}")
            }
        }
    }

    private fun buildOfferSDPResponseJson(sdp: SessionDescription?): JSONObject {
        val json = JSONObject()
        val offerObj = JSONObject()
        json.put("func", "exchangeSdp")
        var offer = sdp?.description
        if (offer == null) {
            offerObj.put("type", "offer")
            offerObj.put("sdp", offer)
            if (mRtcId != 0) {
                offerObj.put("rtcId", mRtcId)
            }
            json.put("args", offerObj)
            return json
        }
//        val removeStr = "a=ice-options:trickle renomination"
//        var preIdx = offer.indexOf(removeStr)
//        Log.d(TAG, "preIdx = $preIdx")
//        while (preIdx != -1) {
//            offer = offer?.removeRange(preIdx, preIdx + removeStr.length + 2)
//            if (offer != null)
//                preIdx = offer.indexOf(removeStr)
//        }
        var candidates = ""
        if(!mOfferCandidates.isEmpty()) {
            for(candi in mOfferCandidates) {
                candidates += "a=" + candi.sdp + "\r\n"
            }
        }
        if (offer != null) {
            var preidx = offer.indexOf("m=")
            var postidx = offer.indexOf("\r\n", preidx) + 2
            offer = offer.substring(0, postidx) + candidates + offer.substring(postidx)
            preidx = offer.indexOf("m=", postidx)
            postidx = offer.indexOf("\r\n", preidx) + 2
            offer = offer.substring(0, postidx) + candidates + offer.substring(postidx)
            if (DEBUG) {
                Log.e(TAG, "offer = $offer")
            }
            offerObj.put("type", "offer")
            offerObj.put("sdp", offer)
            if (mRtcId != 0) {
                offerObj.put("rtcId", mRtcId)
            }
            json.put("args", offerObj)
        }
        return json
    }

    private fun clientSend(reqJson: String): String? {
        val response = arrayOfNulls<String>(1)
        if (DEBUG) {
            logLongString("send $reqJson")
        }
        val ret = mNebulaAPIs?.Send_Command(reqJson, response, TIMEOUT_IN_MS)
        if (DEBUG) {
            Log.d(TAG, "send ret = $ret")
        }
        return response[0]
    }

    override fun sendAnswerSdp(sdp: SessionDescription?) {
        mAnswerSdp = sdp
        if (DEBUG) {
            Log.e(TAG, "answer sdp: ${mAnswerSdp?.description}")
        }
    }

    override fun sendIceGatheringState(newState: PeerConnection.IceGatheringState?) {
        newState?.let {
            Log.e(TAG, "ice gathering state change to $newState")
            mIceGatheringState = newState
        }
    }

    override fun sendLocalIceCandidate(candidate: IceCandidate?) {
        if (candidate != null) {
            Log.d(TAG, "grab candidate $candidate")
            if(mIsGatheringCandidateDone) {
                Log.i(TAG, "already gathering done")
            }
            if (!isLocalNetwork(candidate)) {
                mOfferCandidates.add(candidate)
            }
            if (candidate.sdp.contains("typ relay")) {
                mIsGatheringCandidateDone = true
            }
        }
        else {
            Log.d(TAG, "grab candidate done")
            mIsGatheringCandidateDone = true
        }
    }

    private fun isLocalNetwork(candidate: IceCandidate): Boolean {
        val items = candidate.sdp.split(" ")
        if (items.size < 5) {
            return false
        }
        val ip = items[4]
        val c = countOccurrences(ip, ':')

        //
        // <INFO> filter out lo & dummy network interfaces
        //
        return ip.equals("127.0.0.1") || c in 1..2
    }

    private fun countOccurrences(s: String, ch: Char): Int {
        return s.filter { it == ch }.count()
    }

    override fun sendLocalIceCandidateRemovals(candidates: Array<IceCandidate?>?) {
        Log.d(TAG, "sendLocalIceCandidateRemovals")
    }

    override fun disconnectFromRoom() {
        Log.e(TAG, "disconnectFromRoom")
        var stopResponse: String? = clientSend(genStopWebRtcJson().toString())
        if (stopResponse != null) {
            var stopResJson = JSONObject(stopResponse)
            if (stopResJson.optInt("statusCode") != 200) {
                Log.e(TAG, "stopWebRTC failed")
            }
        }
    }
}

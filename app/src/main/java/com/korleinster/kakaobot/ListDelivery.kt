package com.korleinster.kakaobot

import android.app.job.*
import android.content.ComponentName
import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Optional, user-configured endpoint. Only reminder definitions and summary cross this boundary. */
object ListDelivery {
    private val io=Executors.newSingleThreadExecutor()
    private val busy=ConcurrentHashMap.newKeySet<String>()
    private val epochs=ConcurrentHashMap<String,Long>()
    fun cancel(room:BotEngine) {epochs.merge(room.roomId,1L,Long::plus)}
    private class Rejected(val code:Int,val body:JSONObject):Exception()
    private fun request(server:String,path:String,token:String="",body:JSONObject?=null,method:String=if(body==null) "GET" else "POST"):JSONObject {
        val c=URL(SyncEndpoint.normalize(server)+path).openConnection() as HttpURLConnection
        try {
            c.connectTimeout=8000;c.readTimeout=8000;c.instanceFollowRedirects=false;c.requestMethod=method
            c.setRequestProperty("Accept","application/json")
            if(token.isNotBlank()) c.setRequestProperty("Authorization","Bearer $token")
            if(body!=null) {c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            val code=c.responseCode
            val stream=if(code in 200..299) c.inputStream else c.errorStream
            val bytes=stream?.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(true) {val n=input.read(buffer);if(n<0) break;require(out.size()+n<=65536);out.write(buffer,0,n)}
                out.toByteArray()
            } ?: byteArrayOf()
            val result=runCatching {JSONObject(String(bytes,Charsets.UTF_8))}.getOrDefault(JSONObject())
            if(code !in 200..299) throw Rejected(code,result)
            return result
        } finally {c.disconnect()}
    }
    private fun failure(e:Exception)=when {
        e is Rejected && e.code==401 -> "인증 만료 · 다시 연결 필요, 앱 변경은 보관됨"
        e is Rejected && e.code==429 -> "연결 시도 제한 · 잠시 뒤 다시 시도"
        else -> "동기화 대기 · 기기 변경은 보관됨, 다시 시도 가능"
    }
    fun schedule(context:Context) {
        val scheduler=context.getSystemService(JobScheduler::class.java)
        if(scheduler.getPendingJob(7301)==null) scheduler.schedule(JobInfo.Builder(7301,ComponentName(context,ListDeliveryJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(15*60_000L).build())
    }
    fun connect(room:BotEngine,server:String,key:String,password:String) {
        val base=SyncEndpoint.normalize(server)
        if(!busy.add(room.roomId)) return
        val epoch=epochs[room.roomId] ?: 0L
        room.prefs.webStatus="서버 연결 확인 중";room.report()
        io.execute {
            try {
                val result=request(base,"/device/connect",body=JSONObject().put("roomKey",key).put("password",password))
                room.submit {
                    if((epochs[room.roomId] ?: 0L)!=epoch || room.rooms().none{it===room}) {busy.remove(room.roomId);return@submit}
                    // Preserve local schedules; differing initial lists require a deliberate choice.
                    val local=room.currentReminderList()
                    room.db.resetWebVersion();room.prefs.webConnection(result.getString("roomKey"),result.getString("token"),base)
                    if(local.getJSONArray("definitions").length()>0 || local.getJSONObject("summary").getBoolean("enabled")) room.db.queueSync(local,0)
                    room.prefs.webStatus="연결됨 · 동기화 확인 중";busy.remove(room.roomId);room.report();sync(room)
                }
            } catch(e:Exception) {if((epochs[room.roomId] ?: 0L)==epoch) room.prefs.webStatus=failure(e);busy.remove(room.roomId);room.report()}
        }
    }
    fun sync(room:BotEngine,finished:()->Unit={}) {
        val token=room.prefs.webToken;val server=room.prefs.webServer
        if(!room.consentAccepted() || token.isBlank() || server.isBlank() || !busy.add(room.roomId)) {finished();return}
        val epoch=epochs[room.roomId] ?: 0L
        val queued=if(room.db.syncConflict()==null) room.db.syncPending() else null
        val sent=queued?.getString("mutationId")
        fun current()=room.consentAccepted() && room.prefs.webToken==token && room.prefs.webServer==server && (epochs[room.roomId] ?: 0L)==epoch
        io.execute {
            try {
                val result=request(server,"/device/sync",token,queued,if(queued==null) "GET" else "PUT")
                require(result.getLong("version")>=0)
                room.submit {
                    var completed=false
                    try {
                        if(!current()) return@submit
                        room.db.acceptSync(result,sent);room.tick();completed=true
                        room.prefs.webStatus=when {
                            room.db.syncConflict()!=null -> "동시 수정 발견 · 사용할 목록 선택 필요"
                            room.db.syncPending()!=null -> "앱 변경 전송 대기"
                            else -> "동기화 완료 · v${room.db.webVersion()}"
                        }
                        // Acknowledgement follows durable application; retries never reapply unchanged lists.
                        if(room.db.syncPending()==null && room.db.webVersion()>0) io.execute {
                            runCatching {request(server,"/device/sync/ack",token,JSONObject().put("version",room.db.webVersion()))}
                                .onFailure {if(current()) {room.prefs.webStatus="목록 반영됨 · 완료 확인 재시도 필요";room.report()}}
                        }
                    } catch(e:Exception) {if(current()) room.prefs.webStatus=failure(e)} finally {
                        busy.remove(room.roomId);room.report();finished()
                        if(completed && current() && room.db.syncPending()!=null && room.db.syncConflict()==null) sync(room)
                    }
                }
            } catch(e:Exception) {
                room.submit {
                    try {
                        if(!current()) return@submit
                        if(e is Rejected && e.code==409 && e.body.has("current")) {
                            val remote=e.body.getJSONObject("current")
                            WebList.parse(JSONObject().put("version",remote.getLong("version")+1).put("list",remote.getJSONObject("list")))
                            room.db.setSyncConflict(remote);room.prefs.webStatus="동시 수정 발견 · 사용할 목록 선택 필요"
                        } else room.prefs.webStatus=failure(e)
                    } catch(bad:Exception) {if(current()) room.prefs.webStatus=failure(bad)} finally {busy.remove(room.roomId);room.report();finished()}
                }
            }
        }
    }
    fun disconnect(room:BotEngine) {
        cancel(room);val token=room.prefs.webToken;val server=room.prefs.webServer
        room.db.webSummary()?.let {
            room.prefs.summaryTime=LocalTime.parse(it.getString("time"));room.prefs.sendEmpty=it.getBoolean("sendEmpty")
            room.prefs.saveConfiguration(if(it.getBoolean("enabled")) room.prefs.features+BotFeature.DAILY_SUMMARY else room.prefs.features-BotFeature.DAILY_SUMMARY,room.prefs.tone)
        }
        room.prefs.webConnection("","");room.db.resetWebVersion();room.prefs.webStatus="연결 해제 · 현재 앱 목록 유지";room.tick()
        if(token.isNotBlank() && server.isNotBlank()) io.execute {runCatching{request(server,"/device/connection",token,method="DELETE")}}
    }
}

data class WebList(val version:Long,val definitions:List<Definition>,val summary:JSONObject) {
    companion object {
        fun parse(publication:JSONObject):WebList {
            val version=publication.getLong("version");require(version>0)
            val list=publication.getJSONObject("list");val array=list.getJSONArray("definitions");require(array.length()<=30)
            val defs=(0 until array.length()).map{definitionFromJson(array.getJSONObject(it).toString())}
            require(defs.map{it.id}.toSet().size==defs.size && defs.all{it.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && it.revision>0})
            val summary=list.getJSONObject("summary");LocalTime.parse(summary.getString("time"));summary.getBoolean("enabled");summary.getBoolean("sendEmpty")
            return WebList(version,defs,summary)
        }
    }
}
class ListDeliveryJob:JobService() {
    @Volatile private var active:JobParameters?=null
    override fun onStartJob(params:JobParameters):Boolean {
        active=params;val root=BotEngine.get(this)
        root.submit {
            val rooms=root.rooms().filter{it.prefs.webToken.isNotBlank() && it.prefs.webServer.isNotBlank()};val left=AtomicInteger(rooms.size)
            if(rooms.isEmpty() && active===params) {active=null;jobFinished(params,false)}
            rooms.forEach{room->ListDelivery.sync(room){if(left.decrementAndGet()==0 && active===params){active=null;jobFinished(params,false)}}}
        };return true
    }
    override fun onStopJob(params:JobParameters):Boolean {active=null;return true}
}

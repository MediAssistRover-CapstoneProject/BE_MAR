package com.mediassistrover.backend.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Short device recordings only. No raw-audio persistence, jobs or robot commands. */
@Service
public class OpenAiSttService {
    public static final int MAX_BYTES=1024*1024;
    public record Configuration(boolean enabled, boolean configured, String model, int maxDurationSec) { }
    public record Transcript(String state,String code,String text,String model,long durationMs,long processingMs) { }
    public record Result(int status,Transcript body) { }
    private final boolean enabled;
    private final String key,model;
    private final ObjectMapper json;
    private final URI endpoint;
    private final Semaphore capacity=new Semaphore(1);
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiSttService(@Value("${app.stt.enabled:false}") boolean enabled,
            @Value("${app.stt.openai-api-key:}") String key,
            @Value("${app.stt.model:gpt-transcribe}") String model,ObjectMapper json) {
        this(enabled,key,model,json,URI.create("https://api.openai.com/v1/audio/transcriptions"));
    }
    OpenAiSttService(boolean enabled,String key,String model,ObjectMapper json,URI endpoint) {
        if(model.isBlank()||model.length()>100||!model.matches("[a-zA-Z0-9._:-]+"))
            throw new IllegalArgumentException("Invalid STT model");
        this.enabled=enabled;this.key=key;this.model=model;this.json=json;this.endpoint=endpoint;
    }
    public Configuration configuration(){return new Configuration(enabled,!key.isBlank(),model,20);}

    public Result transcribe(byte[] wav,String language) {
        if(!Set.of("vi","en","auto").contains(language))throw new BusinessRuleException("STT_LANGUAGE_UNSUPPORTED");
        long duration=validateWav(wav),started=System.nanoTime();
        if(!enabled)return failed(503,"STT_DISABLED",duration,started);
        if(key.isBlank())return failed(503,"STT_API_KEY_MISSING",duration,started);
        if(!capacity.tryAcquire())return failed(429,"STT_BUSY",duration,started);
        try {
            String boundary="mar-voice-"+UUID.randomUUID();
            var body=new ByteArrayOutputStream(wav.length+1024);
            field(body,boundary,"model",model);
            field(body,boundary,"response_format","json");
            field(body,boundary,"prompt",language.equals("vi")?
                "Hội thoại tiếng Việt tại bệnh viện. Chép đúng lời nghe được, giữ nguyên tên phòng, tên điểm đến, chữ cái và số được nói.":
                language.equals("en")?"English hospital conversation. Transcribe only audible speech; preserve spoken room names, destination labels, letters and numbers.":
                "Vietnamese and English hospital conversation, including mixed language. Transcribe spoken words without translating; preserve destination names and letters.");
            // gpt-transcribe uses a plural array; legacy models accept one
            // language hint. Never send both schema variants in one request.
            if(model.equals("gpt-transcribe")) {
                for(String code:language.equals("auto")?List.of("vi","en"):List.of(language))
                    field(body,boundary,"languages[]",code);
            } else if(!language.equals("auto"))field(body,boundary,"language",language);
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"capture.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(wav);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII));
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(45))
                .header("Authorization","Bearer "+key).header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            // Complete the body inside HttpClient's request deadline, while
            // cancelling a provider response that exceeds the bounded JSON.
            var response=client.send(request,info->new TranscriptBody());
            byte[] raw=response.body();
            if(raw.length>65536)return failed(502,"STT_PROVIDER_RESPONSE_TOO_LARGE",duration,started);
            if(response.statusCode()!=200) {
                String code=switch(response.statusCode()) {
                    case 401,403 -> "STT_PROVIDER_AUTH_FAILED";
                    case 404 -> "STT_MODEL_UNAVAILABLE";
                    case 429 -> "STT_PROVIDER_QUOTA";
                    default -> "STT_PROVIDER_REJECTED";
                };
                return failed(response.statusCode()==429?503:502,code,duration,started);
            }
            var payload=json.readTree(raw);
            if(payload==null||!payload.isObject())return failed(502,"STT_TRANSCRIPT_INVALID",duration,started);
            if(model.equals("gpt-transcribe")&&payload.path("languages").isArray()&&payload.path("languages").isEmpty())
                return failed(422,"STT_NO_RELIABLE_SPEECH",duration,started);
            String text=payload.path("text").asText("").strip();
            if(text.isEmpty()||text.length()>4000)return failed(502,"STT_TRANSCRIPT_INVALID",duration,started);
            return new Result(200,new Transcript("READY",null,text,model,duration,elapsed(started)));
        } catch(HttpTimeoutException e){return failed(504,"STT_PROVIDER_TIMEOUT",duration,started);}
        catch(InterruptedException e){Thread.currentThread().interrupt();return failed(503,"STT_INTERRUPTED",duration,started);}
        catch(IOException e){return failed(502,"STT_PROVIDER_UNAVAILABLE",duration,started);}
        finally{capacity.release();}
    }
    private Result failed(int status,String code,long duration,long started){
        return new Result(status,new Transcript("FAILED",code,null,model,duration,elapsed(started)));
    }
    private static final class TranscriptBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){
                if(bytes.size()+buffer.remaining()>65536){
                    subscription.cancel();result.completeExceptionally(new IOException("STT_PROVIDER_RESPONSE_TOO_LARGE"));return;
                }
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
    private static long elapsed(long start){return (System.nanoTime()-start)/1_000_000;}
    private static void field(OutputStream out,String boundary,String name,String value)throws IOException{
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));
    }
    static long validateWav(byte[] wav) {
        if(wav==null||wav.length<44||wav.length>MAX_BYTES)throw invalid();
        var b=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if(b.getInt(0)!=0x46464952||b.getInt(8)!=0x45564157||Integer.toUnsignedLong(b.getInt(4))+8!=wav.length)throw invalid();
        boolean format=false;long samples=0;int dataChunks=0;
        for(long offset=12;offset+8<=wav.length;) {
            int pos=(int)offset,id=b.getInt(pos);long size=Integer.toUnsignedLong(b.getInt(pos+4));
            long end=offset+8+size;if(end>wav.length)throw invalid();
            if(id==0x20746d66) {
                if(format||size<16||b.getShort(pos+8)!=1||b.getShort(pos+10)!=1||b.getInt(pos+12)!=16000
                        ||b.getInt(pos+16)!=32000||b.getShort(pos+20)!=2||b.getShort(pos+22)!=16)throw invalid();
                format=true;
            } else if(id==0x61746164) {
                if(++dataChunks>1||size%2!=0)throw invalid();samples=size/2;
            }
            offset=end+(size%2);
        }
        if(!format||dataChunks!=1||samples<3200||samples>320000)throw invalid();
        return samples*1000/16000;
    }
    private static BusinessRuleException invalid(){
        return new BusinessRuleException("STT_INVALID_WAV: require 0.2..20 seconds, PCM16 mono 16000 Hz, at most 1 MB");
    }
}

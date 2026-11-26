package cn.crossdesk.mobile;

import org.json.JSONObject;

/** Same request/acknowledgement and rollback rules as the iOS controller. */
final class RemoteVideoSettings {
    static String[] preferences(){return new String[]{L10n.tr("帧率优先"),L10n.tr("画质优先"),L10n.tr("平衡")};}
    static String[] details(){return new String[]{
        L10n.tr("优先保持画面流畅，带宽或编码压力较大时会更积极地降低分辨率。"),
        L10n.tr("始终保持较高分辨率，带宽不足时通过降低帧率来保证画面清晰度。"),
        L10n.tr("兼顾清晰度和流畅度，只在持续压力下逐步降低分辨率。")};}
    /** Live rates on the wire; 90/120/144 only when the host advertises them. */
    static final int[] FRAME_RATES={30,60,90,120,144};
    static boolean supportedRate(long rate){for(int value:FRAME_RATES)if(value==rate)return true;return false;}
    /** Snaps a refresh ceiling down to a supported rate; unknown values mean 60. */
    static int normalizeMax(int rate){int result=60;for(int value:FRAME_RATES)if(value>result&&value<=rate)result=value;return result;}
    static final class Values {
        final int quality,frameRate,preference;
        Values(int quality,int frameRate,int preference){this.quality=quality;this.frameRate=frameRate;this.preference=preference;}
        boolean valid(){return quality>=0&&quality<=2&&supportedRate(frameRate)&&preference>=0&&preference<=2;}
        JSONObject status(long requestId,boolean accepted){
            try{return new JSONObject().put("type",12).put("video_settings",new JSONObject().put("quality",quality).put("frame_rate",frameRate).put("preference",preference).put("request_id",requestId).put("accepted",accepted));}
            catch(org.json.JSONException impossible){throw new IllegalStateException(impossible);}
        }
    }
    boolean supported,pending,failed;
    /** min(host ceiling, this device's fastest refresh); 60 until the host reports. */
    int maxFrameRate=60;
    /** Fastest refresh rate of this device's display, set by the activity. */
    int deviceMaxFrameRate=60;
    Values selection,applied;
    long requestId,appliedRequestId;
    RemoteVideoSettings(int preference){this(preference,1920);}
    RemoteVideoSettings(int preference,int displayLongEdge){
        // Start within common mobile decoder limits. Hardware availability and
        // maximum resolution vary, and AV1 may still need software fallback.
        // Small screens need no more than 720p; larger screens start at 1080p.
        // All quality/frame-rate choices remain available for manual adjustment.
        selection=applied=new Values(displayLongEdge>0&&displayLongEdge<=1280?0:1,30,preference>=0&&preference<=2?preference:1);
    }
    /** Applies the host's advertised ceiling, capped by this device's display. */
    void setHostMaxFrameRate(int hostMax){
        maxFrameRate=Math.min(normalizeMax(hostMax),normalizeMax(deviceMaxFrameRate));
        if(selection.frameRate>maxFrameRate)selection=new Values(selection.quality,maxFrameRate,selection.preference);
    }
    int[] frameRates(){
        int count=0;for(int value:FRAME_RATES)if(value<=maxFrameRate)count++;
        int[] result=new int[count];int index=0;for(int value:FRAME_RATES)if(value<=maxFrameRate)result[index++]=value;return result;
    }
    void setSupported(boolean value){supported=value;if(!value){selection=applied;pending=false;failed=false;}}
    long begin(Values value){
        if(!supported||!value.valid()||value.frameRate>maxFrameRate)return -1;
        requestId=(requestId+1)&0xffffffffL;selection=value;pending=true;failed=false;return requestId;
    }
    void receive(Values value,long responseId,boolean accepted){
        if(!supported||!value.valid()||responseId<0||responseId>0xffffffffL||(int)(requestId-responseId)<0)return;
        if(accepted&&(int)(responseId-appliedRequestId)>=0){applied=value;appliedRequestId=responseId;}
        if(responseId!=requestId)return;
        pending=false;failed=!accepted;selection=applied;
    }
    void receive(JSONObject message){
        try{
            if(message.getInt("type")!=12)return;
            JSONObject settings=message.getJSONObject("video_settings");
            for(String key:new String[]{"quality","frame_rate","preference","request_id"}){
                Object value=settings.get(key);if(!(value instanceof Integer)&&!(value instanceof Long))return;
            }
            long quality=settings.getLong("quality"),rate=settings.getLong("frame_rate"),preference=settings.getLong("preference");
            if(quality<0||quality>2||!supportedRate(rate)||preference<0||preference>2)return;
            Object accepted=settings.opt("accepted");if(accepted!=null&&!(accepted instanceof Boolean))return;
            receive(new Values((int)quality,(int)rate,(int)preference),settings.getLong("request_id"),Boolean.TRUE.equals(accepted));
        }catch(org.json.JSONException ignored){ }
    }
    void expire(long expiredId){if(pending&&expiredId==requestId){selection=applied;pending=false;failed=true;}}
    String feedback(){return !supported?L10n.tr("连接建立后可调整，需被控端支持。"):failed?L10n.tr("调整失败，请重试。"):"";}
}

package cn.crossdesk.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoSettingsTest {
    private final RemoteVideoSettings.Values first=new RemoteVideoSettings.Values(1,30,2),second=new RemoteVideoSettings.Values(0,60,1);
    private void same(RemoteVideoSettings.Values expected,RemoteVideoSettings.Values actual){assertEquals(expected.quality,actual.quality);assertEquals(expected.frameRate,actual.frameRate);assertEquals(expected.preference,actual.preference);}

    @Test public void acknowledgementCommitsAndRejectionRollsBack(){
        RemoteVideoSettings state=new RemoteVideoSettings(1);state.setSupported(true);long id=state.begin(first);assertTrue(state.pending);same(new RemoteVideoSettings.Values(1,30,1),state.applied);
        state.receive(first,id,true);same(first,state.selection);assertFalse(state.pending);assertFalse(state.failed);state.expire(id);assertFalse(state.failed);
        id=state.begin(second);state.receive(second,id,false);same(first,state.selection);assertTrue(state.failed);state.begin(second);assertFalse(state.failed);
    }

    @Test public void timeoutLateReplyAndCapabilityLoss(){
        RemoteVideoSettings state=new RemoteVideoSettings(1);state.setSupported(true);long id=state.begin(first);state.expire(id);assertTrue(state.failed);same(new RemoteVideoSettings.Values(1,30,1),state.selection);
        state.receive(first,id,true);same(first,state.selection);assertFalse(state.failed);
        id=state.begin(second);state.setSupported(false);state.receive(second,id,true);state.expire(id);same(first,state.selection);assertFalse(state.failed);assertFalse(state.pending);
    }

    @Test public void highFrameRatesFollowHostAndDeviceCeilings(){
        RemoteVideoSettings state=new RemoteVideoSettings(1);state.setSupported(true);
        RemoteVideoSettings.Values fast=new RemoteVideoSettings.Values(2,120,0);
        assertTrue(fast.valid());assertFalse(new RemoteVideoSettings.Values(2,75,0).valid());
        assertEquals(-1,state.begin(fast));assertArrayEquals(new int[]{30,60},state.frameRates());
        state.deviceMaxFrameRate=120;state.setHostMaxFrameRate(144);
        assertEquals(120,state.maxFrameRate);assertArrayEquals(new int[]{30,60,90,120},state.frameRates());
        long id=state.begin(fast);assertTrue(id>=0);state.receive(fast,id,true);same(fast,state.applied);
        state.setHostMaxFrameRate(60);assertEquals(60,state.selection.frameRate);
        state.setHostMaxFrameRate(0);assertEquals(60,state.maxFrameRate);
    }
    @Test public void sessionsStartSmoothAndRememberChoices(){
        RemoteVideoSettings fresh=new RemoteVideoSettings(0,2400,-1,0);
        same(new RemoteVideoSettings.Values(1,60,0),fresh.selection);
        RemoteVideoSettings saved=new RemoteVideoSettings(0,2400,2,120);
        same(new RemoteVideoSettings.Values(2,120,0),saved.selection);
        saved.setHostMaxFrameRate(60);
        assertEquals(60,saved.selection.frameRate);
        same(new RemoteVideoSettings.Values(0,60,0),new RemoteVideoSettings(9,1200,-1,75).selection);
    }
}

package com.muonium.core.playback;

import com.muonium.core.TestPorts;
import com.muonium.core.model.PlaybackState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackStateMachineTest {

    private PlaybackStateMachine machine() {
        return new PlaybackStateMachine(new TestPorts.FixedClock(0), new TestPorts.SilentLogger());
    }

    @Test
    void happyPathToPlaying() {
        var m = machine();
        assertEquals(PlaybackState.IDLE, m.state());
        assertEquals(PlaybackState.LOADING, m.transition(PlaybackStateMachine.Event.LOAD));
        assertEquals(PlaybackState.READY, m.transition(PlaybackStateMachine.Event.READY));
        assertEquals(PlaybackState.PLAYING, m.transition(PlaybackStateMachine.Event.PLAY));
        assertEquals(PlaybackState.PAUSED, m.transition(PlaybackStateMachine.Event.PAUSE));
        assertEquals(PlaybackState.PLAYING, m.transition(PlaybackStateMachine.Event.RESUME));
        assertEquals(PlaybackState.ENDED, m.transition(PlaybackStateMachine.Event.END));
        assertEquals(6, m.history().size());
    }

    @Test
    void illegalTransitionIsRejectedNotSwallowed() {
        var m = machine();
        assertThrows(IllegalStateException.class,
                () -> m.transition(PlaybackStateMachine.Event.PLAY));
        assertEquals(PlaybackState.IDLE, m.state());
        assertThrows(IllegalStateException.class,
                () -> m.transition(PlaybackStateMachine.Event.PAUSE));
    }

    @Test
    void bufferingAndRecovery() {
        var m = machine();
        m.transition(PlaybackStateMachine.Event.LOAD);
        assertEquals(PlaybackState.BUFFERING, m.transition(PlaybackStateMachine.Event.BUFFER_UNDERRUN));
        assertEquals(PlaybackState.PLAYING, m.transition(PlaybackStateMachine.Event.BUFFER_RECOVERED));
    }

    @Test
    void failureIsDistinctFromStop() {
        var m = machine();
        m.transition(PlaybackStateMachine.Event.LOAD);
        assertEquals(PlaybackState.FAILED, m.transition(PlaybackStateMachine.Event.FAIL));
        assertTrue(m.state().isTerminal());
        assertEquals(PlaybackState.LOADING, m.transition(PlaybackStateMachine.Event.LOAD));
    }

    @Test
    void stoppedCanOnlyBeLeftByLoadOrReset() {
        var m = machine();
        m.transition(PlaybackStateMachine.Event.LOAD);
        m.transition(PlaybackStateMachine.Event.STOP);
        assertThrows(IllegalStateException.class,
                () -> m.transition(PlaybackStateMachine.Event.PLAY));
        assertEquals(PlaybackState.IDLE, m.transition(PlaybackStateMachine.Event.RESET));
    }

    @Test
    void seekWhilePausedStaysPaused() {
        var m = machine();
        m.transition(PlaybackStateMachine.Event.LOAD);
        m.transition(PlaybackStateMachine.Event.READY);
        m.transition(PlaybackStateMachine.Event.PLAY);
        m.transition(PlaybackStateMachine.Event.PAUSE);
        assertEquals(PlaybackState.PAUSED, m.transition(PlaybackStateMachine.Event.SEEK));
    }
}

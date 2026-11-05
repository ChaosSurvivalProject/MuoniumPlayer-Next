package com.muonium.core.model;

/** 播放状态机的九个状态。取消、失败、未知状态必须分开，不允许吞异常。 */
public enum PlaybackState {
    IDLE,
    LOADING,
    READY,
    BUFFERING,
    PLAYING,
    PAUSED,
    STOPPED,
    ENDED,
    FAILED;

    /** 终态：只有显式 RESET / LOAD 才能离开。 */
    public boolean isTerminal() {
        return this == STOPPED || this == ENDED || this == FAILED;
    }
}

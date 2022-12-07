package com.hmdp.utils;

public interface ILock {

    boolean trLock(long timeoutSec);

    void unlock();
}

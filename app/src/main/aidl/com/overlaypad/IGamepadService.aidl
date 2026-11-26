package com.overlaypad;

interface IGamepadService {
    void setButton(int linuxKeyCode, boolean pressed);
    void setAbsAxis(int linuxAbsCode, int value);
    void resetAll();
    String getStatus();
    String getForegroundPackage();
}

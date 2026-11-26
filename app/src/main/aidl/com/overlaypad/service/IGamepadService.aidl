package com.overlaypad.service;

interface IGamepadService {
    void destroy() = 16777114;
    boolean initGamepad(String deviceName, int vendorId, int productId) = 1;
    void setButton(int linuxKeyCode, boolean pressed) = 2;
    void setAbsAxis(int linuxAbsCode, int value) = 3;
    void closeGamepad() = 4;
}

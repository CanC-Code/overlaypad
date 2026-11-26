package com.overlaypad.service;

interface IGamepadService {
    void destroy() = 16777114;
    boolean initGamepad(String deviceName, int vendorId, int productId);
    void setButton(int linuxKeyCode, boolean pressed);
    void setAbsAxis(int linuxAbsCode, int value);
    void closeGamepad();
}

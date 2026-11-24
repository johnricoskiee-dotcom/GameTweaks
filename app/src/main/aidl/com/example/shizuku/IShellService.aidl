package com.example.shizuku;

interface IShellService {
    String exec(String command);
    String readSysFile(String path);
    void destroy();
}

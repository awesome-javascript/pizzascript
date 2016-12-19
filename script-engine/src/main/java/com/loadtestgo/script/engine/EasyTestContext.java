package com.loadtestgo.script.engine;

import com.loadtestgo.util.Path;
import org.pmw.tinylog.Logger;

import java.net.Inet4Address;
import java.net.UnknownHostException;

public class EasyTestContext extends TestContext {
    public EasyTestContext() {
        super(new UserContext(new EngineContext()));
        commonInit();
    }

    public EasyTestContext(String name) {
        super(new UserContext(new EngineContext()), name);
        commonInit();
    }

    public EasyTestContext(String name, int userId) {
        super(new UserContext(new EngineContext(), userId), name);
        commonInit();
    }

    private void commonInit() {
        setBaseDirectory(Path.getCurrentWorkingDirectory());
        try {
            getEngineContext().setLocalPublicIp(Inet4Address.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            Logger.info(e);
        }
    }

    public void cleanup() {
        super.cleanup();
        userContext.cleanup();
        userContext.getEngineContext().cleanup();
    }
}

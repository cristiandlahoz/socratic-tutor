package com.wornux.ui.conversation;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@Tag("ascii-frame-animation")
@JsModule("./conversation/ascii-frame-animation.ts")
public class AsciiFrameAnimation extends Component {

    public AsciiFrameAnimation(String frameFolder, int frameCount, int fps) {
        setFrameFolder(frameFolder);
        setFrameCount(frameCount);
        setFps(fps);
        setLoop(true);
        setBouncing(true);
    }

    public void setFrameFolder(String frameFolder) {
        getElement().setProperty("frameFolder", frameFolder);
    }

    public void setFrameCount(int frameCount) {
        getElement().setProperty("frameCount", frameCount);
    }

    public void setFps(int fps) {
        getElement().setProperty("fps", fps);
    }

    public void setLoop(boolean loop) {
        getElement().setProperty("loop", loop);
    }

    public void setBouncing(boolean bouncing) {
        getElement().setProperty("bouncing", bouncing);
    }
}

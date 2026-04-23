package com.wornux.chat;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;

@Tag("lottie-player")
@NpmPackage(value = "@lottiefiles/lottie-player", version = "2.0.12")
@JsModule("@lottiefiles/lottie-player")
public class LottiePlayer extends Component {

  public LottiePlayer(String src, boolean autoplay, boolean loop) {
    setSource(src);
    setAutoplay(autoplay);
    setLoop(loop);
    getElement().setAttribute("background", "transparent");
    getElement().setAttribute("mode", "normal");
  }

  public void setSource(String src) {
    getElement().setAttribute("src", src);
  }

  public void setAutoplay(boolean autoplay) {
    setBooleanAttribute("autoplay", autoplay);
  }

  public void setLoop(boolean loop) {
    setBooleanAttribute("loop", loop);
  }

  public void setSpeed(double speed) {
    getElement().setProperty("speed", speed);
  }

  public void play() {
    getElement().callJsFunction("play");
  }

  public void pause() {
    getElement().callJsFunction("pause");
  }

  public void stop() {
    getElement().callJsFunction("stop");
  }

  private void setBooleanAttribute(String name, boolean value) {
    if (value) {
      getElement().setAttribute(name, true);
    } else {
      getElement().removeAttribute(name);
    }
  }
}

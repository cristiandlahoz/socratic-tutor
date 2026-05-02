package com.wornux.presentation.chat;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;

@Tag("ascii-frame-animation")
@JsModule("./ascii-frame-animation.ts")
public class AsciiFrameAnimation extends Component {

  public AsciiFrameAnimation(String frameFolder, int frameCount, int fps) {
    setFrameFolder(frameFolder);
    setFrameCount(frameCount);
    setFps(fps);
    setLoop(true);
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
}

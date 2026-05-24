package com.wornux.presentation.crunner;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.internal.JacksonUtils;
import com.wornux.application.crunner.CDiagnostic;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Tag("c-debug-source-viewer")
@JsModule("./c-debug-source-viewer.tsx")
@NpmPackage(value = "@uiw/react-codemirror", version = "4.25.4")
@NpmPackage(value = "@fsegurai/codemirror-theme-solarized-dark", version = "6.2.5")
@NpmPackage(value = "@fsegurai/codemirror-theme-solarized-light", version = "6.2.5")
@NpmPackage(value = "@codemirror/lang-json", version = "6.0.2")
@NpmPackage(value = "@codemirror/lang-xml", version = "6.1.0")
@NpmPackage(value = "@codemirror/lang-javascript", version = "6.2.4")
@NpmPackage(value = "@codemirror/lang-python", version = "6.1.7")
@NpmPackage(value = "@codemirror/lang-java", version = "6.0.1")
@NpmPackage(value = "@codemirror/lang-cpp", version = "6.0.2")
@NpmPackage(value = "@codemirror/lint", version = "6.9.5")
public final class CDebugSourceViewer extends Component implements HasSize {

  public CDebugSourceViewer() {
    setLang("c");
  }

  public void setValue(String value) {
    getElement().setProperty("value", value == null ? "" : value);
  }

  public void setLang(String lang) {
    getElement().setProperty("lang", lang == null || lang.isBlank() ? "c" : lang);
  }

  public void setDiagnostics(Collection<CDiagnostic> diagnostics) {
    var safeDiagnostics =
        diagnostics == null ? List.<CDiagnostic>of() : new ArrayList<>(diagnostics);
    getElement().setPropertyJson("diagnostics", JacksonUtils.listToJson(safeDiagnostics));
  }

  public void setActiveLine(Integer activeLine) {
    getElement().setProperty("activeLine", activeLine == null ? 0 : activeLine);
  }
}

package com.wornux.ui.navigation;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouterLink;
import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.workspace.WorkspaceDestination;
import java.util.Objects;
import java.util.function.Supplier;

public record NavigationEntry(
    String label,
    RouteTarget routeTarget,
    Supplier<Component> iconFactory,
    ContextLevel minimumContextLevel,
    AppPermission requiredPermission,
    WorkspaceDestination workspaceDestination,
    int order) {

  public record RouteTarget(Class<? extends Component> viewTarget, QueryParameters parameters) {
    public RouteTarget {
      Objects.requireNonNull(viewTarget, "viewTarget class configuration cannot be null");

      if (parameters == null) {
        parameters = QueryParameters.empty();
      }
    }

    public RouteTarget(Class<? extends Component> viewTarget) {
      this(viewTarget, QueryParameters.empty());
    }
    /**
     * Reconfigures an existing RouterLink instance securely with this target's routing context.
     * Designed defensively to prevent NullPointerExceptions and ensure structural stability.
     *
     * @param link The RouterLink instance to populate. Can safely be null (ignored).
     */
    public void setRouteOn(RouterLink link) {
      if (link == null) {
        return;
      }

      link.setRoute(this.viewTarget);
      link.setQueryParameters(this.parameters);
    }
  }

  public NavigationEntry(
      String label,
      Class<? extends Component> viewTarget,
      Supplier<Component> iconFactory,
      ContextLevel minimumContextLevel,
      AppPermission requiredPermission,
      int order) {
    this(
        label,
        new RouteTarget(viewTarget),
        iconFactory,
        minimumContextLevel,
        requiredPermission,
        null,
        order);
  }

  public NavigationEntry(
      String label,
      Class<? extends Component> viewTarget,
      Supplier<Component> iconFactory,
      ContextLevel minimumContextLevel,
      AppPermission requiredPermission,
      WorkspaceDestination workspaceDestination,
      int order) {
    this(
        label,
        new RouteTarget(viewTarget),
        iconFactory,
        minimumContextLevel,
        requiredPermission,
        workspaceDestination,
        order);
  }

  public Component createIcon() {
    return iconFactory.get();
  }
}

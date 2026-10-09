package com.logistics.gateway.route;

import java.util.List;

public class GatewayRouteDefinition {
    private String id;
    private String uri;
    private List<String> predicates;
    private List<String> filters;
    private String pathPrefix;
    private boolean stripPrefix;

    public GatewayRouteDefinition() {
    }

    public GatewayRouteDefinition(String id, String uri, String pathPrefix, boolean stripPrefix) {
        this.id = id;
        this.uri = uri;
        this.pathPrefix = pathPrefix;
        this.stripPrefix = stripPrefix;
        this.predicates = List.of("Path=" + pathPrefix + (pathPrefix.endsWith("/**") ? "" : "/**"));
        this.filters = stripPrefix ? List.of("StripPrefix=1") : List.of();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public List<String> getPredicates() {
        return predicates;
    }

    public void setPredicates(List<String> predicates) {
        this.predicates = predicates;
    }

    public List<String> getFilters() {
        return filters;
    }

    public void setFilters(List<String> filters) {
        this.filters = filters;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public boolean isStripPrefix() {
        return stripPrefix;
    }

    public void setStripPrefix(boolean stripPrefix) {
        this.stripPrefix = stripPrefix;
    }
}

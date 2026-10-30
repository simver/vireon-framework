package com.simxe.vireon.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 显式启用的文档展示配置；不透传任意第三方配置。 */
@ConfigurationProperties("vireon.openapi")
public class OpenApiProperties {
    private boolean enabled;
    private String path = "/docs";
    private String title = "接口与架构评审";
    private String layout = "balanced";
    private Feature review = new Feature();
    private Feature gitHistory = new Feature();
    private Feature tryIt = new Feature();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getPath() { return path; }
    public void setPath(String value) { path = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getLayout() { return layout; }
    public void setLayout(String value) { layout = value; }
    public Feature getReview() { return review; }
    public void setReview(Feature value) { review = value; }
    public Feature getGitHistory() { return gitHistory; }
    public void setGitHistory(Feature value) { gitHistory = value; }
    public Feature getTryIt() { return tryIt; }
    public void setTryIt(Feature value) { tryIt = value; }
    /** 可选增强开关。 */
    public static class Feature {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
    }
}

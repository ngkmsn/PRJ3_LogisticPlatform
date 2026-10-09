package com.logistics.user.dto;

public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private UserSummaryDto user;

    public LoginResponse() {
        this.tokenType = "Bearer";
    }

    public LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummaryDto user) {
        this.accessToken = accessToken;
        this.tokenType = (tokenType != null) ? tokenType : "Bearer";
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public static LoginResponse of(String accessToken, long expiresInSeconds, UserSummaryDto user) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, user);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserSummaryDto getUser() {
        return user;
    }

    public void setUser(UserSummaryDto user) {
        this.user = user;
    }
}

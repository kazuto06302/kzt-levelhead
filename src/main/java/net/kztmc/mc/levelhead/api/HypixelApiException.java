package net.kztmc.mc.levelhead.api;

public class HypixelApiException extends Exception {

    private final int statusCode;

    public HypixelApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRateLimited() {
        return statusCode == 429;
    }
}
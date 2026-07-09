package com.wornux.services.training_activity;

public class AdaptiveTutorStartUnavailableException extends IllegalStateException {

    public static final String PUBLIC_MESSAGE =
            "No fue posible continuar la tutoría en este momento. Intenta nuevamente dentro de unos minutos.";

    public AdaptiveTutorStartUnavailableException(Throwable cause) {
        super(PUBLIC_MESSAGE, cause);
    }
}

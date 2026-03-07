package com.l.erp.common.exception.custom;

import java.time.Instant;

public class UserLockedException extends RuntimeException{
    private final Instant lockedUntil;

    public UserLockedException(Instant lockedUntil) {
        super("Usuário bloqueado");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}

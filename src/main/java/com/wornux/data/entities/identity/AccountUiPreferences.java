package com.wornux.data.entities.identity;

import com.wornux.data.enums.ThemePreference;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountUiPreferences {

    private ThemePreference theme = ThemePreference.SYSTEM;
    private int baseFontSize = 13;
}

/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2025 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.discordsrv.common.feature.bansync.enums;

import com.discordsrv.common.abstraction.sync.result.ISyncResult;

public enum BanSyncResult implements ISyncResult {

    // Error
    NO_PUNISHMENT_INTEGRATION("No punishment integration"),
    NO_DISCORD_CONNECTION("No Discord connection"),
    GUILD_DOESNT_EXIST("Guild doesn't exist"),
    INVALID_CONFIG("Invalid config"),
    NOT_A_GUILD_MEMBER("User is not part of the server the role is in"),
    ROLE_CHANGE_CANNOT_CHANGE_GAME("Ban sync is configured such that adding or removing the banned role in Discord does not affect game state"),

    ;

    private final String format;

    BanSyncResult(String format) {
        this.format = format;
    }

    @Override
    public boolean isError() {
        return true;
    }

    @Override
    public boolean isUpdate() {
        return false;
    }

    @Override
    public String getFormat() {
        return format;
    }
}

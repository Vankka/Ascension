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

package com.discordsrv.common.config.main;

import com.discordsrv.common.abstraction.sync.enums.SyncDirection;
import com.discordsrv.common.config.main.generic.AbstractSyncConfig;
import com.discordsrv.common.util.Game;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class NicknameSyncConfig extends AbstractSyncConfig<NicknameSyncConfig, Game, Long> {

    public NicknameSyncConfig() {
        // Change defaults
        timer.enabled = false;
        direction = SyncDirection.MINECRAFT_TO_DISCORD;
    }

    @Comment("The id for the Discord server where the nicknames should be synced from/to")
    public long serverId = 0L;

    // TODO: more info on regex pairs (String#replaceAll)
    @Comment("Regex filters for nicknames")
    public Map<Pattern, String> nicknameRegexFilters = new LinkedHashMap<>();

    @Override
    public boolean isSet() {
        return serverId != 0;
    }

    @Override
    public Game gameId() {
        return Game.INSTANCE;
    }

    @Override
    public Long discordId() {
        return serverId;
    }

    @Override
    public boolean isSameAs(NicknameSyncConfig otherConfig) {
        return false;
    }

    @Override
    public String describe() {
        return Long.toUnsignedString(serverId);
    }
}

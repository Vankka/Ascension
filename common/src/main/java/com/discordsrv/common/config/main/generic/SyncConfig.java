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

package com.discordsrv.common.config.main.generic;

import com.discordsrv.common.abstraction.sync.enums.SyncDirection;
import com.discordsrv.common.abstraction.sync.enums.SyncSide;
import com.discordsrv.common.config.configurate.annotation.Constants;
import com.discordsrv.common.config.configurate.annotation.Order;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class SyncConfig {

    @Comment("The direction to synchronize in.\n"
            + "Valid options: %1, %2, %3")
    @Constants.Comment({"bidirectional", "minecraft_to_discord", "discord_to_minecraft"})
    @Order(-5)
    public SyncDirection direction = SyncDirection.BIDIRECTIONAL;

    @Comment("Timed resynchronization")
    public TimerConfig timer = new TimerConfig();

    @ConfigSerializable
    public static class TimerConfig {

        @Comment("If timed synchronization is enabled")
        public boolean enabled = true;

        @Comment("The number of minutes between timed synchronization cycles")
        public int cycleTime = 5;
    }

    @Comment("Decides which side takes priority when using timed synchronization or the resync command and there are differences\n"
            + "Valid options: %1, %2")
    @Constants.Comment({"minecraft", "discord"})
    public SyncSide tieBreaker = SyncSide.MINECRAFT;
}

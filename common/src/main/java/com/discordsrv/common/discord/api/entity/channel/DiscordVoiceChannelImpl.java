/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2024 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
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

package com.discordsrv.common.discord.api.entity.channel;

import com.discordsrv.api.discord.entity.channel.DiscordVoiceChannel;
import com.discordsrv.common.DiscordSRV;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

public class DiscordVoiceChannelImpl extends AbstractDiscordGuildMessageChannel<VoiceChannel> implements DiscordVoiceChannel {

    public DiscordVoiceChannelImpl(DiscordSRV discordSRV, VoiceChannel voiceChannel) {
        super(discordSRV, voiceChannel);
    }

    @Override
    public VoiceChannel asJDA() {
        return channel;
    }

    @Override
    public String toString() {
        return "VoiceChannel:" + getName() + "(" + Long.toUnsignedString(getId()) + ")";
    }
}

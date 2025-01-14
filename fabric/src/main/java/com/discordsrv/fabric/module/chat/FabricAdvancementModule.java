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

package com.discordsrv.fabric.module.chat;

import com.discordsrv.api.component.MinecraftComponent;
import com.discordsrv.api.events.message.receive.game.AwardMessageReceiveEvent;
import com.discordsrv.common.abstraction.player.IPlayer;
import com.discordsrv.common.util.ComponentUtil;
import com.discordsrv.fabric.FabricDiscordSRV;
import com.discordsrv.fabric.module.AbstractFabricModule;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class FabricAdvancementModule extends AbstractFabricModule {
    private final FabricDiscordSRV discordSRV;
    private static FabricAdvancementModule instance;

    public FabricAdvancementModule(FabricDiscordSRV discordSRV) {
        super(discordSRV);
        this.discordSRV = discordSRV;
        instance = this;
    }

    public static void onGrant(AdvancementEntry advancementEntry, String criterionName, CallbackInfoReturnable<Boolean> cir, ServerPlayerEntity owner) {
        if (instance == null || !instance.enabled) return;

        FabricDiscordSRV discordSRV = instance.discordSRV;
        Advancement advancement = advancementEntry.value();
        if(advancement.name().isEmpty()) return; // Usually a crafting recipe.
        String achievement = Formatting.strip(advancement.name().get().getString());
        MinecraftComponent achievementName = ComponentUtil.fromPlain(achievement);

        IPlayer player = discordSRV.playerProvider().player(owner);
        discordSRV.eventBus().publish(
                new AwardMessageReceiveEvent(
                        null,
                        player,
                        achievementName,
                        null,
                        null,
                        false
                )
        );
    }
}

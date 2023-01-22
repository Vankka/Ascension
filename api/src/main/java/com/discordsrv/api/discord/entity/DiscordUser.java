/*
 * This file is part of the DiscordSRV API, licensed under the MIT License
 * Copyright (c) 2016-2023 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.discordsrv.api.discord.entity;

import com.discordsrv.api.discord.entity.channel.DiscordDMChannel;
import com.discordsrv.api.placeholder.annotation.Placeholder;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * A Discord user.
 */
public interface DiscordUser extends JDAEntity<User>, Snowflake, Mentionable {

    /**
     * Gets if this user is the bot this DiscordSRV instance is connected.
     * @return true if this user is the bot connected to this DiscordSRV instance
     */
    boolean isSelf();

    /**
     * Gets if this user is a bot (or webhook).
     * @return true if this user is a bot or webhook
     */
    boolean isBot();

    /**
     * Gets the username of the Discord user.
     * @return the user's username
     */
    @Placeholder("user_name")
    @NotNull
    String getUsername();

    /**
     * Gets the Discord user's discriminator.
     * @return the user's discriminator
     */
    @Placeholder("user_discriminator")
    @NotNull
    String getDiscriminator();

    /**
     * Gets the Discord user's avatar url, if an avatar is set.
     * @return the user's avatar url or {@code null}
     */
    @Placeholder("user_avatar_url")
    @Nullable
    String getAvatarUrl();

    /**
     * Gets the Discord user's avatar that is currently active,
     * if an avatar isn't set it'll be the url to the default avatar provided by Discord.
     * @return the user's avatar url
     */
    @Placeholder("user_effective_avatar_url")
    @NotNull
    String getEffectiveAvatarUrl();

    /**
     * Gets the Discord user's username followed by a {@code #} and their discriminator.
     * @return the Discord user's username and discriminator in the following format {@code Username#1234}
     */
    @Placeholder("user_tag")
    default String getAsTag() {
        return getUsername() + "#" + getDiscriminator();
    }

    /**
     * Opens a private channel with the user or instantly returns the already cached private channel for this user.
     * @return a future for the private channel with this Discord user
     */
    CompletableFuture<DiscordDMChannel> openPrivateChannel();

}

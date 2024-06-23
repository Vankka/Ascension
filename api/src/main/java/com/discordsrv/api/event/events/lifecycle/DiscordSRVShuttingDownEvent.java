/*
 * This file is part of the DiscordSRV API, licensed under the MIT License
 * Copyright (c) 2016-2024 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
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

package com.discordsrv.api.event.events.lifecycle;

import com.discordsrv.api.event.bus.EventPriority;
import com.discordsrv.api.event.events.Event;

/**
 * Indicates that DiscordSRV is shutting down.
 * <p>
 * DiscordSRV's own systems will shut down at the following times:
 * {@link EventPriority#EARLY}<br/>
 *  - DiscordSRV's own modules shutdown<br/>
 *
 * {@link EventPriority#LATE}<br/>
 *  - Discord connections are shutdown<br/>
 *
 * {@link EventPriority#LAST}<br/>
 *  - DiscordSRV's scheduler is shutdown<br/>
 */
public class DiscordSRVShuttingDownEvent implements Event {
}
